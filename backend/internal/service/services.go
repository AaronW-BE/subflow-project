package service

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"sort"
	"subflow/backend/internal/model"
	"subflow/backend/internal/repository"
	"sync"
	"time"
)

// fallbackEditedAt dates the hand-written table below.
//
// That table is now only a cold start: it is what /rates answers with in the
// window between the process booting and the first successful fetch, on a first
// ever run with nothing cached. Every other path serves provider data.
//
// It is deliberately not time.Now(). Stamping a compile-time constant with the
// process start time made a table that had never moved report itself as updated
// seconds ago, to every client and to the admin console.
//
// The figures are approximate and known to be stale - measured against live
// data on 2026-09-02, 25 of the 40 were off by more than 5%, the worst being
// TRY at -29.6%. Good enough to render a total for a few seconds at boot,
// nowhere near good enough to ship as the steady state.
var fallbackEditedAt = time.Date(2026, 9, 2, 0, 0, 0, 0, time.UTC)

// RateService serves foreign exchange rates, refreshed from a live provider.
//
// Reads never block on the network. A background loop replaces the table in
// place, and every failure leaves the previous values standing - a provider
// outage must not silently move anyone's totals.
type RateService struct {
	mu    sync.RWMutex
	rates model.CurrencyRates
	live  bool      // true once provider data has replaced the fallback table
	next  time.Time // when the provider says it will publish again

	store  RateSnapshotStore
	client *http.Client

	// providerURL is a field rather than the constant so tests can point it at
	// a stub server.
	providerURL string

	// apiKey selects the keyed endpoint when set. Held only to redact it out
	// of anything logged - it is a path segment of providerURL, so every
	// transport error carries it.
	apiKey string

	// required is the set a fetch must cover to be accepted: the currencies the
	// app can select, fixed at construction. Deriving it from whatever is
	// currently served would ratchet upward - after one good fetch it would be
	// all 166 the provider returns, and dropping a single obscure one would
	// then reject the whole table.
	required []string
}

// RateSnapshotStore persists the last good quote across restarts. It is an
// interface so the refresh logic can be tested without a database.
type RateSnapshotStore interface {
	SaveRateSnapshot(payload string) error
	LoadRateSnapshot() (payload string, fetchedAt time.Time, ok bool, err error)
}

// NewRateService builds the rate service.
//
// apiKey is optional. Empty selects the open, keyless endpoint, which is the
// documented supported configuration and needs no account; a key selects the
// keyed tier. It is never written to a config file in the repo - see
// EXCHANGE_RATE_API_KEY in cmd/server.
func NewRateService(store RateSnapshotStore, apiKey string) *RateService {
	s := &RateService{
		store:       store,
		client:      &http.Client{Timeout: 20 * time.Second},
		apiKey:      apiKey,
		providerURL: providerURLFor(apiKey),
		rates: model.CurrencyRates{
			BaseCurrency: "USD",
			Rates: map[string]float64{
				"USD": 1,
				"EUR": 0.92,
				"GBP": 0.78,
				"JPY": 155.3,
				"CHF": 0.9,
				"CAD": 1.36,
				"AUD": 1.51,
				"NZD": 1.64,
				"CNY": 7.23,
				"HKD": 7.81,
				"TWD": 32.3,
				"SGD": 1.35,
				"KRW": 1370,
				"INR": 83.45,
				"IDR": 16200,
				"THB": 36.5,
				"MYR": 4.7,
				"PHP": 58,
				"VND": 25400,
				"BRL": 5.45,
				"MXN": 18.5,
				"CLP": 950,
				"COP": 4100,
				"ZAR": 18.6,
				"NGN": 1550,
				"EGP": 48.5,
				"TRY": 34,
				"ILS": 3.7,
				"AED": 3.67,
				"SAR": 3.75,
				"PLN": 4,
				"SEK": 10.6,
				"NOK": 10.8,
				"DKK": 6.87,
				"CZK": 23.2,
				"HUF": 355,
				"RON": 4.57,
				"UAH": 41.5,
				"PKR": 278,
				"BDT": 120,
			},
			UpdatedAt:   fallbackEditedAt,
			Provider:    "built-in fallback table",
			ProviderURL: "",
		},
	}
	for code := range s.rates.Rates {
		s.required = append(s.required, code)
	}
	sort.Strings(s.required) // deterministic error messages

	if apiKey != "" {
		log.Println("FX: using the keyed endpoint (EXCHANGE_RATE_API_KEY is set).")
	} else {
		log.Println("FX: using the open keyless endpoint; attribution is required wherever these rates are shown.")
	}

	s.restoreCached()
	return s
}

// restoreCached loads the last good quote so a restart does not regress to the
// fallback table while waiting on the first fetch.
func (s *RateService) restoreCached() {
	if s.store == nil {
		return
	}
	payload, fetchedAt, ok, err := s.store.LoadRateSnapshot()
	if err != nil {
		log.Printf("FX: could not read the cached quote: %v", err)
		return
	}
	if !ok {
		return
	}

	var snap rateSnapshot
	if err := json.Unmarshal([]byte(payload), &snap); err != nil {
		log.Printf("FX: cached quote is unreadable, ignoring it: %v", err)
		return
	}
	s.apply(&snap)
	log.Printf("FX: restored %d cached rates quoted %s (cached %s)",
		len(snap.Rates), snap.QuotedAt.Format(time.RFC3339), fetchedAt.Format(time.RFC3339))
}

