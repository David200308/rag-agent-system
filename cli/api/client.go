package api

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"

	"cli/config"
)

var httpClient = &http.Client{Timeout: 30 * time.Second}

func do(method, path string, body any) (json.RawMessage, error) {
	var r io.Reader
	if body != nil {
		data, err := json.Marshal(body)
		if err != nil {
			return nil, err
		}
		r = bytes.NewReader(data)
	}

	req, err := http.NewRequest(method, config.BaseURL()+path, r)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	if tok := config.Token(); tok != "" {
		req.Header.Set("Authorization", "Bearer "+tok)
	}

	resp, err := httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("connection failed: %w", err)
	}
	defer resp.Body.Close()

	b, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}

	switch resp.StatusCode {
	case 401:
		return nil, fmt.Errorf("not authenticated — run: agent-system login <email>")
	case 204:
		return json.RawMessage("{}"), nil
	}
	if resp.StatusCode >= 400 {
		return nil, fmt.Errorf("HTTP %d: %s", resp.StatusCode, string(b))
	}
	if len(b) == 0 {
		return json.RawMessage("{}"), nil
	}
	return json.RawMessage(b), nil
}

func Get(path string) (json.RawMessage, error)            { return do("GET", path, nil) }
func Post(path string, body any) (json.RawMessage, error) { return do("POST", path, body) }
func Delete(path string) (json.RawMessage, error)         { return do("DELETE", path, nil) }
