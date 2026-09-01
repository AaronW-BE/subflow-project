package repository

import (
	"database/sql"
	"subflow/backend/internal/model"
	"time"
)

// migratePurchases creates the purchase ledger. Called from InitDB.
func (db *DB) migratePurchases() error {
	queries := []string{
		`CREATE TABLE IF NOT EXISTS purchases (
			purchase_token TEXT PRIMARY KEY,
			user_id TEXT,
			product_id TEXT,
			order_id TEXT,
			package_name TEXT,
			pro_tier TEXT,
			state TEXT,
			reported_at DATETIME,
			verified_at DATETIME,
			FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
		);`,
		`CREATE INDEX IF NOT EXISTS idx_purchases_user ON purchases(user_id);`,
		`CREATE INDEX IF NOT EXISTS idx_purchases_reported ON purchases(reported_at);`,
	}
	for _, q := range queries {
		if _, err := db.conn.Exec(q); err != nil {
			return err
		}
	}
	return nil
}

// UpsertPurchase records a purchase token reported by a client.
//
// The purchase token is the primary key, so a client replaying the same
// purchase on every launch updates the row instead of inflating revenue.
func (db *DB) UpsertPurchase(p *model.Purchase) error {
	db.mu.Lock()
	defer db.mu.Unlock()

	query := `INSERT INTO purchases
		(purchase_token, user_id, product_id, order_id, package_name, pro_tier, state, reported_at, verified_at)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
		ON CONFLICT(purchase_token) DO UPDATE SET
			user_id=excluded.user_id,
			order_id=excluded.order_id,
			pro_tier=excluded.pro_tier,
			state=excluded.state,
			verified_at=excluded.verified_at;`

	_, err := db.conn.Exec(
		query,
		p.PurchaseToken, p.UserID, p.ProductID, p.OrderID,
		p.PackageName, string(p.ProTier), p.State, p.ReportedAt, p.VerifiedAt,
	)
	return err
}

func (db *DB) ListPurchases(limit, offset int) ([]model.Purchase, error) {
	db.mu.RLock()
	defer db.mu.RUnlock()

	rows, err := db.conn.Query(
		`SELECT purchase_token, user_id, product_id, order_id, package_name, pro_tier, state, reported_at, verified_at
		 FROM purchases ORDER BY reported_at DESC LIMIT ? OFFSET ?`,
		limit, offset,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	result := make([]model.Purchase, 0, limit)
	for rows.Next() {
		var p model.Purchase
		var tier string
		var verifiedAt sql.NullTime
		if err := rows.Scan(
			&p.PurchaseToken, &p.UserID, &p.ProductID, &p.OrderID,
			&p.PackageName, &tier, &p.State, &p.ReportedAt, &verifiedAt,
		); err != nil {
			return nil, err
		}
		p.ProTier = model.ProTier(tier)
		if verifiedAt.Valid {
			p.VerifiedAt = &verifiedAt.Time
		}
		result = append(result, p)
	}
	return result, rows.Err()
}

// CountPurchasesByTier powers the revenue figures on the admin dashboard.
func (db *DB) CountPurchasesByTier() (map[string]int, error) {
	db.mu.RLock()
	defer db.mu.RUnlock()

	rows, err := db.conn.Query(
		`SELECT pro_tier, COUNT(*) FROM purchases WHERE state = ? GROUP BY pro_tier`,
		model.PurchaseStatePurchased,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	counts := map[string]int{}
	for rows.Next() {
		var tier string
		var n int
		if err := rows.Scan(&tier, &n); err != nil {
			return nil, err
		}
		counts[tier] = n
	}
	return counts, rows.Err()
}

// PurchasesSince counts purchases reported after a cutoff, for trend charts.
func (db *DB) PurchasesSince(cutoff time.Time) (int, error) {
	db.mu.RLock()
	defer db.mu.RUnlock()

	var n int
	err := db.conn.QueryRow(
		`SELECT COUNT(*) FROM purchases WHERE state = ? AND reported_at >= ?`,
		model.PurchaseStatePurchased, cutoff,
	).Scan(&n)
	return n, err
}
