package service

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

// A fetch is only allowed to replace the served table if it is complete and
// coherent. These cases are the ones that would otherwise convert silently at
// the wrong rate rather than fail loudly.
func TestFetchRatesRejectsBadResponses(t *testing.T) {
	required := []string{"USD", "EUR", "KRW"}

	cases := []struct {
		name string
		code int
		body string
	}{
		{
			name: "provider reported an error",
			code: 200,
			body: `{"result":"error","error-type":"unsupported-code"}`,
		},
		{
			name: "not a USD base",
			code: 200,
			body: `{"result":"success","base_code":"EUR","rates":{"USD":1,"EUR":1,"KRW":1}}`,
		},
		{
			name: "USD is not 1.0 in a USD-based table",
			code: 200,
			body: `{"result":"success","base_code":"USD","rates":{"USD":1.07,"EUR":0.92,"KRW":1370}}`,
		},
		{
			name: "missing a currency the app can select",
			code: 200,
			body: `{"result":"success","base_code":"USD","rates":{"USD":1,"EUR":0.92}}`,
		},
		{
			name: "a selectable currency is zero",
			code: 200,
			body: `{"result":"success","base_code":"USD","rates":{"USD":1,"EUR":0.92,"KRW":0}}`,
		},
		{
			name: "rate limited",
			code: 429,
			body: `too many requests`,
		},
		{
			name: "not JSON at all",
			code: 200,
			body: `<html>captive portal</html>`,
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
				w.WriteHeader(tc.code)
				_, _ = w.Write([]byte(tc.body))
			}))
			defer srv.Close()

			snap, err := fetchAt(t, srv.URL, required)
			if err == nil {
				t.Fatalf("expected a rejection, got a usable snapshot: %+v", snap)
			}
		})
	}
}

func TestFetchRatesAcceptsAGoodResponse(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`{
			"result":"success","base_code":"USD",
			"time_last_update_unix":1788220951,
			"time_next_update_unix":1788308781,
			"rates":{"USD":1,"EUR":0.86281,"KRW":1374.61}
		}`))
	}))
	defer srv.Close()

	snap, err := fetchAt(t, srv.URL, []string{"USD", "EUR", "KRW"})
	if err != nil {
		t.Fatalf("expected success, got %v", err)
	}
	if got := snap.Rates["KRW"]; got != 1374.61 {
		t.Errorf("KRW = %v, want 1374.61", got)
	}
	// The quote time must come from the provider, not from our clock - that
	// distinction is the whole reason UpdatedAt stopped being time.Now().
	want := time.Unix(1788220951, 0).UTC()
	if !snap.QuotedAt.Equal(want) {
		t.Errorf("QuotedAt = %s, want %s", snap.QuotedAt, want)
	}
	if !snap.NextUpdate.Equal(time.Unix(1788308781, 0).UTC()) {
		t.Errorf("NextUpdate = %s, want the provider's value", snap.NextUpdate)
	}
}

// memStore is a RateSnapshotStore that keeps the payload in memory.
type memStore struct {
	payload string
	saved   bool
}

func (m *memStore) SaveRateSnapshot(p string) error { m.payload, m.saved = p, true; return nil }
func (m *memStore) LoadRateSnapshot() (string, time.Time, bool, error) {
	return m.payload, time.Now(), m.saved, nil
}

// A failed refresh must leave the previous table standing. Falling back to the
// compile-time values on every hiccup would move every user's totals by up to
// 30%, which is the error measured in the hand-written table.
func TestFailedRefreshKeepsPreviousRates(t *testing.T) {
	store := &memStore{}
	svc := NewRateService(store, "")

	body := fullBody(svc, map[string]float64{"EUR": 0.86281, "KRW": 1374.61})
	good := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(body))
	}))
	svc.providerURL = good.URL
	if err := svc.Refresh(context.Background()); err != nil {
		t.Fatalf("first refresh failed: %v", err)
	}
	good.Close()

	if !svc.IsLive() {
		t.Error("expected the service to report live provider data")
	}
	if !store.saved {
		t.Error("expected a successful fetch to be cached")
	}
	before := svc.GetRates()
	if before.Rates["KRW"] != 1374.61 {
		t.Fatalf("KRW = %v, want the fetched value", before.Rates["KRW"])
	}
	if before.Provider != rateProviderName {
		t.Errorf("Provider = %q, want the attribution to travel with the rates", before.Provider)
	}

	// Now the provider goes down.
	down := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer down.Close()
	svc.providerURL = down.URL

	if err := svc.Refresh(context.Background()); err == nil {
		t.Fatal("expected the refresh to fail")
	}
	after := svc.GetRates()
	if after.Rates["KRW"] != before.Rates["KRW"] || !after.UpdatedAt.Equal(before.UpdatedAt) {
		t.Errorf("a failed refresh changed the served table: %v -> %v", before, after)
	}
}

// A restart must come back on the cached quote, not on the fallback table.
func TestRestoresCachedQuoteOnStart(t *testing.T) {
	store := &memStore{}
	first := NewRateService(store, "")
	body := fullBody(first, map[string]float64{"EUR": 0.86281, "KRW": 1374.61})
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(body))
	}))
	defer srv.Close()

	first.providerURL = srv.URL
	if err := first.Refresh(context.Background()); err != nil {
		t.Fatalf("refresh failed: %v", err)
	}

	// Same store, fresh process, no network yet.
	second := NewRateService(store, "")
	if !second.IsLive() {
		t.Error("expected the restarted service to serve the cached quote")
	}
	if got := second.GetRates().Rates["KRW"]; got != 1374.61 {
		t.Errorf("KRW after restart = %v, want the cached 1374.61", got)
	}
}

