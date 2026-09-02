package service

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
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
	// Open access: no key, rate limited, attribution required.
	rateProviderURL = "https://open.er-api.com/v6/latest/USD"
	// Keyed tiers: a different host, and the key sits in the path. Attribution
	// is not required on these, but see redactKey - a key in the path means
	// every transport error carries it, and those get logged.
	rateProviderKeyedURL = "https://v6.exchangerate-api.com/v6/%s/latest/USD"

	rateProviderName = "Exchange Rate API" // renders as "Rates By Exchange Rate API"
	rateProviderLink = "https://www.exchangerate-api.com"
)

// providerURLFor picks the endpoint for the configured key.
//
// An empty key is the normal, supported case: the open endpoint needs none.
func providerURLFor(apiKey string) string {
	if apiKey == "" {
		return rateProviderURL
	}
	return fmt.Sprintf(rateProviderKeyedURL, url.PathEscape(apiKey))
}

// redactKey strips the API key out of anything about to be logged.
//
// net/http puts the full request URL into every *url.Error, and on the keyed
// endpoint the key is a path segment - so a plain DNS failure would otherwise
// print the credential into the server log on every retry.
func redactKey(text, apiKey string) string {
	if apiKey == "" {
		return text
	}
	return strings.ReplaceAll(text, apiKey, "REDACTED")
}

// rateProviderResponse is the subset of the payload we use.
type rateProviderResponse struct {
	Result   string `json:"result"`
	BaseCode string `json:"base_code"`
	// The open endpoint calls this "rates"; the keyed endpoints call the same
	// thing "conversion_rates". Both are decoded and whichever arrived is used,
	// so one code path serves either.
	Rates              map[string]float64 `json:"rates"`
	ConversionRates    map[string]float64 `json:"conversion_rates"`
	TimeLastUpdateUnix int64              `json:"time_last_update_unix"`
	TimeNextUpdateUnix int64              `json:"time_next_update_unix"`
	ErrorType          string             `json:"error-type"`
}

// table returns whichever rate map the endpoint populated.
func (r *rateProviderResponse) table() map[string]float64 {
	if len(r.ConversionRates) > 0 {
		return r.ConversionRates
	}
	return r.Rates
}

// operatorActionable reports whether an error needs a human rather than a
// retry. Retrying an invalid key just burns the log until someone looks.
func operatorActionable(errorType string) bool {
	switch errorType {
	case "invalid-key", "inactive-account", "quota-reached":
		return true
	default:
		return false
	}
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
func fetchRates(
	ctx context.Context, client *http.Client, endpoint, apiKey string, required []string,
) (*rateSnapshot, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err != nil {
		return nil, errors.New(redactKey(err.Error(), apiKey))
	}
	req.Header.Set("Accept", "application/json")
	req.Header.Set("User-Agent", "SubFlow/1.0 (+https://subflow.alwaysup.dpdns.org)")

	resp, err := client.Do(req)
	if err != nil {
		// *url.Error embeds the request URL, which on the keyed endpoint
		// contains the key.
		return nil, errors.New(redactKey(err.Error(), apiKey))
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		switch {
		case resp.StatusCode == http.StatusTooManyRequests:
			// Over the open-access limit. Clears on its own; the caller backs off.
			return nil, errors.New("provider rate limited us (HTTP 429)")
		case (resp.StatusCode == http.StatusForbidden ||
			resp.StatusCode == http.StatusUnauthorized) && apiKey != "":
			// The keyed endpoint answers a bad key with a bare 403 rather than
			// the documented JSON error body, so without this the operator just
			// sees a status code and has to guess.
			return nil, fmt.Errorf(
				"provider rejected the API key (HTTP %d) - check EXCHANGE_RATE_API_KEY; "+
					"retrying will not fix it", resp.StatusCode,
			)
		default:
			return nil, fmt.Errorf("provider returned HTTP %d", resp.StatusCode)
		}
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
		if operatorActionable(parsed.ErrorType) {
			return nil, fmt.Errorf(
				"provider rejected the request: %s - this needs attention, retrying will not fix it",
				parsed.ErrorType,
			)
		}
		return nil, fmt.Errorf("provider reported failure: %s %s", parsed.Result, parsed.ErrorType)
	}
	if parsed.BaseCode != "USD" {
		return nil, fmt.Errorf("expected a USD base, got %q", parsed.BaseCode)
	}
	rates := parsed.table()
	if len(rates) == 0 {
		return nil, errors.New("provider returned no rates")
	}
	if r, ok := rates["USD"]; !ok || r != 1.0 {
		return nil, fmt.Errorf("USD is not 1.0 in a USD-based response (got %v)", r)
	}

	var missing []string
	for _, code := range required {
		if v, ok := rates[code]; !ok || v <= 0 {
			missing = append(missing, code)
		}
	}
	if len(missing) > 0 {
		return nil, fmt.Errorf("provider is missing %d selectable currencies: %v", len(missing), missing)
	}

	snap := &rateSnapshot{Rates: rates}
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
