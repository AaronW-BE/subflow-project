// Package config resolves server settings from a file, the environment and
// built-in defaults.
package config

import (
	"errors"
	"fmt"
	"io"
	"io/fs"
	"os"
	"strconv"
	"strings"

	"github.com/pelletier/go-toml/v2"
)

// DefaultPath is looked for in the working directory when no path is given.
// Its absence is not an error — the environment alone is a supported setup.
const DefaultPath = "subflow.config.toml"

// PathEnvVar names the file, for setups that cannot pass a flag.
const PathEnvVar = "SUBFLOW_CONFIG"

// Config is the full set of server settings.
//
// TOML rather than JSON, for two reasons that both matter for a file a human
// edits by hand and that holds secrets. It has comments, so the template can
// explain what each key does instead of smuggling the explanation into the
// value. And its strings are unambiguous: in YAML an admin_token of `no` or
// `0123` would be decoded as a bool or a number, whereas TOML requires the
// quotes. go-toml is already compiled into this binary via gin, so it costs
// nothing the server was not already carrying.
//
// Secrets live here, which is why LoadedFrom is reported and the values never
// are: anything that prints a Config would otherwise put the JWT secret and the
// admin token into a log.
type Config struct {
	Port   string `toml:"port"`
	DBPath string `toml:"db_path"`

	// JWTSecret signs session tokens. Whoever holds it can forge a login for
	// any user. Empty means a random per-boot value is generated instead.
	JWTSecret string `toml:"jwt_secret"`

	// AdminToken guards the /admin API. Empty means one is generated and
	// printed at startup.
	AdminToken string `toml:"admin_token"`

	// ExchangeRateAPIKey selects the keyed FX endpoint. Empty uses the open,
	// keyless one, which requires attribution wherever the rates are shown.
	ExchangeRateAPIKey string `toml:"exchange_rate_api_key"`

	// LoadedFrom is the config file actually read, or "" when none was.
	LoadedFrom string `toml:"-"`
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

// applyFile overlays a TOML file onto cfg, leaving absent keys untouched.
func applyFile(cfg *Config, path string) error {
	f, err := os.Open(path)
	if err != nil {
		return err
	}
	defer f.Close()

	// A config file is small; a huge one is a mistake or an attack, and
	// decoding it unbounded is how a server runs out of memory at startup.
	//
	// Unknown keys are rejected rather than ignored: a typo in a secret's name
	// otherwise looks exactly like a working config until something fails to
	// authenticate.
	dec := toml.NewDecoder(io.LimitReader(f, 1<<20)).DisallowUnknownFields()

	if err := dec.Decode(cfg); err != nil {
		var strict *toml.StrictMissingError
		if errors.As(err, &strict) {
			// StrictMissingError.String() is a multi-line excerpt of the file
			// with the offending lines underlined. That is good in a terminal
			// and poor in a log, where one event should be one line, so the
			// key names and line numbers are pulled out instead.
			return fmt.Errorf("%s: unknown key(s): %s", path, describeUnknownKeys(strict))
		}
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

// describeUnknownKeys renders rejected keys as "name (line N)", comma separated.
func describeUnknownKeys(strict *toml.StrictMissingError) string {
	described := make([]string, 0, len(strict.Errors))
	for i := range strict.Errors {
		decodeErr := &strict.Errors[i]
		name := strings.Join(decodeErr.Key(), ".")
		if name == "" {
			name = "<unnamed>"
		}
		if row, _ := decodeErr.Position(); row > 0 {
			described = append(described, fmt.Sprintf("%s (line %d)", name, row))
			continue
		}
		described = append(described, name)
	}
	return strings.Join(described, ", ")
}
