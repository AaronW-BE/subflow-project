package repository

import (
	"database/sql"
	"time"
)

// migrateRates creates the one-row cache holding the last good FX quote.
//
// Without it a restart during a provider outage would serve the compile-time
// fallback table, which is precisely the stale hand-written data the live feed
// exists to replace. Surviving a restart is the whole point of persisting it.
func (db *DB) migrateRates() error {
	_, err := db.conn.Exec(`CREATE TABLE IF NOT EXISTS rate_snapshot (
		id INTEGER PRIMARY KEY CHECK (id = 1),
		payload TEXT NOT NULL,
		fetched_at DATETIME NOT NULL
	);`)
	return err
}

// SaveRateSnapshot replaces the cached quote.
func (db *DB) SaveRateSnapshot(payload string) error {
	db.mu.Lock()
	defer db.mu.Unlock()

	_, err := db.conn.Exec(
		`INSERT INTO rate_snapshot (id, payload, fetched_at) VALUES (1, ?, ?)
		 ON CONFLICT(id) DO UPDATE SET payload = excluded.payload, fetched_at = excluded.fetched_at`,
		payload, time.Now().UTC(),
	)
	return err
}

// LoadRateSnapshot returns the cached quote, or ok=false when none was stored.
func (db *DB) LoadRateSnapshot() (payload string, fetchedAt time.Time, ok bool, err error) {
	db.mu.RLock()
	defer db.mu.RUnlock()

	row := db.conn.QueryRow(`SELECT payload, fetched_at FROM rate_snapshot WHERE id = 1`)
	switch err = row.Scan(&payload, &fetchedAt); {
	case err == sql.ErrNoRows:
		return "", time.Time{}, false, nil
	case err != nil:
		return "", time.Time{}, false, err
	}
	return payload, fetchedAt, true, nil
}
