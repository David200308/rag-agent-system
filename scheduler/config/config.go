package config

import (
	"os"
	"strings"
)

type Config struct {
	Port              string
	TemporalHostPort  string // e.g. "temporal:7233"
	TemporalNamespace string // default "default"
	BackendURL        string
	ServiceKey        string
	ValidateURL       string
}

func Load() *Config {
	backendURL := getEnv("BACKEND_URL", "http://localhost:8081")
	return &Config{
		Port:              getEnv("PORT", "8082"),
		TemporalHostPort:  getEnv("TEMPORAL_HOST_PORT", "localhost:7233"),
		TemporalNamespace: getEnv("TEMPORAL_NAMESPACE", "default"),
		BackendURL:        backendURL,
		ServiceKey:        getSecret("SCHEDULER_SERVICE_KEY", "scheduler-secret-key"),
		ValidateURL:       backendURL + "/api/v1/auth/validate",
	}
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

// getSecret supports Docker secrets: if {KEY}_FILE is set, reads the value from that file.
func getSecret(key, fallback string) string {
	if path := os.Getenv(key + "_FILE"); path != "" {
		data, err := os.ReadFile(path)
		if err == nil {
			return strings.TrimSpace(string(data))
		}
	}
	return getEnv(key, fallback)
}
