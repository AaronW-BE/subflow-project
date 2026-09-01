package service

import (
	"errors"
	"strings"
	"subflow/backend/internal/model"
	"subflow/backend/internal/repository"
	"time"
)

// Play product ids, mirrored from BillingManager.kt.
const (
	SKUMonthly  = "subflow_sub_monthly"
	SKUAnnual   = "subflow_sub_annual"
	SKULifetime = "subflow_inapp_lifetime"
)

// US list prices, used only to estimate revenue on the admin dashboard. Real
// per-country revenue comes from the Play Console payout reports, which also
// net out Play's cut and tax.
//
// These are the prices the paywall was observed rendering against production
// Play on 2026-08-31 (see docs/google-play-release-checklist.md). The values
// they replace - $2.99/mo and $19.99/yr - were the figures from ADR 0002, i.e.
// what the pricing was expected to be before the Console products were created.
// They never matched the shipped products, so every MRR figure the console
// showed was overstated by 1.5x on monthly and 2x on annual.
const (
	priceMonthlyUSD  = 1.99
	priceAnnualUSD   = 9.99
	priceLifetimeUSD = 24.99
)

// skuMonthlyValueUSD is each SKU's contribution to monthly recurring revenue.
var skuMonthlyValueUSD = map[string]float64{
	SKUMonthly:  priceMonthlyUSD,
	SKUAnnual:   priceAnnualUSD / 12.0,
	SKULifetime: 0, // one-time, excluded from recurring revenue
}

type BillingService struct {
	db *repository.DB
}

func NewBillingService(db *repository.DB) *BillingService {
	return &BillingService{db: db}
}

// TierForProduct maps a Play product id onto an entitlement tier.
func TierForProduct(productID string) (model.ProTier, bool) {
	switch productID {
	case SKUMonthly:
		return model.ProTierMonthly, true
	case SKUAnnual:
		return model.ProTierAnnual, true
	case SKULifetime:
		return model.ProTierLifetime, true
	default:
		return model.ProTierFree, false
	}
}

// RecordPurchase stores a purchase reported by a client and grants the
// matching entitlement on the server-side user record.
//
// This does not itself prove the purchase is genuine - the client already
// holds a Play-signed entitlement, and this ledger is what a later
// androidpublisher verification job would run against.
func (s *BillingService) RecordPurchase(
	userID, productID, purchaseToken, orderID, packageName string,
) (*model.Purchase, error) {
	if strings.TrimSpace(purchaseToken) == "" {
		return nil, errors.New("purchase_token is required")
	}
	tier, ok := TierForProduct(productID)
	if !ok {
		return nil, errors.New("unknown product_id: " + productID)
	}

	purchase := &model.Purchase{
		PurchaseToken: purchaseToken,
		UserID:        userID,
		ProductID:     productID,
		OrderID:       orderID,
		PackageName:   packageName,
		ProTier:       tier,
		State:         model.PurchaseStatePurchased,
		ReportedAt:    time.Now(),
	}

	if err := s.db.UpsertPurchase(purchase); err != nil {
		return nil, err
	}

	// Keep the user record in step so the Admin Console and the sync API agree
	// with what the device already unlocked.
	if user, err := s.db.GetUserByID(userID); err == nil && user != nil {
		user.IsPro = true
		user.ProTier = tier
		if tier != model.ProTierLifetime {
			expiry := time.Now().AddDate(0, 1, 0)
			if tier == model.ProTierAnnual {
				expiry = time.Now().AddDate(1, 0, 0)
			}
			user.ProExpiresAt = &expiry
		} else {
			user.ProExpiresAt = nil
		}
		user.LastActiveAt = time.Now()
		_ = s.db.UpsertUser(user)
	}

	return purchase, nil
}

func (s *BillingService) ListPurchases(limit, offset int) ([]model.Purchase, error) {
	if limit <= 0 || limit > 200 {
		limit = 50
	}
	if offset < 0 {
		offset = 0
	}
	return s.db.ListPurchases(limit, offset)
}

// PurchasePage is one page of the ledger plus the size of the whole ledger.
type PurchasePage struct {
	Purchases []model.Purchase `json:"purchases"`
	Total     int              `json:"total"`
	Limit     int              `json:"limit"`
	Offset    int              `json:"offset"`
}

func (s *BillingService) ListPurchasePage(limit, offset int) (*PurchasePage, error) {
	purchases, err := s.ListPurchases(limit, offset)
	if err != nil {
		return nil, err
	}
	total, err := s.db.CountPurchases()
	if err != nil {
		return nil, err
	}
	return &PurchasePage{Purchases: purchases, Total: total, Limit: limit, Offset: offset}, nil
}

// RevenueSummary reports recurring revenue derived from the purchase ledger.
type RevenueSummary struct {
	PurchasesByTier map[string]int `json:"purchases_by_tier"`
	EstimatedMRR    float64        `json:"estimated_mrr"`
	EstimatedARR    float64        `json:"estimated_arr"`
	LifetimeSales   int            `json:"lifetime_sales"`
	// LifetimeGross is what those one-time sales are worth at list price.
	// The count alone was the only lifetime figure on the dashboard, which
	// made the tier look like it contributed nothing.
	LifetimeGross float64 `json:"lifetime_gross"`
	Last30Days    int     `json:"purchases_last_30_days"`
}

func (s *BillingService) RevenueSummary() (*RevenueSummary, error) {
	counts, err := s.db.CountPurchasesByTier()
	if err != nil {
		return nil, err
	}

	summary := &RevenueSummary{PurchasesByTier: counts}
	summary.EstimatedMRR += float64(counts[string(model.ProTierMonthly)]) * skuMonthlyValueUSD[SKUMonthly]
	summary.EstimatedMRR += float64(counts[string(model.ProTierAnnual)]) * skuMonthlyValueUSD[SKUAnnual]
	summary.EstimatedARR = summary.EstimatedMRR * 12
	summary.LifetimeSales = counts[string(model.ProTierLifetime)]
	summary.LifetimeGross = float64(summary.LifetimeSales) * priceLifetimeUSD

	if n, err := s.db.PurchasesSince(time.Now().AddDate(0, 0, -30)); err == nil {
		summary.Last30Days = n
	}
	return summary, nil
}
