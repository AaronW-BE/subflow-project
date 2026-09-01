package service_test

import (
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
	authSvc := service.NewAuthService(db)
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
	rateSvc := service.NewRateService()
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
	adminSvc := service.NewAdminService(db)
	kpi, err := adminSvc.GetKPIs()
	if err != nil {
		t.Fatalf("GetKPIs failed: %v", err)
	}
	if kpi.TotalUsers == 0 {
		t.Errorf("Expected total users > 0, got %d", kpi.TotalUsers)
	}
}
