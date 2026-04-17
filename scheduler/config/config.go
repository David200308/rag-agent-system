package config

import (
	"os"
	"strings"
)

type Config struct {
	Port        string
	DSNI        string
	BackendURL  string
	ServiceKey  string
	ValidateURL string
}

func Load() *Config {
	mysqlHost := getEnv("MYSQL_HOST", "localhost")
	mysqlPort := getEnv("MYSQL_PORT", "3306")
	mysqlDB   := getEnv("MYSQL_DB", "ragagent")
	mysqlUser := getEnv("MYSQL_USER", "ragagent")
	mysqlPass := getSecret("MYSQL_PASSWORD", "ragagent")

	dsn := mysqlUser + ":" + mysqlPass +
		"@tcp(" + mysqlHost + ":" + mysqlPort + ")/" + mysqlDB +
		"?parseTime=true&loc=UTC"

	backendURL := getEnv("BACKEND_URL", "http://localhost:8081")

	return &Config{
		Port:        getEnv("PORT", "8082"),
		DSNI:        dsn,
		BackendURL:  backendURL,
		ServiceKey:  getSecret("SCHEDULER_SERVICE_KEY", "scheduler-secret-key"),
		ValidateURL: backendURL + "/api/v1/auth/validate",
	}
}

// getEnv returns the env var value or the fallback.
func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

// getSecret supports Docker secrets: if {key}_FILE is set, it reads the value
// from that file path. Falls back to the plain env var, then to the default.
func getSecret(key, fallback string) string {
	if path := os.Getenv(key + "_FILE"); path != "" {
		data, err := os.ReadFile(path)
		if err == nil {
			return strings.TrimSpace(string(data))
		}
	}
	return getEnv(key, fallback)
}
