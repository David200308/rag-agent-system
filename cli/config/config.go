package config

import (
	"encoding/json"
	"os"
	"path/filepath"
)

const defaultBaseURL = "http://localhost:8081"

type file struct {
	BaseURL string  `json:"base_url"`
	Token   *string `json:"token"`
	Email   *string `json:"email"`
}

func configPath() string {
	home, _ := os.UserHomeDir()
	return filepath.Join(home, ".config", "agent-system", "config.json")
}

func load() file {
	data, err := os.ReadFile(configPath())
	if err != nil {
		return file{BaseURL: defaultBaseURL}
	}
	var f file
	if err := json.Unmarshal(data, &f); err != nil {
		return file{BaseURL: defaultBaseURL}
	}
	if f.BaseURL == "" {
		f.BaseURL = defaultBaseURL
	}
	return f
}

func save(f file) error {
	p := configPath()
	if err := os.MkdirAll(filepath.Dir(p), 0755); err != nil {
		return err
	}
	data, _ := json.MarshalIndent(f, "", "  ")
	return os.WriteFile(p, data, 0600)
}

func BaseURL() string {
	return load().BaseURL
}

func Token() string {
	if t := load().Token; t != nil {
		return *t
	}
	return ""
}

func Email() string {
	if e := load().Email; e != nil {
		return *e
	}
	return ""
}

func SetBaseURL(u string) error {
	f := load()
	f.BaseURL = u
	return save(f)
}

func SetToken(t string) error {
	f := load()
	f.Token = &t
	return save(f)
}

func SetEmail(e string) error {
	f := load()
	f.Email = &e
	return save(f)
}

func ClearAuth() error {
	f := load()
	f.Token = nil
	f.Email = nil
	return save(f)
}