// fullBody builds a provider response covering everything svc requires, so a
// test exercising refresh behaviour is not accidentally testing the
// completeness guard instead.
func fullBody(svc *RateService, overrides map[string]float64) string {
	rates := map[string]float64{}
	for _, code := range svc.required {
		rates[code] = 2.0
	}
	rates["USD"] = 1.0
	for k, v := range overrides {
		rates[k] = v
	}
	payload, err := json.Marshal(map[string]any{
		"result":                "success",
		"base_code":             "USD",
		"time_last_update_unix": 1788220951,
		"time_next_update_unix": 1788308781,
		"rates":                 rates,
	})
	if err != nil {
		panic(err)
	}
	return string(payload)
}

func fetchAt(t *testing.T, url string, required []string) (*rateSnapshot, error) {
	t.Helper()
	svc := NewRateService(nil, "")
	svc.providerURL = url
	return fetchRates(context.Background(), svc.client, url, "", required)
}

// The keyed endpoints name the rate map "conversion_rates" where the open one
// says "rates". Both must work, because which endpoint is in use depends only
// on whether EXCHANGE_RATE_API_KEY happens to be set.
func TestFetchRatesAcceptsKeyedResponseShape(t *testing.T) {
	svc := NewRateService(nil, "")

	rates := map[string]float64{"USD": 1.0}
	for _, code := range svc.required {
		if _, ok := rates[code]; !ok {
			rates[code] = 2.0
		}
	}
	rates["KRW"] = 1374.61
	payload, err := json.Marshal(map[string]any{
		"result":                "success",
		"base_code":             "USD",
		"time_last_update_unix": 1788220951,
		"conversion_rates":      rates, // note: not "rates"
	})
	if err != nil {
		t.Fatal(err)
	}

	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write(payload)
	}))
	defer srv.Close()

	snap, err := fetchRates(context.Background(), svc.client, srv.URL, "", svc.required)
	if err != nil {
		t.Fatalf("keyed response shape rejected: %v", err)
	}
	if snap.Rates["KRW"] != 1374.61 {
		t.Errorf("KRW = %v, want the value from conversion_rates", snap.Rates["KRW"])
	}
}

func TestProviderURLForSelectsEndpoint(t *testing.T) {
	if got := providerURLFor(""); got != rateProviderURL {
		t.Errorf("empty key should use the open endpoint, got %s", got)
	}
	got := providerURLFor("abc123")
	if !strings.Contains(got, "v6.exchangerate-api.com") || !strings.Contains(got, "abc123") {
		t.Errorf("keyed URL = %s, want the keyed host with the key in the path", got)
	}
}

// The key is a path segment, so it rides along inside every transport error -
// and those get logged on each retry. A leaked key in a log file is a leaked
// credential.
func TestTransportErrorsDoNotLeakTheKey(t *testing.T) {
	const key = "super-secret-key-value"

	// A closed server guarantees a connection-level failure, which is the case
	// that carries the URL.
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {}))
	endpoint := srv.URL + "/v6/" + key + "/latest/USD"
	srv.Close()

	svc := NewRateService(nil, "")
	_, err := fetchRates(context.Background(), svc.client, endpoint, key, svc.required)
	if err == nil {
		t.Fatal("expected the request to fail")
	}
	if strings.Contains(err.Error(), key) {
		t.Fatalf("the API key leaked into the error text: %s", err)
	}
	if !strings.Contains(err.Error(), "REDACTED") {
		t.Errorf("expected the key to be redacted, got: %s", err)
	}
}

// An invalid key is not a transient failure, and saying so is the difference
// between an operator noticing and a retry loop filling the log.
func TestOperatorActionableErrorsAreCalledOut(t *testing.T) {
	for _, errType := range []string{"invalid-key", "inactive-account", "quota-reached"} {
		srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			_, _ = w.Write([]byte(`{"result":"error","error-type":"` + errType + `"}`))
		}))
		svc := NewRateService(nil, "")
		_, err := fetchRates(context.Background(), svc.client, srv.URL, "", svc.required)
		srv.Close()

		if err == nil {
			t.Fatalf("%s: expected an error", errType)
		}
		if !strings.Contains(err.Error(), errType) ||
			!strings.Contains(err.Error(), "needs attention") {
			t.Errorf("%s: expected an operator-actionable message, got: %v", errType, err)
		}
	}
}

// The keyed endpoint answers a bad key with a bare 403, not the documented
// JSON error body, so the status code is all the operator gets to work with.
func TestBadKeyStatusIsExplained(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusForbidden)
	}))
	defer srv.Close()

	svc := NewRateService(nil, "")
	_, err := fetchRates(context.Background(), svc.client, srv.URL, "a-key", svc.required)
	if err == nil {
		t.Fatal("expected an error")
	}
	if !strings.Contains(err.Error(), "EXCHANGE_RATE_API_KEY") {
		t.Errorf("a 403 with a key set should name the key, got: %v", err)
	}

	// Without a key configured, 403 is not a key problem and must not claim to be.
	_, err = fetchRates(context.Background(), svc.client, srv.URL, "", svc.required)
	if err == nil || strings.Contains(err.Error(), "EXCHANGE_RATE_API_KEY") {
		t.Errorf("keyless 403 should not blame the key, got: %v", err)
	}
}
