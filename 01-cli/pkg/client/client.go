package client

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"

	"iot-forge-cli/pkg/config"
	"iot-forge-cli/pkg/dto"
)

// Client is the HTTP client for the IoT-forge API.
type Client struct {
	endpoint     string
	httpClient   *http.Client
	token        string
	refreshToken string
	apiToken     string
}

// ClientOption configures a Client.
type ClientOption func(*Client)

// WithToken sets the access token for authenticated requests.
func WithToken(token string) ClientOption {
	return func(c *Client) { c.token = token }
}

// WithAPIToken sets the API token for headless/agent mode.
func WithAPIToken(token string) ClientOption {
	return func(c *Client) { c.apiToken = token }
}

// WithRefreshToken sets the refresh token for auto-refresh.
func WithRefreshToken(token string) ClientOption {
	return func(c *Client) { c.refreshToken = token }
}

// New creates a new API client.
func New(endpoint string, opts ...ClientOption) *Client {
	c := &Client{
		endpoint:   endpoint,
		httpClient: &http.Client{Timeout: 30 * time.Second},
	}
	for _, opt := range opts {
		opt(c)
	}
	return c
}

// NewClientFromConfig creates a client from a Config struct.
func NewClientFromConfig(cfg *config.Config) *Client {
	opts := []ClientOption{}
	if cfg.APIToken != "" {
		opts = append(opts, WithAPIToken(cfg.APIToken))
	} else if cfg.AccessToken != "" {
		opts = append(opts, WithToken(cfg.AccessToken))
		opts = append(opts, WithRefreshToken(cfg.RefreshToken))
	}
	return New(cfg.Endpoint, opts...)
}

func (c *Client) doRequest(ctx context.Context, method, path string, body, result any) error {
	var reqBody io.Reader
	if body != nil {
		data, err := json.Marshal(body)
		if err != nil {
			return fmt.Errorf("marshal request body: %w", err)
		}
		reqBody = bytes.NewReader(data)
	}

	url := c.endpoint + path
	req, err := http.NewRequestWithContext(ctx, method, url, reqBody)
	if err != nil {
		return fmt.Errorf("create request: %w", err)
	}

	if c.apiToken != "" {
		req.Header.Set("Authorization", "Bearer "+c.apiToken)
	} else if c.token != "" {
		req.Header.Set("Authorization", "Bearer "+c.token)
	}

	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	req.Header.Set("Accept", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("http request: %w", err)
	}
	defer resp.Body.Close()

	respData, err := io.ReadAll(resp.Body)
	if err != nil {
		return fmt.Errorf("read response body: %w", err)
	}

	if resp.StatusCode >= 400 {
		var errResp dto.BaseResponse
		if json.Unmarshal(respData, &errResp) == nil && errResp.ErrorCode != "" {
			return &APIError{
				StatusCode: resp.StatusCode,
				ErrorCode:  errResp.ErrorCode,
				Message:    errResp.ErrorMsg,
			}
		}
		return fmt.Errorf("http %d: %s", resp.StatusCode, string(respData))
	}

	var wrapped dto.BaseResponse
	if err := json.Unmarshal(respData, &wrapped); err != nil {
		return fmt.Errorf("parse response: %w", err)
	}

	if wrapped.ErrorCode != "" && wrapped.ErrorCode != "00000" {
		return &APIError{
			StatusCode: resp.StatusCode,
			ErrorCode:  wrapped.ErrorCode,
			Message:    wrapped.ErrorMsg,
		}
	}

	if result != nil && len(wrapped.Body) > 0 {
		if err := json.Unmarshal(wrapped.Body, result); err != nil {
			return fmt.Errorf("parse response body: %w", err)
		}
	}

	return nil
}

// unmarshalRaw is a helper to unmarshal json.RawMessage into a typed target.
func unmarshalRaw(raw json.RawMessage, target any) error {
	return json.Unmarshal(raw, target)
}

// APIError represents a structured API error.
type APIError struct {
	StatusCode int
	ErrorCode  string
	Message    string
}

func (e *APIError) Error() string {
	if e.ErrorCode != "" {
		return fmt.Sprintf("[%s] %s", e.ErrorCode, e.Message)
	}
	return fmt.Sprintf("http %d: %s", e.StatusCode, e.Message)
}
