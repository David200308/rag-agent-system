package config

import (
	"fmt"
	"os"
	"strings"
)

type Config struct {
	Port          string
	RedisAddr     string
	RedisPassword string
	DSN           string // MySQL connection string
	BackendURL    string
	ServiceKey    string
	ValidateURL   string
}

func Load() *Config {
	backendURL := getEnv("BACKEND_URL", "http://localhost:8081")
	dsn := fmt.Sprintf("%s:%s@tcp(%s:%s)/%s?parseTime=true",
		getEnv("MYSQL_USER", "ragagent"),
		getSecret("MYSQL_PASSWORD", "ragagent"),
		getEnv("MYSQL_HOST", "localhost"),
		getEnv("MYSQL_PORT", "3306"),
		getEnv("MYSQL_DB", "ragagent"),
	)
	return &Config{
		Port:          getEnv("PORT", "8082"),
		RedisAddr:     getEnv("REDIS_ADDR", "localhost:6379"),
		RedisPassword: getSecret("REDIS_PASSWORD", ""),
		DSN:           dsn,
		BackendURL:    backendURL,
		ServiceKey:    getSecret("SCHEDULER_SERVICE_KEY", "scheduler-secret-key"),
		ValidateURL:   backendURL + "/api/v1/auth/validate",
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
