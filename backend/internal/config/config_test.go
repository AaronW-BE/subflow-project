package config

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// clearEnv unsets every variable Load consults, so a developer's own shell
// cannot change what these tests measure.
func clearEnv(t *testing.T) {
	t.Helper()
	for _, name := range []string{
		"PORT", "DB_PATH", "JWT_SECRET", "ADMIN_TOKEN", "EXCHANGE_RATE_API_KEY", PathEnvVar,
	} {
		t.Setenv(name, "")
		os.Unsetenv(name)
	}
}

func writeConfig(t *testing.T, body string) string {
	t.Helper()
	path := filepath.Join(t.TempDir(), "subflow.config.json")
	if err := os.WriteFile(path, []byte(body), 0o600); err != nil {
		t.Fatal(err)
	}
	return path
}

func TestDefaultsWhenNothingIsConfigured(t *testing.T) {
	clearEnv(t)
	// Point at a path that does not exist, without marking it explicit.
	t.Chdir(t.TempDir())

	cfg, err := Load("")
	if err != nil {
		t.Fatalf("a server with no config at all must still start: %v", err)
	}
	if cfg.Port != "8085" || cfg.DBPath != "subflow.db" {
		t.Errorf("defaults = %s / %s, want 8085 / subflow.db", cfg.Port, cfg.DBPath)
	}
	if cfg.LoadedFrom != "" {
		t.Errorf("LoadedFrom = %q, want empty when no file was read", cfg.LoadedFrom)
	}
}

func TestFileIsRead(t *testing.T) {
	clearEnv(t)
	path := writeConfig(t, `{
		"port": "9001",
		"db_path": "/data/subflow.db",
		"jwt_secret": "from-file",
		"admin_token": "admin-from-file",
		"exchange_rate_api_key": "key-from-file"
	}`)

	cfg, err := Load(path)
	if err != nil {
		t.Fatalf("Load failed: %v", err)
	}
	if cfg.Port != "9001" || cfg.DBPath != "/data/subflow.db" {
		t.Errorf("got %s / %s", cfg.Port, cfg.DBPath)
	}
	if cfg.JWTSecret != "from-file" || cfg.AdminToken != "admin-from-file" ||
		cfg.ExchangeRateAPIKey != "key-from-file" {
		t.Error("secrets were not read from the file")
	}
	if cfg.LoadedFrom != path {
		t.Errorf("LoadedFrom = %q, want %q", cfg.LoadedFrom, path)
	}
}

// The documented precedence. Environment wins so a container can override one
// setting without rewriting a file, and so a credential can be rotated without
// editing anything on disk.
func TestEnvironmentOverridesFile(t *testing.T) {
	clearEnv(t)
	path := writeConfig(t, `{"port":"9001","jwt_secret":"from-file","admin_token":"file-admin"}`)

	t.Setenv("PORT", "7777")
	t.Setenv("JWT_SECRET", "from-env")

	cfg, err := Load(path)
	if err != nil {
		t.Fatalf("Load failed: %v", err)
	}
	if cfg.Port != "7777" {
		t.Errorf("PORT = %s, want the environment to win", cfg.Port)
	}
	if cfg.JWTSecret != "from-env" {
		t.Errorf("JWT_SECRET = %q, want the environment to win", cfg.JWTSecret)
	}
	// Settings the environment does not mention keep their file values.
	if cfg.AdminToken != "file-admin" {
		t.Errorf("AdminToken = %q, want the file value to survive", cfg.AdminToken)
	}
}

// An empty environment variable is not an override. Otherwise a shell that
// exports PORT= would silently blank a configured port.
func TestEmptyEnvDoesNotOverride(t *testing.T) {
	clearEnv(t)
	path := writeConfig(t, `{"port":"9001"}`)
	t.Setenv("PORT", "")

	cfg, err := Load(path)
	if err != nil {
		t.Fatalf("Load failed: %v", err)
	}
	if cfg.Port != "9001" {
		t.Errorf("PORT = %s, want the file value to survive an empty env var", cfg.Port)
	}
}

// A file named explicitly and missing is an error; a file merely looked for is
// not. Silently ignoring --config=typo.json would start a server with none of
// the settings the operator believed they had passed.
func TestMissingExplicitFileIsAnError(t *testing.T) {
	clearEnv(t)
	missing := filepath.Join(t.TempDir(), "nope.json")

	if _, err := Load(missing); err == nil {
		t.Error("expected an error for an explicitly named file that does not exist")
	}

	t.Setenv(PathEnvVar, missing)
	if _, err := Load(""); err == nil {
		t.Errorf("expected an error when %s names a missing file", PathEnvVar)
	}
}

// A misspelled key is the dangerous case: it looks like a working config right
// up until something fails to authenticate.
func TestUnknownKeysAreRejected(t *testing.T) {
	clearEnv(t)
	path := writeConfig(t, `{"jwt_secrets":"typo"}`)

	_, err := Load(path)
	if err == nil {
		t.Fatal("expected an unknown key to be rejected")
	}
	if !strings.Contains(err.Error(), "jwt_secrets") {
		t.Errorf("the error should name the offending key, got: %v", err)
	}
}

func TestInvalidPortIsRejected(t *testing.T) {
	clearEnv(t)
	path := writeConfig(t, `{"port":"http"}`)

	if _, err := Load(path); err == nil {
		t.Error("expected a non-numeric port to be rejected at startup")
	}
}

// Describe feeds the startup log. Secrets must never reach it.
func TestDescribeHidesSecretValues(t *testing.T) {
	cfg := Config{
		Port: "8085", DBPath: "subflow.db",
		JWTSecret:          "super-secret-signing-key",
		AdminToken:         "super-secret-admin-token",
		ExchangeRateAPIKey: "super-secret-api-key",
		LoadedFrom:         "subflow.config.json",
	}
	got := cfg.Describe()

	for _, secret := range []string{cfg.JWTSecret, cfg.AdminToken, cfg.ExchangeRateAPIKey} {
		if strings.Contains(got, secret) {
			t.Errorf("Describe leaked a secret value: %s", got)
		}
	}
	if !strings.Contains(got, "jwt_secret set") {
		t.Errorf("Describe should report presence, got: %s", got)
	}
}

func TestDescribeReportsUnsetSecrets(t *testing.T) {
	got := Defaults().Describe()
	if !strings.Contains(got, "jwt_secret unset") ||
		!strings.Contains(got, "environment and defaults only") {
		t.Errorf("unexpected description: %s", got)
	}
}
