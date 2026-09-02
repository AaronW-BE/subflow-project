// Package config resolves server settings from a file, the environment and
// built-in defaults.
package config

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"os"
	"strconv"
)

// DefaultPath is looked for in the working directory when no path is given.
// Its absence is not an error — the environment alone is a supported setup.
const DefaultPath = "subflow.config.json"

// PathEnvVar names the file, for setups that cannot pass a flag.
const PathEnvVar = "SUBFLOW_CONFIG"

// Config is the full set of server settings.
//
// Secrets live here, which is why LoadedFrom is reported and the values never
// are: anything that prints a Config would otherwise put the JWT secret and the
// admin token into a log.
type Config struct {
	Port   string `json:"port"`
	DBPath string `json:"db_path"`

	// JWTSecret signs session tokens. Whoever holds it can forge a login for
	// any user. Empty means a random per-boot value is generated instead.
	JWTSecret string `json:"jwt_secret"`

	// AdminToken guards the /admin API. Empty means one is generated and
	// printed at startup.
	AdminToken string `json:"admin_token"`

	// ExchangeRateAPIKey selects the keyed FX endpoint. Empty uses the open,
	// keyless one, which requires attribution wherever the rates are shown.
	ExchangeRateAPIKey string `json:"exchange_rate_api_key"`

	// LoadedFrom is the config file actually read, or "" when none was.
	LoadedFrom string `json:"-"`
}

// Defaults are what an unconfigured server runs on.
func Defaults() Config {
	return Config{Port: "8085", DBPath: "subflow.db"}
}

// Load resolves settings with the environment taking precedence over the file,
// and the file over the defaults.
//
// Environment wins deliberately. A container or CI job overrides one setting
// without rewriting a file it may not be able to write, and it means an
// emergency credential rotation does not depend on editing anything on disk.
//
// path may be empty, in which case SUBFLOW_CONFIG is consulted and then
// DefaultPath. A file named explicitly must exist; a file merely looked for
// need not.
func Load(path string) (Config, error) {
	cfg := Defaults()

	explicit := path != ""
	if !explicit {
		path = os.Getenv(PathEnvVar)
		explicit = path != ""
	}
	if !explicit {
		path = DefaultPath
	}

	switch err := applyFile(&cfg, path); {
	case err == nil:
		cfg.LoadedFrom = path
	case errors.Is(err, fs.ErrNotExist) && !explicit:
		// No file, and none was asked for. Environment and defaults it is.
	default:
		return cfg, err
	}

	applyEnv(&cfg)

	if _, err := strconv.Atoi(cfg.Port); err != nil {
		return cfg, fmt.Errorf("port %q is not a number", cfg.Port)
	}
	if cfg.DBPath == "" {
		return cfg, errors.New("db_path cannot be empty")
	}
	return cfg, nil
}

// applyFile overlays a JSON file onto cfg, leaving absent keys untouched.
func applyFile(cfg *Config, path string) error {
	f, err := os.Open(path)
	if err != nil {
		return err
	}
	defer f.Close()

	// A config file is small; a huge one is a mistake or an attack, and
	// decoding it unbounded is how a server runs out of memory at startup.
	dec := json.NewDecoder(io.LimitReader(f, 1<<20))
	// Reject unknown keys rather than ignoring them. A typo in a secret's name
	// otherwise looks exactly like a working config until something fails to
	// authenticate.
	dec.DisallowUnknownFields()

	if err := dec.Decode(cfg); err != nil {
		return fmt.Errorf("%s: %w", path, err)
	}
	return nil
}

// applyEnv overlays environment variables, ignoring the ones that are unset.
func applyEnv(cfg *Config) {
	for _, binding := range []struct {
		name  string
		field *string
	}{
		{"PORT", &cfg.Port},
		{"DB_PATH", &cfg.DBPath},
		{"JWT_SECRET", &cfg.JWTSecret},
		{"ADMIN_TOKEN", &cfg.AdminToken},
		{"EXCHANGE_RATE_API_KEY", &cfg.ExchangeRateAPIKey},
	} {
		if v, ok := os.LookupEnv(binding.name); ok && v != "" {
			*binding.field = v
		}
	}
}

// Describe summarises the configuration for the startup log.
//
// It reports only whether each secret is set, never its value. The whole point
// of moving these out of the source was to stop them being readable, and a
// startup banner that echoes them undoes that.
func (c Config) Describe() string {
	source := "environment and defaults only"
	if c.LoadedFrom != "" {
		source = c.LoadedFrom + ", overlaid by the environment"
	}
	return fmt.Sprintf(
		"config: %s | port %s | db %s | jwt_secret %s | admin_token %s | exchange_rate_api_key %s",
		source, c.Port, c.DBPath,
		presence(c.JWTSecret), presence(c.AdminToken), presence(c.ExchangeRateAPIKey),
	)
}

func presence(v string) string {
	if v == "" {
		return "unset"
	}
	return "set"
}
