package client

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strconv"
	"time"

	"agent-cli/internal/config"
	"agent-cli/internal/identity"
)

// Version is injected by cmd/root.go to avoid an import cycle.
var Version = "1.0.0"

type Client struct {
	BaseURL string
	Token   string
	Email   string
	http    *http.Client
}

func New(cfg *config.Config) *Client {
	return &Client{
		BaseURL: cfg.Server,
		Token:   cfg.Token,
		Email:   cfg.Email,
		http:    &http.Client{Timeout: 120 * time.Second},
	}
}

func (c *Client) newRequest(method, path string, body any) (*http.Request, error) {
	var r io.Reader
	if body != nil {
		b, err := json.Marshal(body)
		if err != nil {
			return nil, err
		}
		r = bytes.NewReader(b)
	}

	req, err := http.NewRequest(method, c.BaseURL+path, r)
	if err != nil {
		return nil, err
	}

	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	if c.Token != "" {
		req.Header.Set("Authorization", "Bearer "+c.Token)
	}

	// Sign the request if the user is logged in
	if c.Email != "" {
		sig, ts, err := identity.Sign(Version, method, path, c.Email)
		if err == nil {
			req.Header.Set("X-Cli-Signature", sig)
			req.Header.Set("X-Cli-Timestamp", strconv.FormatInt(ts, 10))
			req.Header.Set("X-Cli-Version", Version)
		}
	}

	return req, nil
}

// JSON performs a request and decodes the response into out (may be nil).
func (c *Client) JSON(method, path string, body any, out any) error {
	req, err := c.newRequest(method, path, body)
	if err != nil {
		return err
	}
	resp, err := c.http.Do(req)
	if err != nil {
		return fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()

	data, err := io.ReadAll(resp.Body)
	if err != nil {
		return err
	}
	if resp.StatusCode == 401 {
		return fmt.Errorf("unauthorized — run: agent-cli auth login")
	}
	if resp.StatusCode == 403 {
		return fmt.Errorf("forbidden: %s", string(data))
	}
	if resp.StatusCode >= 400 {
		return fmt.Errorf("HTTP %d: %s", resp.StatusCode, string(data))
	}
	if out != nil && len(data) > 0 {
		return json.Unmarshal(data, out)
	}
	return nil
}

// Raw returns the response body bytes directly.
func (c *Client) Raw(method, path string, body any) (int, []byte, error) {
	req, err := c.newRequest(method, path, body)
	if err != nil {
		return 0, nil, err
	}
	resp, err := c.http.Do(req)
	if err != nil {
		return 0, nil, fmt.Errorf("request failed: %w", err)
	}
	defer resp.Body.Close()
	data, err := io.ReadAll(resp.Body)
	return resp.StatusCode, data, err
}
