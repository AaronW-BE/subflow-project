package repository

import (
	"database/sql"
	"path/filepath"
	"testing"
	"time"

	"subflow/backend/internal/model"
)

// A subscription has to come back from the server as the same subscription it
// went in as. That sounds obvious enough not to test, which is exactly why the
// client and the server disagreed about every date field for as long as they
// did without anything failing.
func TestSubscriptionRoundTripKeepsTrialFields(t *testing.T) {
	db := newTestDB(t)
	seedUser(t, db, "u1")

	now := time.Now().UnixMilli()
	in := &model.Subscription{
		ID:                 "sub_1",
		UserID:             "u1",
		Name:               "Claude Pro",
		Category:           "Productivity",
		Amount:             0, // a running trial costs nothing today
		Currency:           "USD",
		Cycle:              model.CycleMonthly,
		FirstBillDate:      "2026-09-01",
		NextBillDate:       "2026-09-19",
		ReminderDaysBefore: 1,
		IsActive:           true,
		ColorHex:           "#D97757",
		UpdatedAt:          now,
		IsTrial:            true,
		TrialEndDate:       "2026-09-19",
		TrialConverts:      true,
		PostTrialAmount:    20,
		PostTrialCycle:     model.CycleAnnually,
		TrialOutcome:       "",
	}
	if err := db.UpsertSubscription(in); err != nil {
		t.Fatalf("upsert: %v", err)
	}

	got, err := db.GetSubscriptionsForUser("u1", 0)
	if err != nil {
		t.Fatalf("read back: %v", err)
	}
	if len(got) != 1 {
		t.Fatalf("expected 1 subscription, got %d", len(got))
	}
	out := got[0]

	if !out.IsTrial {
		t.Error("IsTrial was lost")
	}
	if out.TrialEndDate != in.TrialEndDate {
		t.Errorf("TrialEndDate = %q, want %q", out.TrialEndDate, in.TrialEndDate)
	}
	if !out.TrialConverts {
		t.Error("TrialConverts was lost")
	}
	if out.PostTrialAmount != in.PostTrialAmount {
		t.Errorf("PostTrialAmount = %v, want %v", out.PostTrialAmount, in.PostTrialAmount)
	}
	// The cycle it converts to, not the one it currently carries: getting these
	// two the wrong way round would put an annual charge in every month.
	if out.PostTrialCycle != model.CycleAnnually {
		t.Errorf("PostTrialCycle = %q, want %q", out.PostTrialCycle, model.CycleAnnually)
	}
	if out.Cycle != model.CycleMonthly {
		t.Errorf("Cycle = %q, want %q", out.Cycle, model.CycleMonthly)
	}
	// The fields that were being dropped before the wire format was fixed.
	if out.FirstBillDate != in.FirstBillDate || out.NextBillDate != in.NextBillDate {
		t.Errorf("dates = %q/%q, want %q/%q", out.FirstBillDate, out.NextBillDate, in.FirstBillDate, in.NextBillDate)
	}
	if !out.IsActive || out.ColorHex != in.ColorHex || out.UpdatedAt != in.UpdatedAt {
		t.Errorf("active/colour/timestamp = %v/%q/%d", out.IsActive, out.ColorHex, out.UpdatedAt)
	}
}

// The columns are added by ensureColumns rather than by CREATE TABLE, because
// CREATE TABLE IF NOT EXISTS does nothing to a database that already exists.
// A server upgraded in place has to keep the rows it already had.
func TestMigrateAddsTrialColumnsToAnExistingDatabase(t *testing.T) {
	path := filepath.Join(t.TempDir(), "old.db")

	// A database in the shape it had before trials existed.
	old, err := sql.Open("sqlite", path)
	if err != nil {
		t.Fatalf("open: %v", err)
	}
	if _, err := old.Exec(`CREATE TABLE subscriptions (
		id TEXT PRIMARY KEY, user_id TEXT, name TEXT, category TEXT, amount REAL,
		currency TEXT, cycle TEXT, first_bill_date TEXT, next_bill_date TEXT,
		reminder_days_before INTEGER, is_active INTEGER DEFAULT 1, color_hex TEXT,
		icon_url TEXT, notes TEXT, updated_at INTEGER, is_deleted INTEGER DEFAULT 0);`); err != nil {
		t.Fatalf("create old table: %v", err)
	}
	if _, err := old.Exec(`INSERT INTO subscriptions (id, user_id, name, amount, currency, cycle, updated_at)
		VALUES ('legacy', 'u1', 'Netflix', 15.49, 'USD', 'monthly', 1);`); err != nil {
		t.Fatalf("insert legacy row: %v", err)
	}
	if err := old.Close(); err != nil {
		t.Fatalf("close: %v", err)
	}

	db, err := InitDB(path)
	if err != nil {
		t.Fatalf("InitDB on an existing database: %v", err)
	}
	defer db.Close()

	for _, column := range []string{
		"is_trial", "trial_end_date", "trial_converts",
		"post_trial_amount", "post_trial_cycle", "trial_outcome",
	} {
		if !hasColumn(t, db, "subscriptions", column) {
			t.Errorf("column %q was not added", column)
		}
	}

	var name string
	var amount float64
	if err := db.conn.QueryRow("SELECT name, amount FROM subscriptions WHERE id = 'legacy'").Scan(&name, &amount); err != nil {
		t.Fatalf("legacy row is gone: %v", err)
	}
	if name != "Netflix" || amount != 15.49 {
		t.Errorf("legacy row changed: %q %v", name, amount)
	}
}

// Running the migration twice must be a no-op; SQLite errors on a duplicate
// ADD COLUMN rather than shrugging, so every server restart would fail.
func TestMigrateIsIdempotent(t *testing.T) {
	path := filepath.Join(t.TempDir(), "twice.db")

	first, err := InitDB(path)
	if err != nil {
		t.Fatalf("first init: %v", err)
	}
	first.Close()

	second, err := InitDB(path)
	if err != nil {
		t.Fatalf("second init on the same database: %v", err)
	}
	second.Close()
}

func newTestDB(t *testing.T) *DB {
	t.Helper()
	db, err := InitDB(filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatalf("InitDB: %v", err)
	}
	t.Cleanup(func() { db.Close() })
	return db
}

func seedUser(t *testing.T, db *DB, id string) {
	t.Helper()
	// Subscriptions carry a foreign key onto users, and foreign keys are on.
	if err := db.UpsertUser(&model.User{
		ID:    id,
		Email: id + "@example.test",
		Name:  "Test",
	}); err != nil {
		t.Fatalf("seed user: %v", err)
	}
}

func hasColumn(t *testing.T, db *DB, table, column string) bool {
	t.Helper()
	rows, err := db.conn.Query("PRAGMA table_info(" + table + ");")
	if err != nil {
		t.Fatalf("table_info: %v", err)
	}
	defer rows.Close()
	for rows.Next() {
		var cid int
		var name, colType string
		var notNull, pk int
		var dflt sql.NullString
		if err := rows.Scan(&cid, &name, &colType, &notNull, &dflt, &pk); err != nil {
			t.Fatalf("scan: %v", err)
		}
		if name == column {
			return true
		}
	}
	return false
}
