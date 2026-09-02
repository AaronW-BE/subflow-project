package service_test

import (
	"math"
	"os"
	"subflow/backend/internal/model"
	"subflow/backend/internal/repository"
	"subflow/backend/internal/service"
	"testing"
	"time"
)

func TestBackendCoreFlows(t *testing.T) {
	testDBPath := "test_subflow.db"
	defer os.Remove(testDBPath)

	db, err := repository.InitDB(testDBPath)
	if err != nil {
		t.Fatalf("InitDB failed: %v", err)
	}
	defer db.Close()

	// 1. Test Presets seeded
	presetSvc := service.NewPresetService(db)
	presets, err := presetSvc.GetPresets("", "")
	if err != nil {
		t.Fatalf("GetPresets failed: %v", err)
	}
	if len(presets) < 15 {
		t.Errorf("Expected at least 15 presets, got %d", len(presets))
	}

	// 2. Test Guest Authentication & JWT
	authSvc := service.NewAuthService(db, "test-secret")
	user, token, err := authSvc.AuthenticateAsGuest("")
	if err != nil {
		t.Fatalf("AuthenticateAsGuest failed: %v", err)
	}
	if user == nil || token == "" {
		t.Fatalf("Expected valid user and token, got user=%v, token=%s", user, token)
	}

	// 3. Test Validate JWT
	claims, err := authSvc.ValidateJWT(token)
	if err != nil {
		t.Fatalf("ValidateJWT failed: %v", err)
	}
	if claims.UserID != user.ID {
		t.Errorf("Expected user ID %s, got %s", user.ID, claims.UserID)
	}

	// 4. Test Rates Conversion
	rateSvc := service.NewRateService(db, "")
	converted, err := rateSvc.Convert(100.0, "USD", "EUR")
	if err != nil {
		t.Fatalf("Convert failed: %v", err)
	}
	if converted <= 0 {
		t.Errorf("Expected positive converted amount, got %f", converted)
	}

	// 5. Test Local-First Sync
	syncSvc := service.NewSyncService(db)
	nowMs := time.Now().UnixMilli()
	subItem := model.Subscription{
		ID:                 "sub_client_1",
		UserID:             user.ID,
		Name:               "Netflix",
		Category:           "Entertainment",
		Amount:             15.49,
		Currency:           "USD",
		Cycle:              model.CycleMonthly,
		FirstBillDate:      "2026-08-01",
		NextBillDate:       "2026-09-01",
		ReminderDaysBefore: 3,
		IsActive:           true,
		ColorHex:           "#E50914",
		UpdatedAt:          nowMs,
	}

	syncResp, err := syncSvc.ProcessSync(user.ID, &model.SyncRequest{
		LastSyncTimestamp: 0,
		Subscriptions:     []model.Subscription{subItem},
	})
	if err != nil {
		t.Fatalf("ProcessSync failed: %v", err)
	}
	if len(syncResp.Subscriptions) == 0 {
		t.Fatalf("Expected at least 1 synced subscription returned, got 0")
	}

	// 6. Test Admin KPI
	billingSvc := service.NewBillingService(db)
	adminSvc := service.NewAdminService(db, billingSvc)
	kpi, err := adminSvc.GetKPIs()
	if err != nil {
		t.Fatalf("GetKPIs failed: %v", err)
	}
	if kpi.TotalUsers == 0 {
		t.Errorf("Expected total users > 0, got %d", kpi.TotalUsers)
	}

	// The dashboard used to synthesise DAU as TotalUsers*0.45+1 whenever the
	// real count came back zero. This user was just created, so DAU is 1 - and
	// crucially it must not exceed the number of users that exist.
	if kpi.ActiveUsersDAU > kpi.TotalUsers {
		t.Errorf("DAU %d exceeds total users %d - a real count cannot", kpi.ActiveUsersDAU, kpi.TotalUsers)
	}

	// Nobody has bought anything in this database, so recurring revenue is
	// zero. The previous formula reported ProSubscribers * 2.49 here, which
	// billed admin-granted entitlements as though they were sales.
	if kpi.EstimatedMRR != 0 {
		t.Errorf("Expected zero MRR with an empty purchase ledger, got %f", kpi.EstimatedMRR)
	}

	if len(kpi.UserGrowthTrend) != 7 {
		t.Fatalf("Expected a 7-day trend, got %d points", len(kpi.UserGrowthTrend))
	}
	for _, pt := range kpi.UserGrowthTrend {
		if pt.NewUsers < 0 || pt.NewPurchases < 0 {
			t.Errorf("Trend point %s has a negative count: %+v", pt.Date, pt)
		}
	}
}

