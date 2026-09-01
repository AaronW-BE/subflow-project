package model

import "time"

// BillingCycle represents recurrence intervals.
type BillingCycle string

const (
	CycleWeekly       BillingCycle = "weekly"
	CycleMonthly      BillingCycle = "monthly"
	CycleQuarterly    BillingCycle = "quarterly"
	CycleSemiAnnually BillingCycle = "semi_annually"
	CycleAnnually     BillingCycle = "annually"
)

// ProTier represents subscription tiers.
type ProTier string

const (
	ProTierFree     ProTier = "free"
	ProTierMonthly  ProTier = "monthly"
	ProTierAnnual   ProTier = "annual"
	ProTierLifetime ProTier = "lifetime"
)

// User represents an application user.
type User struct {
	ID           string     `json:"id"`
	Email        string     `json:"email"`
	Name         string     `json:"name"`
	Picture      string     `json:"picture"`
	AuthProvider string     `json:"auth_provider"` // "google", "apple", "guest"
	IsPro        bool       `json:"is_pro"`
	ProTier      ProTier    `json:"pro_tier"`
	ProExpiresAt *time.Time `json:"pro_expires_at,omitempty"`
	CreatedAt    time.Time  `json:"created_at"`
	LastActiveAt time.Time  `json:"last_active_at"`
}

// Subscription represents a single recurring expense item.
type Subscription struct {
	ID                 string       `json:"id"`
	UserID             string       `json:"user_id"`
	Name               string       `json:"name"`
	Category           string       `json:"category"` // "Entertainment", "Productivity", "Cloud", "Utilities", "Health"
	Amount             float64      `json:"amount"`
	Currency           string       `json:"currency"` // "USD", "EUR", "GBP", "JPY", "CNY", etc.
	Cycle              BillingCycle `json:"cycle"`
	FirstBillDate      string       `json:"first_bill_date"` // YYYY-MM-DD
	NextBillDate       string       `json:"next_bill_date"`  // YYYY-MM-DD
	ReminderDaysBefore int          `json:"reminder_days_before"`
	IsActive           bool         `json:"is_active"`
	ColorHex           string       `json:"color_hex"`
	IconURL            string       `json:"icon_url"`
	Notes              string       `json:"notes,omitempty"`
	UpdatedAt          int64        `json:"updated_at"` // Unix epoch milliseconds for sync
	IsDeleted          bool         `json:"is_deleted"`
}

// Purchase state as reported by the Play Billing client.
const (
	PurchaseStatePurchased = "purchased"
	PurchaseStatePending   = "pending"
	PurchaseStateRefunded  = "refunded"
)

// Purchase is the server-side ledger entry for a Google Play purchase.
//
// The client is the source of the entitlement (Play Billing already gates it
// on device); this record exists so revenue is visible in the Admin Console
// and so a purchase token can later be verified against the Google Play
// Developer API from a trusted context.
type Purchase struct {
	PurchaseToken string     `json:"purchase_token"`
	UserID        string     `json:"user_id"`
	ProductID     string     `json:"product_id"`
	OrderID       string     `json:"order_id"`
	PackageName   string     `json:"package_name"`
	ProTier       ProTier    `json:"pro_tier"`
	State         string     `json:"state"`
	ReportedAt    time.Time  `json:"reported_at"`
	VerifiedAt    *time.Time `json:"verified_at,omitempty"`
}

// PresetService represents a catalog template of a popular service.
type PresetService struct {
	ID               string       `json:"id"`
	Name             string       `json:"name"`
	Category         string       `json:"category"`
	BrandColor       string       `json:"brand_color"`
	IconURL          string       `json:"icon_url"`
	DefaultCycle     BillingCycle `json:"default_cycle"`
	DefaultAmountUSD float64      `json:"default_amount_usd"`
	WebsiteURL       string       `json:"website_url"`
	IsPopular        bool         `json:"is_popular"`
}

// CurrencyRate holds current foreign exchange rates relative to USD.
type CurrencyRates struct {
	BaseCurrency string             `json:"base_currency"` // USD
	Rates        map[string]float64 `json:"rates"`
	UpdatedAt    time.Time          `json:"updated_at"`
}

// SyncRequest represents client mutations to sync.
type SyncRequest struct {
	LastSyncTimestamp int64          `json:"last_sync_timestamp"` // milliseconds
	Subscriptions     []Subscription `json:"subscriptions"`
}

// SyncResponse returns updated items since the requested timestamp.
type SyncResponse struct {
	ServerTimestamp int64          `json:"server_timestamp"`
	Subscriptions   []Subscription `json:"subscriptions"`
}

// AdminKPI holds dashboard analytics metrics.
type AdminKPI struct {
	TotalUsers           int                 `json:"total_users"`
	ActiveUsersDAU       int                 `json:"dau"`
	ActiveUsersMAU       int                 `json:"mau"`
	TotalTrackedSubs     int                 `json:"total_tracked_subs"`
	ProSubscribers       int                 `json:"pro_subscribers"`
	ProConversionRate    float64             `json:"pro_conversion_rate"`
	EstimatedMRR         float64             `json:"estimated_mrr"`
	EstimatedARR         float64             `json:"estimated_arr"`
	TopTrackedServices   []ServicePopularity `json:"top_tracked_services"`
	UserGrowthTrend      []TrendPoint        `json:"user_growth_trend"`
	CategoryDistribution map[string]int      `json:"category_distribution"`
}

type ServicePopularity struct {
	Name       string  `json:"name"`
	Count      int     `json:"count"`
	Percentage float64 `json:"percentage"`
	IconURL    string  `json:"icon_url"`
}

type TrendPoint struct {
	Date           string  `json:"date"`
	NewUsers       int     `json:"new_users"`
	CumulativeSubs int     `json:"cumulative_subs"`
	MRR            float64 `json:"mrr"`
}
