package config

import (
	"encoding/json"
	"os"
	"path/filepath"
)

const defaultURL = "http://localhost:8888"

type file struct {
	URL   string  `json:"url"`
	KeyID string  `json:"key_id"`
	Email *string `json:"email"`
}

func configPath() string {
	home, _ := os.UserHomeDir()
	return filepath.Join(home, ".config", "agent-system", "config.json")
}

func load() file {
	data, err := os.ReadFile(configPath())
	if err != nil {
		return file{URL: defaultURL}
	}
	var f file
	if err := json.Unmarshal(data, &f); err != nil {
		return file{URL: defaultURL}
	}
	if f.URL == "" {
		f.URL = defaultURL
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

func URL() string {
	return load().URL
}

func KeyID() string {
	return load().KeyID
}

func Email() string {
	if e := load().Email; e != nil {
		return *e
	}
	return ""
}

func SetURL(u string) error {
	f := load()
	f.URL = u
	return save(f)
}

func SetKeyID(id string) error {
	f := load()
	f.KeyID = id
	return save(f)
}

func SetEmail(e string) error {
	f := load()
	f.Email = &e
	return save(f)
}

func ClearAuth() error {
	f := load()
	f.Email = nil
	return save(f)
}
