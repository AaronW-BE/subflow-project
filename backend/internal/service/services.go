package service

import (
	"fmt"
	"subflow/backend/internal/model"
	"subflow/backend/internal/repository"
	"sync"
	"time"
)

// RateService manages live foreign exchange rates.
type RateService struct {
	mu    sync.RWMutex
	rates model.CurrencyRates
}

func NewRateService() *RateService {
	s := &RateService{
		rates: model.CurrencyRates{
			BaseCurrency: "USD",
			Rates: map[string]float64{
				"USD": 1.0,
				"EUR": 0.92,
				"GBP": 0.78,
				"JPY": 155.30,
				"CAD": 1.36,
				"AUD": 1.51,
				"CHF": 0.90,
				"CNY": 7.23,
				"INR": 83.45,
				"BRL": 5.45,
				"KRW": 1370.0,
			},
			UpdatedAt: time.Now(),
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
