package config

import "os"

type Config struct {
	BackendURL  string
	InternalKey string // AUTH_GATEWAY_KEY on backend side, INTERNAL_KEY here
	KeysFile    string
	Port        string
}

func Load() Config {
	return Config{
		BackendURL:  env("BACKEND_URL", "http://localhost:8081"),
		InternalKey: env("INTERNAL_KEY", ""),
		KeysFile:    env("KEYS_FILE", "keys.json"),
		Port:        env("PORT", "8888"),
	}
}

func env(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}
