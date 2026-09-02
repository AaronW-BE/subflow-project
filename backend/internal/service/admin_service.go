package service

import (
	"fmt"
	"subflow/backend/internal/model"
	"subflow/backend/internal/repository"
	"time"

	"github.com/google/uuid"
)

type AdminService struct {
	db      *repository.DB
	billing *BillingService
}

func NewAdminService(db *repository.DB, billing *BillingService) *AdminService {
	return &AdminService{db: db, billing: billing}
}

// GetKPIs assembles the dashboard figures.
//
// The repository leaves MRR and ARR at zero because it cannot see Play's list
// prices. They are filled in here from the purchase ledger, which is the same
// source the Revenue tab uses - previously the two tabs derived revenue
// differently and disagreed with each other on the same screen.
func (s *AdminService) GetKPIs() (*model.AdminKPI, error) {
	kpi, err := s.db.GetAdminKPI()
	if err != nil {
		return nil, err
	}

	revenue, err := s.billing.RevenueSummary()
	if err != nil {
		// A broken ledger should not blank the whole dashboard; the rest of
		// the KPIs are independent of it and still worth showing.
		return kpi, nil
	}
	kpi.EstimatedMRR = revenue.EstimatedMRR
	kpi.EstimatedARR = revenue.EstimatedARR
	return kpi, nil
}

// UserPage is one page of the user table plus the size of the whole table, so
// the console can tell the operator how much it is not showing.
type UserPage struct {
	Users  []model.User `json:"users"`
	Total  int          `json:"total"`
	Limit  int          `json:"limit"`
	Offset int          `json:"offset"`
}

func (s *AdminService) ListUsers(limit, offset int) (*UserPage, error) {
	if limit <= 0 || limit > 100 {
		limit = 50
	}
	if offset < 0 {
		offset = 0
	}

	users, err := s.db.ListAllUsers(limit, offset)
	if err != nil {
		return nil, err
	}
	if users == nil {
		users = []model.User{}
	}

	total, err := s.db.CountUsers()
	if err != nil {
		return nil, err
	}

	return &UserPage{Users: users, Total: total, Limit: limit, Offset: offset}, nil
}

func (s *AdminService) SetUserProStatus(userID string, isPro bool, tier model.ProTier) error {
	user, err := s.db.GetUserByID(userID)
	if err != nil {
		return fmt.Errorf("user not found: %w", err)
	}

	user.IsPro = isPro
	user.ProTier = tier
	if isPro {
		t := time.Now().AddDate(1, 0, 0)
		user.ProExpiresAt = &t
	} else {
		user.ProExpiresAt = nil
	}
	user.LastActiveAt = time.Now()

	return s.db.UpsertUser(user)
}

// SeedDemoData seeds mock users and activity if the database is newly created.
//
// It returns the number of demo users written, so the console can distinguish
// "seeded" from "declined because this database already has users" - both used
// to come back as a bare nil, and the operator was told it had worked either
// way.
func (s *AdminService) SeedDemoData() (int, error) {
	kpi, err := s.db.GetAdminKPI()
	if err != nil {
		return 0, err
	}
	if kpi.TotalUsers > 3 {
		return 0, nil // already populated
	}

	mockUsers := []struct {
		Email string
		Name  string
		IsPro bool
		Tier  model.ProTier
		Subs  []struct {
			Name, Cat string
			Amt       float64
			Cyc       model.BillingCycle
			Color     string
		}
	}{
		{
			Email: "sarah.connor@gmail.com", Name: "Sarah Connor", IsPro: true, Tier: model.ProTierAnnual,
			Subs: []struct {
				Name, Cat string
				Amt       float64
				Cyc       model.BillingCycle
				Color     string
			}{
				{"Netflix", "Entertainment", 15.49, model.CycleMonthly, "#E50914"},
				{"Spotify", "Entertainment", 10.99, model.CycleMonthly, "#1DB954"},
				{"iCloud+ 2TB", "Cloud", 9.99, model.CycleMonthly, "#007AFF"},
				{"ChatGPT Plus", "Productivity", 20.00, model.CycleMonthly, "#10A37F"},
				{"Gym Membership", "Health", 49.00, model.CycleMonthly, "#E11D48"},
				{"Amazon Prime", "Utilities", 14.99, model.CycleMonthly, "#00A8E1"},
			},
		},
		{
			Email: "david.miller@apple.com", Name: "David Miller", IsPro: true, Tier: model.ProTierLifetime,
			Subs: []struct {
				Name, Cat string
				Amt       float64
				Cyc       model.BillingCycle
				Color     string
			}{
				{"Apple One Premier", "Entertainment", 37.95, model.CycleMonthly, "#1C1C1E"},
				{"GitHub Copilot", "Productivity", 10.00, model.CycleMonthly, "#24292F"},
				{"Notion Team", "Productivity", 16.00, model.CycleMonthly, "#000000"},
				{"Claude Pro", "Productivity", 20.00, model.CycleMonthly, "#D97706"},
			},
		},
		{
			Email: "elena.rostova@berlin.de", Name: "Elena Rostova", IsPro: false, Tier: model.ProTierFree,
			Subs: []struct {
				Name, Cat string
				Amt       float64
				Cyc       model.BillingCycle
				Color     string
			}{
				{"Spotify", "Entertainment", 10.99, model.CycleMonthly, "#1DB954"},
				{"Duolingo", "Utilities", 6.99, model.CycleMonthly, "#58CC02"},
			},
		},
	}

	now := time.Now()
	for _, m := range mockUsers {
		uID := "usr_" + uuid.New().String()[:8]
		user := &model.User{
			ID:           uID,
			Email:        m.Email,
			Name:         m.Name,
			Picture:      "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100",
			AuthProvider: "google",
			IsPro:        m.IsPro,
			ProTier:      m.Tier,
			CreatedAt:    now.AddDate(0, 0, -14),
			LastActiveAt: now,
		}
		if m.IsPro {
			exp := now.AddDate(1, 0, 0)
			user.ProExpiresAt = &exp
		}
		_ = s.db.UpsertUser(user)

		for _, sub := range m.Subs {
			_ = s.db.UpsertSubscription(&model.Subscription{
				ID:                 "sub_" + uuid.New().String()[:8],
				UserID:             uID,
				Name:               sub.Name,
				Category:           sub.Cat,
				Amount:             sub.Amt,
				Currency:           "USD",
				Cycle:              sub.Cyc,
				FirstBillDate:      now.AddDate(0, -2, 0).Format("2006-01-02"),
				NextBillDate:       now.AddDate(0, 0, 5).Format("2006-01-02"),
				ReminderDaysBefore: 3,
				IsActive:           true,
				ColorHex:           sub.Color,
				UpdatedAt:          now.UnixMilli(),
			})
		}
	}

	return len(mockUsers), nil
}