// apply swaps in a new table. The caller must not hold the lock.
func (s *RateService) apply(snap *rateSnapshot) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.rates = model.CurrencyRates{
		BaseCurrency: "USD",
		Rates:        snap.Rates,
		UpdatedAt:    snap.QuotedAt,
		Provider:     rateProviderName,
		ProviderURL:  rateProviderLink,
		Keyed:        s.apiKey != "",
	}
	s.live = true
	s.next = snap.NextUpdate
}

// IsLive reports whether the served table came from the provider rather than
// the compile-time fallback. The admin console shows this, because "these
// numbers are hand-written and wrong" is worth saying out loud.
func (s *RateService) IsLive() bool {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.live
}

// Refresh fetches once and swaps the table in on success.
func (s *RateService) Refresh(ctx context.Context) error {
	snap, err := fetchRates(ctx, s.client, s.providerURL, s.apiKey, s.required)
	if err != nil {
		return err
	}
	s.apply(snap)

	if s.store != nil {
		if payload, err := json.Marshal(snap); err != nil {
			log.Printf("FX: could not encode the quote for caching: %v", err)
		} else if err := s.store.SaveRateSnapshot(string(payload)); err != nil {
			log.Printf("FX: could not cache the quote: %v", err)
		}
	}
	return nil
}

// StartRefreshing runs the refresh loop until ctx is cancelled.
//
// The provider publishes once a day and tells us when it will publish next, so
// the loop sleeps until then rather than polling on a timer of our own
// choosing. Failures retry on a short backoff; the previously served table
// stays in place throughout.
func (s *RateService) StartRefreshing(ctx context.Context) {
	go func() {
		const (
			minRetry = 5 * time.Minute
			maxRetry = 2 * time.Hour
			skew     = 5 * time.Minute // let the provider actually publish
		)
		retry := minRetry

		for {
			err := s.Refresh(ctx)
			var wait time.Duration
			if err != nil {
				log.Printf("FX: refresh failed (%v); keeping the current table, retrying in %s", err, retry)
				wait = retry
				if retry *= 2; retry > maxRetry {
					retry = maxRetry
				}
			} else {
				retry = minRetry
				s.mu.RLock()
				quoted := s.rates.UpdatedAt
				s.mu.RUnlock()
				log.Printf("FX: refreshed from %s, quoted %s", rateProviderName, quoted.Format(time.RFC3339))

				wait = time.Until(s.nextUpdate().Add(skew))
				if wait < time.Hour {
					// No usable next-update hint, or it is already past.
					wait = 6 * time.Hour
				}
			}

			select {
			case <-ctx.Done():
				return
			case <-time.After(wait):
			}
		}
	}()
}

func (s *RateService) nextUpdate() time.Time {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.next
}

func (s *RateService) GetRates() model.CurrencyRates {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.rates
}

// Convert converts an amount between two currencies via USD base rate.
func (s *RateService) Convert(amount float64, fromCurrency, toCurrency string) (float64, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	fromRate, ok1 := s.rates.Rates[fromCurrency]
	toRate, ok2 := s.rates.Rates[toCurrency]
	if !ok1 || !ok2 {
		return 0, fmt.Errorf("unsupported currency conversion from %s to %s", fromCurrency, toCurrency)
	}

	// amountInUSD = amount / fromRate
	// result = amountInUSD * toRate
	return (amount / fromRate) * toRate, nil
}

// PresetService provides subscription catalog operations.
type PresetService struct {
	db *repository.DB
}

func NewPresetService(db *repository.DB) *PresetService {
	return &PresetService{db: db}
}

func (s *PresetService) GetPresets(search, category string) ([]model.PresetService, error) {
	return s.db.GetPresets(search, category)
}

func (s *PresetService) SavePreset(p *model.PresetService) error {
	return s.db.SavePreset(p)
}

// DeletePreset reports false when no preset had that id.
func (s *PresetService) DeletePreset(id string) (bool, error) {
	return s.db.DeletePreset(id)
}

// SyncService handles local-first cloud synchronization.
type SyncService struct {
	db *repository.DB
}

func NewSyncService(db *repository.DB) *SyncService {
	return &SyncService{db: db}
}

func (s *SyncService) ProcessSync(userID string, req *model.SyncRequest) (*model.SyncResponse, error) {
	nowMs := time.Now().UnixMilli()

	// 1. Process client mutations
	for i := range req.Subscriptions {
		sub := req.Subscriptions[i]
		sub.UserID = userID
		if sub.UpdatedAt == 0 {
			sub.UpdatedAt = nowMs
		}
		if err := s.db.UpsertSubscription(&sub); err != nil {
			return nil, fmt.Errorf("failed to sync item %s: %w", sub.ID, err)
		}
	}

	// 2. Fetch server-side changes modified after client's last sync
	serverItems, err := s.db.GetSubscriptionsForUser(userID, req.LastSyncTimestamp)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch server changes: %w", err)
	}

	return &model.SyncResponse{
		ServerTimestamp: nowMs,
		Subscriptions:   serverItems,
	}, nil
}