// TestRevenueMatchesPlayListPrices pins the estimates to the prices the paywall
// was observed rendering against production Play, so a future edit to the SKU
// table cannot quietly go back to the pre-launch guesses from ADR 0002.
func TestRevenueMatchesPlayListPrices(t *testing.T) {
	testDBPath := "test_revenue.db"
	defer os.Remove(testDBPath)

	db, err := repository.InitDB(testDBPath)
	if err != nil {
		t.Fatalf("InitDB failed: %v", err)
	}
	defer db.Close()

	authSvc := service.NewAuthService(db, "test-secret")
	user, _, err := authSvc.AuthenticateAsGuest("")
	if err != nil {
		t.Fatalf("AuthenticateAsGuest failed: %v", err)
	}

	billingSvc := service.NewBillingService(db)
	for _, tc := range []struct{ product, token string }{
		{service.SKUMonthly, "tok_monthly"},
		{service.SKUAnnual, "tok_annual"},
		{service.SKULifetime, "tok_lifetime"},
	} {
		if _, err := billingSvc.RecordPurchase(user.ID, tc.product, tc.token, "", ""); err != nil {
			t.Fatalf("RecordPurchase(%s) failed: %v", tc.product, err)
		}
	}

	summary, err := billingSvc.RevenueSummary()
	if err != nil {
		t.Fatalf("RevenueSummary failed: %v", err)
	}

	// $1.99/month + $9.99/year. The lifetime sale is one-time and must not
	// appear in a recurring figure.
	wantMRR := 1.99 + 9.99/12.0
	if math.Abs(summary.EstimatedMRR-wantMRR) > 0.001 {
		t.Errorf("EstimatedMRR = %f, want %f", summary.EstimatedMRR, wantMRR)
	}
	if math.Abs(summary.EstimatedARR-wantMRR*12) > 0.001 {
		t.Errorf("EstimatedARR = %f, want %f", summary.EstimatedARR, wantMRR*12)
	}
	if summary.LifetimeSales != 1 {
		t.Errorf("LifetimeSales = %d, want 1", summary.LifetimeSales)
	}
	if math.Abs(summary.LifetimeGross-24.99) > 0.001 {
		t.Errorf("LifetimeGross = %f, want 24.99", summary.LifetimeGross)
	}
}

// TestDeletePresetReportsMisses guards the difference between "removed" and
// "there was nothing with that id" - a DELETE that matches no row is not an
// SQL error, so the console used to report a successful delete for a typo.
func TestDeletePresetReportsMisses(t *testing.T) {
	testDBPath := "test_presets.db"
	defer os.Remove(testDBPath)

	db, err := repository.InitDB(testDBPath)
	if err != nil {
		t.Fatalf("InitDB failed: %v", err)
	}
	defer db.Close()

	presetSvc := service.NewPresetService(db)
	presets, err := presetSvc.GetPresets("", "")
	if err != nil || len(presets) == 0 {
		t.Fatalf("expected seeded presets, got %d (err %v)", len(presets), err)
	}

	deleted, err := presetSvc.DeletePreset(presets[0].ID)
	if err != nil {
		t.Fatalf("DeletePreset failed: %v", err)
	}
	if !deleted {
		t.Errorf("Expected %s to be deleted", presets[0].ID)
	}

	deleted, err = presetSvc.DeletePreset("no_such_preset")
	if err != nil {
		t.Fatalf("DeletePreset on a missing id returned an error: %v", err)
	}
	if deleted {
		t.Error("Expected deleted=false for an id that does not exist")
	}
}

// TestListUsersReportsTotal covers the pagination contract the console relies
// on to say "showing N of M" rather than presenting a page as the whole table.
func TestListUsersReportsTotal(t *testing.T) {
	testDBPath := "test_paging.db"
	defer os.Remove(testDBPath)

	db, err := repository.InitDB(testDBPath)
	if err != nil {
		t.Fatalf("InitDB failed: %v", err)
	}
	defer db.Close()

	authSvc := service.NewAuthService(db, "test-secret")
	for i := 0; i < 5; i++ {
		if _, _, err := authSvc.AuthenticateAsGuest(""); err != nil {
			t.Fatalf("AuthenticateAsGuest failed: %v", err)
		}
	}

	adminSvc := service.NewAdminService(db, service.NewBillingService(db))
	page, err := adminSvc.ListUsers(2, 0)
	if err != nil {
		t.Fatalf("ListUsers failed: %v", err)
	}
	if len(page.Users) != 2 {
		t.Errorf("Expected 2 users on the page, got %d", len(page.Users))
	}
	if page.Total != 5 {
		t.Errorf("Expected a total of 5, got %d", page.Total)
	}

	// A limit past the cap must fall back to the default rather than being
	// passed through to SQLite.
	capped, err := adminSvc.ListUsers(9999, -3)
	if err != nil {
		t.Fatalf("ListUsers with out-of-range paging failed: %v", err)
	}
	if capped.Limit != 50 || capped.Offset != 0 {
		t.Errorf("Expected limit/offset to clamp to 50/0, got %d/%d", capped.Limit, capped.Offset)
	}
}
