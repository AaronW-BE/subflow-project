package service

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"
)

// The open-access endpoint of ExchangeRate-API. No key, commercial use allowed,
// caching allowed, updated once a day.
//
// Chosen over Frankfurter because Frankfurter republishes the ECB reference
// table, which is 30 currencies - it is missing TWD, AED, SAR, VND, PKR, BDT,
// NGN, EGP, UAH, CLP and COP, eleven of the forty this app offers. This
// provider returns 166.
//
// Their terms require visible attribution wherever the rates are shown, which
// is why RateAttribution travels in the /rates payload rather than being
// hard-coded in each client.
const (
	rateProviderURL  = "https://open.er-api.com/v6/latest/USD"
	rateProviderName = "Exchange Rate API" // renders as "Rates By Exchange Rate API"
	rateProviderLink = "https://www.exchangerate-api.com"
)

// rateProviderResponse is the subset of the payload we use.
type rateProviderResponse struct {
	Result             string             `json:"result"`
	BaseCode           string             `json:"base_code"`
	Rates              map[string]float64 `json:"rates"`
	TimeLastUpdateUnix int64              `json:"time_last_update_unix"`
	TimeNextUpdateUnix int64              `json:"time_next_update_unix"`
	ErrorType          string             `json:"error-type"`
}

// rateSnapshot is one successful fetch: the rates plus when the provider says
// they were quoted and when it expects to publish again.
type rateSnapshot struct {
	Rates      map[string]float64 `json:"rates"`
	QuotedAt   time.Time          `json:"quoted_at"`
	NextUpdate time.Time          `json:"next_update"`
}

// fetchRates pulls one USD-based quote.
//
// It refuses a response whose USD rate is not 1.0, and one that is missing
// currencies the app can select. Both are cheap guards against replacing a
// working table with a malformed one: every client totals subscriptions with
// whatever this returns, and a partial table would silently convert at 1:1
// rather than fail.
func fetchRates(ctx context.Context, client *http.Client, url string, required []string) (*rateSnapshot, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Accept", "application/json")
	req.Header.Set("User-Agent", "SubFlow/1.0 (+https://subflow.alwaysup.dpdns.org)")

	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		// 429 means we are over the open-access limit; the caller backs off.
		return nil, fmt.Errorf("provider returned HTTP %d", resp.StatusCode)
	}

	// Cap the read: this is an unauthenticated third party, and an unbounded
	// io.ReadAll on a hostile or broken response is an easy way to run a
	// server out of memory.
	body, err := io.ReadAll(io.LimitReader(resp.Body, 4<<20))
	if err != nil {
		return nil, err
	}

	var parsed rateProviderResponse
	if err := json.Unmarshal(body, &parsed); err != nil {
		return nil, fmt.Errorf("provider response was not JSON: %w", err)
	}
	if parsed.Result != "success" {
		return nil, fmt.Errorf("provider reported failure: %s %s", parsed.Result, parsed.ErrorType)
	}
	if parsed.BaseCode != "USD" {
		return nil, fmt.Errorf("expected a USD base, got %q", parsed.BaseCode)
	}
	if r, ok := parsed.Rates["USD"]; !ok || r != 1.0 {
		return nil, fmt.Errorf("USD is not 1.0 in a USD-based response (got %v)", r)
	}

	var missing []string
	for _, code := range required {
		if v, ok := parsed.Rates[code]; !ok || v <= 0 {
			missing = append(missing, code)
		}
	}
	if len(missing) > 0 {
		return nil, fmt.Errorf("provider is missing %d selectable currencies: %v", len(missing), missing)
	}

	snap := &rateSnapshot{Rates: parsed.Rates}
	if parsed.TimeLastUpdateUnix > 0 {
		snap.QuotedAt = time.Unix(parsed.TimeLastUpdateUnix, 0).UTC()
	} else {
		snap.QuotedAt = time.Now().UTC()
	}
	if parsed.TimeNextUpdateUnix > 0 {
		snap.NextUpdate = time.Unix(parsed.TimeNextUpdateUnix, 0).UTC()
	}
	return snap, nil
}
