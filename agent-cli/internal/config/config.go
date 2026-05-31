package config

import (
	"encoding/json"
	"os"
	"path/filepath"
)

type Config struct {
	Server string `json:"server"`
	Token  string `json:"token"`
	Email  string `json:"email"`
}

func dir() (string, error) {
	home, err := os.UserHomeDir()
	if err != nil {
		return "", err
	}
	return filepath.Join(home, ".agent-cli"), nil
}

func path() (string, error) {
	d, err := dir()
	if err != nil {
		return "", err
	}
	return filepath.Join(d, "config.json"), nil
}

func Load() (*Config, error) {
	p, err := path()
	if err != nil {
		return defaults(), nil
	}
	data, err := os.ReadFile(p)
	if err != nil {
		return defaults(), nil
	}
	var cfg Config
	if err := json.Unmarshal(data, &cfg); err != nil {
		return defaults(), nil
	}
	if cfg.Server == "" {
		cfg.Server = "http://localhost:8080"
	}
	return &cfg, nil
}

func (cfg *Config) Save() error {
	return Save(cfg)
}

func Save(cfg *Config) error {
	p, err := path()
	if err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(p), 0700); err != nil {
		return err
	}
	data, err := json.MarshalIndent(cfg, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(p, data, 0600)
}

func defaults() *Config {
	return &Config{Server: "http://localhost:8080"}
}
