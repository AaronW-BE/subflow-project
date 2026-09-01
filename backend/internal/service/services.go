package service

import (
	"fmt"
	"subflow/backend/internal/model"
	"subflow/backend/internal/repository"
	"sync"
	"time"
)

// ratesEditedAt is when the table below was last edited by hand.
//
// It is deliberately not time.Now(). There is no live feed behind this service
// - the map is a compile-time constant - and stamping it with the process start
// time made a table that had not moved in months report itself as updated
// seconds ago, on both the admin console and every client that reads /rates.
//
// The figures are approximate reference rates, good enough to total a
// subscription list and not good enough to settle a payment. Wiring a real
// provider means replacing this whole block and setting UpdatedAt from the
// provider's own quote time, not from the clock.
var ratesEditedAt = time.Date(2026, 9, 2, 0, 0, 0, 0, time.UTC)

// RateService manages foreign exchange rates.
type RateService struct {
	mu    sync.RWMutex
	rates model.CurrencyRates
}

func NewRateService() *RateService {
	s := &RateService{
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
			UpdatedAt: ratesEditedAt,
		},
	}
	return s
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
