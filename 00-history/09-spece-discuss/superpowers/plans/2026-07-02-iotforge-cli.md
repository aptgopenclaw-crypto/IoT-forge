# iotforge CLI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build standalone `iotforge` CLI for tenant administrators and AI agents to manage IoT-forge resources (device, telemetry, dispatch).

**Architecture:** Cobra CLI with a typed Go SDK layer (`pkg/client/`). CLI commands delegate to the SDK, which handles HTTP calls, auth, and token refresh. All authorization is server-side — CLI never makes access decisions.

**Tech Stack:** Go 1.22+, Cobra, Viper, `net/http`, `encoding/json`, `text/tabwriter`

## Global Constraints

- Go 1.22+ minimum version in `go.mod`
- Project root: `01-cli/` within the IoT-forge monorepo (independent module, not a subpackage)
- Module path: `github.com/taipei-iot/iot-forge-cli` (or local relative if preferred; use `iot-forge-cli`)
- No external dependencies beyond Cobra, Viper, and Go standard library
- Config file: `~/.iotforge/config.json`, permission 600 on write
- All API calls go through `pkg/client/` SDK layer
- `--output` flag supports `table`, `json`, `yaml` everywhere
- Every SDK method returns `(T, error)` — no `*T` pointer returns for zero-value types except slices/maps
- Token auto-refresh is handled transparently in `client.doRequest()`
- API paths match existing backend: `/v1/auth/devices`, `/v1/auth/telemetry`, `/v1/auth/dispatch`

---

### Task 1: Go module init + Cobra skeleton + Viper config

**Files:**
- Create: `01-cli/go.mod`
- Create: `01-cli/go.sum`
- Create: `01-cli/cmd/iotforge/main.go`
- Create: `01-cli/cmd/iotforge/root.go`
- Create: `01-cli/pkg/config/config.go`

**Interfaces:**
- Produces: module scaffold; `config.Load()` → `(*Config, error)`; `config.Save(cfg)` → `error`; `Config` struct with Endpoint, AccessToken, RefreshToken, TokenExpiry, APIToken, DefaultOutput fields; root Cobra command with `--endpoint`, `--output`, `--api-token` persistent flags

- [ ] **Step 1: Initialize Go module**

```bash
cd /home/kevin/workspaces/side-project/IoT-forge/01-cli
go mod init iot-forge-cli
```

- [ ] **Step 2: Add dependencies**

```bash
go get github.com/spf13/cobra@latest
go get github.com/spf13/viper@latest
go get github.com/spf13/pflag@latest
go mod tidy
```

- [ ] **Step 3: Create config package**

Write `01-cli/pkg/config/config.go`:

```go
package config

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
)

type TenantInfo struct {
	ID   string `json:"id"`
	Name string `json:"name"`
}

type Config struct {
	Endpoint      string     `json:"endpoint"`
	DefaultOutput string     `json:"default_output"`
	CurrentTenant *TenantInfo `json:"current_tenant,omitempty"`
	AccessToken   string     `json:"access_token,omitempty"`
	TokenExpiry   int64      `json:"token_expiry,omitempty"`
	RefreshToken  string     `json:"refresh_token,omitempty"`
	APIToken      string     `json:"api_token,omitempty"`
}

func ConfigDir() (string, error) {
	home, err := os.UserHomeDir()
	if err != nil {
		return "", fmt.Errorf("cannot find home dir: %w", err)
	}
	return filepath.Join(home, ".iotforge"), nil
}

func ConfigPath() (string, error) {
	dir, err := ConfigDir()
	if err != nil {
		return "", err
	}
	return filepath.Join(dir, "config.json"), nil
}

func Load() (*Config, error) {
	path, err := ConfigPath()
	if err != nil {
		return nil, err
	}
	data, err := os.ReadFile(path)
	if err != nil {
		if os.IsNotExist(err) {
			return &Config{Endpoint: "http://localhost:8080", DefaultOutput: "table"}, nil
		}
		return nil, fmt.Errorf("read config: %w", err)
	}
	var cfg Config
	if err := json.Unmarshal(data, &cfg); err != nil {
		return nil, fmt.Errorf("parse config: %w", err)
	}
	if cfg.Endpoint == "" {
		cfg.Endpoint = "http://localhost:8080"
	}
	if cfg.DefaultOutput == "" {
		cfg.DefaultOutput = "table"
	}
	return &cfg, nil
}

func Save(cfg *Config) error {
	dir, err := ConfigDir()
	if err != nil {
		return err
	}
	if err := os.MkdirAll(dir, 0700); err != nil {
		return fmt.Errorf("create config dir: %w", err)
	}
	path, err := ConfigPath()
	if err != nil {
		return err
	}
	data, err := json.MarshalIndent(cfg, "", "  ")
	if err != nil {
		return fmt.Errorf("marshal config: %w", err)
	}
	if err := os.WriteFile(path, data, 0600); err != nil {
		return fmt.Errorf("write config: %w", err)
	}
	return nil
}

func Clear() error {
	path, err := ConfigPath()
	if err != nil {
		return err
	}
	if err := os.Remove(path); err != nil && !os.IsNotExist(err) {
		return fmt.Errorf("remove config: %w", err)
	}
	return nil
}
```

- [ ] **Step 4: Create root Cobra command**

Write `01-cli/cmd/iotforge/root.go`:

```go
package main

import (
	"fmt"
	"os"

	"github.com/spf13/cobra"
	"github.com/spf13/viper"
	"iot-forge-cli/pkg/config"
)

var (
	cfgFile   string
	endpoint  string
	outputFmt string
	apiToken  string
)

var rootCmd = &cobra.Command{
	Use:   "iotforge",
	Short: "IoT-forge CLI — manage IoT resources from your terminal",
	Long: `iotforge is a CLI tool for managing IoT-forge platform resources.
It supports interactive login and AI agent API token modes.
All commands respect your RBAC permissions and tenant data scope.`,
	PersistentPreRunE: func(cmd *cobra.Command, args []string) error {
		// Skip config load for login/logout
		if cmd.Name() == "login" || cmd.Name() == "logout" {
			return nil
		}
		return nil
	},
}

func Execute() {
	if err := rootCmd.Execute(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

func init() {
	cobra.OnInitialize(initConfig)

	rootCmd.PersistentFlags().StringVar(&endpoint, "endpoint", "", "API server URL (env: IOTFORGE_ENDPOINT)")
	rootCmd.PersistentFlags().StringVarP(&outputFmt, "output", "o", "", "Output format: table, json, yaml (env: IOTFORGE_OUTPUT)")
	rootCmd.PersistentFlags().StringVar(&apiToken, "api-token", "", "API token for headless mode (env: IOTFORGE_API_TOKEN)")

	viper.BindPEnv("endpoint", "IOTFORGE_ENDPOINT")
	viper.BindPEnv("output", "IOTFORGE_OUTPUT")
	viper.BindPEnv("api_token", "IOTFORGE_API_TOKEN")

	viper.BindPFlag("endpoint", rootCmd.PersistentFlags().Lookup("endpoint"))
	viper.BindPFlag("output", rootCmd.PersistentFlags().Lookup("output"))
	viper.BindPFlag("api_token", rootCmd.PersistentFlags().Lookup("api-token"))
}

func initConfig() {
	cfg, err := config.Load()
	if err != nil {
		fmt.Fprintf(os.Stderr, "Warning: cannot load config: %v\n", err)
		cfg = &config.Config{Endpoint: "http://localhost:8080", DefaultOutput: "table"}
	}
	viper.SetDefault("endpoint", cfg.Endpoint)
	viper.SetDefault("output", cfg.DefaultOutput)
	viper.SetDefault("api_token", cfg.APIToken)
}

func resolveConfig() (*config.Config, error) {
	cfg, err := config.Load()
	if err != nil {
		return nil, err
	}
	if v := viper.GetString("endpoint"); v != "" {
		cfg.Endpoint = v
	}
	if v := viper.GetString("output"); v != "" {
		cfg.DefaultOutput = v
	}
	if v := viper.GetString("api_token"); v != "" {
		cfg.APIToken = v
	}
	return cfg, nil
}
```

- [ ] **Step 5: Create main.go entry point**

Write `01-cli/cmd/iotforge/main.go`:

```go
package main

func main() {
	Execute()
}
```

- [ ] **Step 6: Verify it compiles**

```bash
cd /home/kevin/workspaces/side-project/IoT-forge/01-cli
go build ./cmd/iotforge/
echo 'build ok'
```

Expected: binary `iotforge` created, no errors.

- [ ] **Step 7: Commit**

```bash
cd /home/kevin/workspaces/side-project/IoT-forge
git add 01-cli/go.mod 01-cli/go.sum 01-cli/cmd/iotforge/main.go 01-cli/cmd/iotforge/root.go 01-cli/pkg/config/config.go
git commit -m "feat(cli): scaffold Go module with Cobra skeleton and Viper config"

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```

---

### Task 2: Shared DTO definitions

**Files:**
- Create: `01-cli/pkg/dto/auth.go`
- Create: `01-cli/pkg/dto/device.go`
- Create: `01-cli/pkg/dto/telemetry.go`
- Create: `01-cli/pkg/dto/dispatch.go`
- Create: `01-cli/pkg/dto/common.go`

**Interfaces:**
- Produces: Shared request/response structs matching backend API JSON shapes; `BaseResponse[T]` generic wrapper used by all SDK methods
- Consumes: none (standalone definitions)

**Key: All JSON field names must match the backend's `@JsonProperty` / default Spring Boot serialization (`camelCase`).**

- [ ] **Step 1: Create common response wrapper + enums**

Write `01-cli/pkg/dto/common.go`:

```go
package dto

import "encoding/json"

// BaseResponse wraps all IoT-forge API responses.
type BaseResponse struct {
	ErrorCode string          `json:"errorCode"`
	ErrorMsg  string          `json:"errorMsg"`
	Timestamp int64           `json:"timestamp"`
	Body      json.RawMessage `json:"body"`
}

// PageResponse wraps paginated list results.
type PageResponse struct {
	Content       json.RawMessage `json:"content"`
	TotalElements int64           `json:"totalElements"`
	TotalPages    int             `json:"totalPages"`
	Page          int             `json:"page"`
	Size          int             `json:"size"`
}

type DeviceStatus string

const (
	DeviceStatusActive       DeviceStatus = "ACTIVE"
	DeviceStatusInactive     DeviceStatus = "INACTIVE"
	DeviceStatusDecommissioned DeviceStatus = "DECOMMISSIONED"
)

type DispatchStatus string

const (
	DispatchStatusPending   DispatchStatus = "PENDING"
	DispatchStatusApproved  DispatchStatus = "APPROVED"
	DispatchStatusInProgress DispatchStatus = "IN_PROGRESS"
	DispatchStatusCompleted DispatchStatus = "COMPLETED"
	DispatchStatusRejected  DispatchStatus = "REJECTED"
)

type DispatchPriority string

const (
	DispatchPriorityLow    DispatchPriority = "LOW"
	DispatchPriorityMedium DispatchPriority = "MEDIUM"
	DispatchPriorityHigh   DispatchPriority = "HIGH"
	DispatchPriorityUrgent DispatchPriority = "URGENT"
)
```

- [ ] **Step 2: Create auth DTOs**

Write `01-cli/pkg/dto/auth.go`:

```go
package dto

type LoginRequest struct {
	Username string `json:"username"`
	Password string `json:"password"`
}

type LoginResponse struct {
	TemporaryToken string `json:"temporaryToken"`
	ExpiresIn      int64  `json:"expiresIn"`
}

type TenantInfo struct {
	ID   string `json:"id"`
	Name string `json:"name"`
}

type SelectTenantRequest struct {
	TemporaryToken string `json:"temporaryToken"`
	TenantID       string `json:"tenantId"`
}

type SelectTenantResponse struct {
	AccessToken  string `json:"accessToken"`
	RefreshToken string `json:"refreshToken"`
	ExpiresIn    int64  `json:"expiresIn"`
}

type RefreshTokenRequest struct {
	RefreshToken string `json:"refreshToken"`
}

type RefreshTokenResponse struct {
	AccessToken string `json:"accessToken"`
	ExpiresIn   int64  `json:"expiresIn"`
}
```

- [ ] **Step 3: Create device DTOs**

Write `01-cli/pkg/dto/device.go`:

```go
package dto

type DeviceResponse struct {
	ID           int64        `json:"id"`
	Name         string       `json:"name"`
	DeviceType   string       `json:"deviceType"`
	Status       DeviceStatus `json:"status"`
	SerialNumber string       `json:"serialNumber,omitempty"`
	TenantID     string       `json:"tenantId"`
	ParentID     *int64       `json:"parentId,omitempty"`
	CreatedAt    string       `json:"createdAt"`
	UpdatedAt    string       `json:"updatedAt,omitempty"`
	Children     []DeviceResponse `json:"children,omitempty"`
}

type DeviceRequest struct {
	Name         string            `json:"name"`
	DeviceType   string            `json:"deviceType"`
	SerialNumber string            `json:"serialNumber,omitempty"`
	Status       DeviceStatus      `json:"status,omitempty"`
	ParentID     *int64            `json:"parentId,omitempty"`
	Properties   map[string]any    `json:"properties,omitempty"`
}

type DeviceStatsResponse struct {
	TotalByType   map[string]int64 `json:"totalByType"`
	TotalByStatus map[string]int64 `json:"totalByStatus"`
	OnlineRate    float64          `json:"onlineRate"`
	OpenDispatches int64           `json:"openDispatches"`
}
```

- [ ] **Step 4: Create telemetry DTOs**

Write `01-cli/pkg/dto/telemetry.go`:

```go
package dto

type TelemetryQueryRequest struct {
	DeviceID string `json:"-"`        // path parameter
	From     string `json:"from"`
	To       string `json:"to,omitempty"`
	Metric   string `json:"metric,omitempty"`
	Interval string `json:"interval,omitempty"`
}

type TelemetryReading struct {
	Timestamp string  `json:"timestamp"`
	Metric    string  `json:"metric"`
	Value     float64 `json:"value"`
	Unit      string  `json:"unit,omitempty"`
}

type TelemetryQueryResponse struct {
	DeviceID string             `json:"deviceId"`
	Readings []TelemetryReading `json:"readings"`
}
```

- [ ] **Step 5: Create dispatch DTOs**

Write `01-cli/pkg/dto/dispatch.go`:

```go
package dto

type DispatchResponse struct {
	ID          int64            `json:"id"`
	Title       string           `json:"title"`
	Description string           `json:"description,omitempty"`
	Status      DispatchStatus   `json:"status"`
	Priority    DispatchPriority `json:"priority"`
	AssignedTo  string           `json:"assignedTo,omitempty"`
	DeviceID    *int64           `json:"deviceId,omitempty"`
	TenantID    string           `json:"tenantId"`
	CreatedAt   string           `json:"createdAt"`
	UpdatedAt   string           `json:"updatedAt,omitempty"`
}

type DispatchRequest struct {
	Title       string           `json:"title,omitempty"`
	Description string           `json:"description,omitempty"`
	Status      DispatchStatus   `json:"status,omitempty"`
	Priority    DispatchPriority `json:"priority,omitempty"`
	AssignedTo  string           `json:"assignedTo,omitempty"`
	DeviceID    *int64           `json:"deviceId,omitempty"`
}
```

- [ ] **Step 6: Verify compilation**

```bash
cd /home/kevin/workspaces/side-project/IoT-forge/01-cli
go build ./pkg/dto/
echo 'dto build ok'
```

Expected: no errors.

- [ ] **Step 7: Commit**

```bash
cd /home/kevin/workspaces/side-project/IoT-forge
git add 01-cli/pkg/dto/
git commit -m "feat(cli): add shared DTO definitions for auth, device, telemetry, dispatch"

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```

---

### Task 3: HTTP client core (SDK base layer)

**Files:**
- Create: `01-cli/pkg/client/client.go`

**Interfaces:**
- Produces: `Client` struct with `doRequest(method, path, body, result)` method; auto token refresh on 401; `New(endpoint, opts)` constructor
- Consumes: `pkg/config/`, `pkg/dto/`

**`Client` public API:**
```go
type Client struct {}

type ClientOption func(*Client)

func WithToken(token string) ClientOption
func WithAPIToken(token string) ClientOption
func WithRefreshToken(token string) ClientOption

func New(endpoint string, opts ...ClientOption) *Client

// internal:
func (c *Client) doRequest(ctx context.Context, method, path string, body, result any) error
```

- [ ] **Step 1: Write the client package**

Write `01-cli/pkg/client/client.go`:

```go
package client

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"

	"iot-forge-cli/pkg/dto"
)

type Client struct {
	endpoint     string
	httpClient   *http.Client
	token        string
	refreshToken string
	apiToken     string
}

type ClientOption func(*Client)

func WithToken(token string) ClientOption {
	return func(c *Client) { c.token = token }
}

func WithAPIToken(token string) ClientOption {
	return func(c *Client) { c.apiToken = token }
}

func WithRefreshToken(token string) ClientOption {
	return func(c *Client) { c.refreshToken = token }
}

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

	// Auth header
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

	// Try to parse error response early
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

	// Parse wrapped response
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
```

- [ ] **Step 2: Verify compilation**

```bash
cd /home/kevin/workspaces/side-project/IoT-forge/01-cli
go build ./pkg/client/
echo 'client build ok'
```

- [ ] **Step 3: Commit**

```bash
cd /home/kevin/workspaces/side-project/IoT-forge
git add 01-cli/pkg/client/client.go
git commit -m "feat(cli): add SDK HTTP client core with auth header and error handling"

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```

---

### Task 4: Auth SDK methods + CLI login/logout/auth-status

**Files:**
- Create: `01-cli/pkg/client/auth.go`
- Create: `01-cli/cmd/iotforge/login.go`
- Create: `01-cli/cmd/iotforge/logout.go`
- Create: `01-cli/cmd/iotforge/auth_status.go`

**Interfaces:**
- Produces: SDK `Client.Login(ctx, username, password)` → `([]TenantInfo, temporaryToken, error)`; `Client.SelectTenant(ctx, tempToken, tenantID)` → `(accessToken, refreshToken, error)`; `Client.RefreshToken(ctx, refreshToken)` → `(newAccessToken, error)`; CLI `iotforge login`, `iotforge logout`, `iotforge auth status` commands
- Consumes: `pkg/client/client.go`, `pkg/dto/auth.go`, `pkg/config/config.go`

- [ ] **Step 1: Write auth SDK methods**

Write `01-cli/pkg/client/auth.go`:

```go
package client

import (
	"context"
	"iot-forge-cli/pkg/dto"
)

func (c *Client) Login(ctx context.Context, username, password string) (string, error) {
	req := dto.LoginRequest{Username: username, Password: password}
	var resp dto.LoginResponse
	if err := c.doRequest(ctx, "POST", "/v1/auth/login", req, &resp); err != nil {
		return "", err
	}
	return resp.TemporaryToken, nil
}

func (c *Client) ListTenants(ctx context.Context) ([]dto.TenantInfo, error) {
	var tenants []dto.TenantInfo
	if err := c.doRequest(ctx, "POST", "/v1/auth/tenants", nil, &tenants); err != nil {
		return nil, err
	}
	return tenants, nil
}

func (c *Client) SelectTenant(ctx context.Context, tempToken, tenantID string) (*dto.SelectTenantResponse, error) {
	req := dto.SelectTenantRequest{
		TemporaryToken: tempToken,
		TenantID:       tenantID,
	}
	var resp dto.SelectTenantResponse
	if err := c.doRequest(ctx, "POST", "/v1/auth/tenant/select", req, &resp); err != nil {
		return nil, err
	}
	return &resp, nil
}

func (c *Client) RefreshToken(ctx context.Context, refreshToken string) (*dto.RefreshTokenResponse, error) {
	req := dto.RefreshTokenRequest{RefreshToken: refreshToken}
	var resp dto.RefreshTokenResponse
	if err := c.doRequest(ctx, "POST", "/v1/auth/refresh", req, &resp); err != nil {
		return nil, err
	}
	return &resp, nil
}

func (c *Client) Logout(ctx context.Context) error {
	return c.doRequest(ctx, "POST", "/v1/auth/logout", nil, nil)
}
```

- [ ] **Step 2: Write login command**

Write `01-cli/cmd/iotforge/login.go`:

```go
package main

import (
	"context"
	"fmt"
	"os"
	"strconv"
	"strings"
	"syscall"

	"iot-forge-cli/pkg/client"
	"iot-forge-cli/pkg/config"

	"github.com/spf13/cobra"
	"golang.org/x/term"
)

var loginCmd = &cobra.Command{
	Use:   "login",
	Short: "Log in to IoT-forge",
	Long: `Authenticate with your IoT-forge account.
You will be prompted for username and password, then select a tenant.`,
	RunE: runLogin,
}

func init() {
	rootCmd.AddCommand(loginCmd)
}

func runLogin(cmd *cobra.Command, args []string) error {
	cfg, err := resolveConfig()
	if err != nil {
		return fmt.Errorf("config error: %w", err)
	}

	fmt.Print("Username: ")
	var username string
	if _, err := fmt.Scanln(&username); err != nil {
		return fmt.Errorf("read username: %w", err)
	}

	fmt.Print("Password: ")
	passwordBytes, err := term.ReadPassword(int(syscall.Stdin))
	if err != nil {
		return fmt.Errorf("read password: %w", err)
	}
	password := string(passwordBytes)
	fmt.Println()

	if username == "" || password == "" {
		return fmt.Errorf("username and password are required")
	}

	c := client.New(cfg.Endpoint)
	tempToken, err := c.Login(context.Background(), username, password)
	if err != nil {
		return fmt.Errorf("login failed: %w", err)
	}

	fmt.Println("✓ Authenticated")
	fmt.Println()

	c2 := client.New(cfg.Endpoint, client.WithToken(tempToken))
	tenants, err := c2.ListTenants(context.Background())
	if err != nil {
		return fmt.Errorf("list tenants failed: %w", err)
	}

	if len(tenants) == 0 {
		return fmt.Errorf("no tenants available for this account")
	}

	fmt.Println("Available tenants:")
	for i, t := range tenants {
		fmt.Printf("  %d. %s (%s)\n", i+1, t.Name, t.ID)
	}

	fmt.Print("Select tenant [1]: ")
	var selStr string
	if _, err := fmt.Scanln(&selStr); err != nil {
		selStr = "1"
	}
	sel := 1
	if v, err := strconv.Atoi(strings.TrimSpace(selStr)); err == nil && v >= 1 && v <= len(tenants) {
		sel = v
	}

	selectedTenant := tenants[sel-1]

	resp, err := c2.SelectTenant(context.Background(), tempToken, selectedTenant.ID)
	if err != nil {
		return fmt.Errorf("select tenant failed: %w", err)
	}

	cfg.Endpoint = cfg.Endpoint
	cfg.AccessToken = resp.AccessToken
	cfg.RefreshToken = resp.RefreshToken
	cfg.TokenExpiry = resp.ExpiresIn
	cfg.CurrentTenant = &config.TenantInfo{
		ID:   selectedTenant.ID,
		Name: selectedTenant.Name,
	}
	cfg.APIToken = ""

	if err := config.Save(cfg); err != nil {
		return fmt.Errorf("save config: %w", err)
	}

	fmt.Printf("\n✓ Logged in as %s @ %s (%s)\n", username, selectedTenant.Name, selectedTenant.ID)
	return nil
}
```

Note: `golang.org/x/term` is needed for `term.ReadPassword`. Add it:

```bash
cd /home/kevin/workspaces/side-project/IoT-forge/01-cli
go get golang.org/x/term@latest
go mod tidy
```

- [ ] **Step 3: Write logout command**

Write `01-cli/cmd/iotforge/logout.go`:

```go
package main

import (
	"context"
	"fmt"

	"iot-forge-cli/pkg/client"
	"iot-forge-cli/pkg/config"

	"github.com/spf13/cobra"
)

var logoutCmd = &cobra.Command{
	Use:   "logout",
	Short: "Log out and clear stored credentials",
	RunE:  runLogout,
}

func init() {
	rootCmd.AddCommand(logoutCmd)
}

func runLogout(cmd *cobra.Command, args []string) error {
	cfg, err := resolveConfig()
	if err != nil {
		// If config is unreadable, just clear it
		config.Clear()
		fmt.Println("✓ Logged out")
		return nil
	}

	if cfg.AccessToken != "" || cfg.APIToken != "" {
		c := client.New(cfg.Endpoint,
			client.WithToken(cfg.AccessToken),
			client.WithAPIToken(cfg.APIToken),
		)
		if err := c.Logout(context.Background()); err != nil {
			// Logout API failure is non-fatal; clear local state anyway
			fmt.Fprintf(os.Stderr, "Warning: server logout failed: %v\n", err)
		}
	}

	if err := config.Clear(); err != nil {
		return fmt.Errorf("clear config: %w", err)
	}

	fmt.Println("✓ Logged out")
	return nil
}
```

Need to add `"os"` import to logout.go.

- [ ] **Step 4: Write auth status command**

Write `01-cli/cmd/iotforge/auth_status.go`:

```go
package main

import (
	"fmt"
	"time"

	"iot-forge-cli/pkg/config"

	"github.com/spf13/cobra"
)

var authStatusCmd = &cobra.Command{
	Use:   "status",
	Short: "Show current authentication status",
	RunE:  runAuthStatus,
}

func init() {
	// Register under "iotforge auth status"
	authCmd := &cobra.Command{
		Use:   "auth",
		Short: "Authentication commands",
	}
	authCmd.AddCommand(authStatusCmd)
	rootCmd.AddCommand(authCmd)
}

func runAuthStatus(cmd *cobra.Command, args []string) error {
	cfg, err := config.Load()
	if err != nil {
		fmt.Println("Not logged in (config not found)")
		return nil
	}

	fmt.Printf("Endpoint:  %s\n", cfg.Endpoint)
	if cfg.CurrentTenant != nil {
		fmt.Printf("Tenant:    %s (%s)\n", cfg.CurrentTenant.Name, cfg.CurrentTenant.ID)
	} else {
		fmt.Println("Tenant:    (not selected)")
	}

	if cfg.AccessToken != "" {
		expiry := time.Unix(cfg.TokenExpiry, 0)
		remaining := time.Until(expiry)
		if remaining > 0 {
			fmt.Printf("Token:     valid (expires in %s)\n", remaining.Round(time.Second))
		} else {
			fmt.Println("Token:     expired")
		}
	} else if cfg.APIToken != "" {
		fmt.Println("Auth:      API token (headless mode)")
	} else {
		fmt.Println("Auth:      not authenticated")
	}
	return nil
}
```

- [ ] **Step 5: Verify compilation**

```bash
cd /home/kevin/workspaces/side-project/IoT-forge/01-cli
go build ./cmd/iotforge/
echo 'auth build ok'
```

- [ ] **Step 6: Commit**

```bash
cd /home/kevin/workspaces/side-project/IoT-forge
git add 01-cli/cmd/iotforge/login.go 01-cli/cmd/iotforge/logout.go 01-cli/cmd/iotforge/auth_status.go 01-cli/pkg/client/auth.go
git commit -m "feat(cli): implement auth SDK + login/logout/auth-status commands"

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```

---

### Task 5: Output formatting (table / JSON / YAML)

**Files:**
- Create: `01-cli/pkg/output/table.go`

**Interfaces:**
- Produces: `output.Print(cmd, data, opts)` where `opts.Format` is `table|json|yaml` and each data type knows its table columns; `output.WithFormat(fmt)` option
- Consumes: none

- [ ] **Step 1: Write table output**

Write `01-cli/pkg/output/table.go`:

```go
package output

import (
	"encoding/json"
	"fmt"
	"io"
	"os"
	"text/tabwriter"

	"sigs.k8s.io/yaml"
)

var DefaultWriter io.Writer = os.Stdout

type Format string

const (
	FormatTable Format = "table"
	FormatJSON  Format = "json"
	FormatYAML  Format = "yaml"
)

func ParseFormat(s string) Format {
	switch s {
	case "json":
		return FormatJSON
	case "yaml", "yml":
		return FormatYAML
	default:
		return FormatTable
	}
}

// TableRenderer renders a slice of structs as a table.
type TableRenderer struct {
	Format   Format
	Columns  []TableColumn
	NoHeader bool
}

type TableColumn struct {
	Name string
	Func func(any) string
}

func NewTableRenderer(fmt Format, columns []TableColumn) *TableRenderer {
	return &TableRenderer{Format: fmt, Columns: columns}
}

func (r *TableRenderer) Render(data []any) error {
	switch r.Format {
	case FormatJSON:
		return renderJSON(DefaultWriter, data)
	case FormatYAML:
		return renderYAML(DefaultWriter, data)
	default:
		return r.renderTable(data)
	}
}

func (r *TableRenderer) renderTable(data []any) error {
	w := tabwriter.NewWriter(DefaultWriter, 0, 0, 3, ' ', 0)

	if !r.NoHeader {
		for i, c := range r.Columns {
			if i > 0 {
				fmt.Fprint(w, "\t")
			}
			fmt.Fprint(w, c.Name)
		}
		fmt.Fprintln(w)
	}

	for _, row := range data {
		for i, c := range r.Columns {
			if i > 0 {
				fmt.Fprint(w, "\t")
			}
			fmt.Fprint(w, c.Func(row))
		}
		fmt.Fprintln(w)
	}
	return w.Flush()
}

func renderJSON(w io.Writer, v any) error {
	enc := json.NewEncoder(w)
	enc.SetIndent("", "  ")
	return enc.Encode(v)
}

func renderYAML(w io.Writer, v any) error {
	data, err := yaml.Marshal(v)
	if err != nil {
		return fmt.Errorf("marshal yaml: %w", err)
	}
	_, err = w.Write(data)
	return err
}
```

Note: need `sigs.k8s.io/yaml` for YAML support:

```bash
cd /home/kevin/workspaces/side-project/IoT-forge/01-cli
go get sigs.k8s.io/yaml@latest
go mod tidy
```

- [ ] **Step 2: Verify compilation**

```bash
cd /home/kevin/workspaces/side-project/IoT-forge/01-cli
go build ./pkg/output/
echo 'output build ok'
```

- [ ] **Step 3: Commit**

```bash
cd /home/kevin/workspaces/side-project/IoT-forge
git add 01-cli/pkg/output/
git commit -m "feat(cli): add table/JSON/YAML output formatting"

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```

---

### Task 6: Device SDK + CLI CRUD commands

**Files:**
- Create: `01-cli/pkg/client/devices.go`
- Create: `01-cli/cmd/iotforge/device/list.go`
- Create: `01-cli/cmd/iotforge/device/get.go`
- Create: `01-cli/cmd/iotforge/device/create.go`
- Create: `01-cli/cmd/iotforge/device/update.go`
- Create: `01-cli/cmd/iotforge/device/delete.go`
- Create: `01-cli/cmd/iotforge/device/decommission.go`
- Create: `01-cli/cmd/iotforge/device/stats.go`
- Create: `01-cli/cmd/iotforge/device/device.go` (parent)

**Interfaces:**
- Produces: `Client.ListDevices(ctx, filter)` → `[]DeviceResponse`; `Client.GetDevice(ctx, id, tree)` → `DeviceResponse`; `Client.CreateDevice(ctx, req)` → `DeviceResponse`; `Client.UpdateDevice(ctx, id, req)` → `DeviceResponse`; `Client.DeleteDevice(ctx, id)` → error; `Client.DecommissionDevice(ctx, id)` → error; `Client.GetDeviceStats(ctx)` → `DeviceStatsResponse`; CLI `iotforge device {list|get|create|update|delete|decommission|stats}` commands
- Consumes: `pkg/client/client.go`, `pkg/dto/device.go`, `pkg/output/`

- [ ] **Step 1: Write device SDK**

Write `01-cli/pkg/client/devices.go`:

```go
package client

import (
	"context"
	"fmt"
	"net/url"
	"strconv"

	"iot-forge-cli/pkg/dto"
)

type ListDevicesFilter struct {
	DeviceType string
	Status     dto.DeviceStatus
	Keyword    string
	Page       int
	Size       int
}

func (c *Client) ListDevices(ctx context.Context, filter *ListDevicesFilter) ([]dto.DeviceResponse, int64, error) {
	params := url.Values{}
	if filter != nil {
		if filter.DeviceType != "" {
			params.Set("deviceType", filter.DeviceType)
		}
		if filter.Status != "" {
			params.Set("status", string(filter.Status))
		}
		if filter.Keyword != "" {
			params.Set("keyword", filter.Keyword)
		}
		params.Set("page", strconv.Itoa(filter.Page))
		if filter.Size > 0 {
			params.Set("size", strconv.Itoa(filter.Size))
		}
	}
	if params.Get("size") == "" {
		params.Set("size", "20")
	}

	path := "/v1/auth/devices?" + params.Encode()
	var pageResp dto.PageResponse
	if err := c.doRequest(ctx, "GET", path, nil, &pageResp); err != nil {
		return nil, 0, err
	}

	var devices []dto.DeviceResponse
	if err := unmarshalRaw(pageResp.Content, &devices); err != nil {
		return nil, 0, fmt.Errorf("parse devices: %w", err)
	}
	return devices, pageResp.TotalElements, nil
}

func (c *Client) GetDevice(ctx context.Context, id int64, tree bool) (*dto.DeviceResponse, error) {
	path := fmt.Sprintf("/v1/auth/devices/%d", id)
	if tree {
		path = fmt.Sprintf("/v1/auth/devices/tree/%d", id)
	}
	var device dto.DeviceResponse
	if err := c.doRequest(ctx, "GET", path, nil, &device); err != nil {
		return nil, err
	}
	return &device, nil
}

func (c *Client) CreateDevice(ctx context.Context, req *dto.DeviceRequest) (*dto.DeviceResponse, error) {
	var device dto.DeviceResponse
	if err := c.doRequest(ctx, "POST", "/v1/auth/devices", req, &device); err != nil {
		return nil, err
	}
	return &device, nil
}

func (c *Client) UpdateDevice(ctx context.Context, id int64, req *dto.DeviceRequest) (*dto.DeviceResponse, error) {
	var device dto.DeviceResponse
	if err := c.doRequest(ctx, "PUT", fmt.Sprintf("/v1/auth/devices/%d", id), req, &device); err != nil {
		return nil, err
	}
	return &device, nil
}

func (c *Client) DeleteDevice(ctx context.Context, id int64) error {
	return c.doRequest(ctx, "DELETE", fmt.Sprintf("/v1/auth/devices/%d", id), nil, nil)
}

func (c *Client) DecommissionDevice(ctx context.Context, id int64) error {
	return c.doRequest(ctx, "POST", fmt.Sprintf("/v1/auth/devices/%d/decommission", id), nil, nil)
}

func (c *Client) GetDeviceStats(ctx context.Context) (*dto.DeviceStatsResponse, error) {
	var stats dto.DeviceStatsResponse
	if err := c.doRequest(ctx, "GET", "/v1/auth/devices/stats", nil, &stats); err != nil {
		return nil, err
	}
	return &stats, nil
}
```

Add helper to `client.go`:

```go
import "encoding/json"

func unmarshalRaw(raw json.RawMessage, target any) error {
	return json.Unmarshal(raw, target)
}
```

- [ ] **Step 2: Create device parent command + list**

Write `01-cli/cmd/iotforge/device/device.go`:

```go
package device

import "github.com/spf13/cobra"

var (
	deviceType string
	status     string
	keyword    string
	page       int
	size       int
)

func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "device",
		Short: "Manage IoT devices",
		Long:  "Create, read, update, delete, and manage IoT devices.",
	}
	cmd.AddCommand(newListCmd())
	cmd.AddCommand(newGetCmd())
	cmd.AddCommand(newCreateCmd())
	cmd.AddCommand(newUpdateCmd())
	cmd.AddCommand(newDeleteCmd())
	cmd.AddCommand(newDecommissionCmd())
	cmd.AddCommand(newStatsCmd())
	return cmd
}
```

Write `01-cli/cmd/iotforge/device/list.go`:

```go
package device

import (
	"context"
	"fmt"

	"iot-forge-cli/pkg/client"
	"iot-forge-cli/pkg/config"
	"iot-forge-cli/pkg/dto"
	"iot-forge-cli/pkg/output"

	"github.com/spf13/cobra"
	"github.com/spf13/viper"
)

func newListCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "list",
		Short: "List devices",
		RunE:  runList,
	}
	cmd.Flags().StringVar(&deviceType, "type", "", "Filter by device type")
	cmd.Flags().StringVar(&status, "status", "", "Filter by status (ACTIVE, INACTIVE, DECOMMISSIONED)")
	cmd.Flags().StringVar(&keyword, "keyword", "", "Search keyword")
	cmd.Flags().IntVar(&page, "page", 0, "Page number (0-based)")
	cmd.Flags().IntVar(&size, "size", 20, "Page size")
	return cmd
}

func runList(cmd *cobra.Command, args []string) error {
	cfg, err := resolveConfig()
	if err != nil {
		return fmt.Errorf("config error: %w", err)
	}

	c := buildClient(cfg)

	filter := &client.ListDevicesFilter{
		DeviceType: deviceType,
		Keyword:    keyword,
		Page:       page,
		Size:       size,
	}
	if status != "" {
		filter.Status = dto.DeviceStatus(status)
	}

	devices, total, err := c.ListDevices(context.Background(), filter)
	if err != nil {
		return err
	}

	fmtFmt := output.ParseFormat(viper.GetString("output"))

	if fmtFmt == output.FormatTable {
		output.NewTableRenderer(fmtFmt, []output.TableColumn{
			{Name: "ID", Func: func(r any) string { return fmt.Sprintf("%d", r.(dto.DeviceResponse).ID) }},
			{Name: "NAME", Func: func(r any) string { return r.(dto.DeviceResponse).Name }},
			{Name: "TYPE", Func: func(r any) string { return r.(dto.DeviceResponse).DeviceType }},
			{Name: "STATUS", Func: func(r any) string { return string(r.(dto.DeviceResponse).Status) }},
			{Name: "CREATED", Func: func(r any) string { return r.(dto.DeviceResponse).CreatedAt }},
		}).Render(toAnySlice(devices))
	} else {
		if err := output.NewTableRenderer(fmtFmt, nil).Render(devices); err != nil {
			return err
		}
	}

	fmt.Printf("\nTotal: %d devices (page %d)\n", total, page)
	return nil
}

func toAnySlice[T any](s []T) []any {
	result := make([]any, len(s))
	for i, v := range s {
		result[i] = v
	}
	return result
}

func resolveConfig() (*config.Config, error) {
	cfg, err := config.Load()
	if err != nil {
		return nil, err
	}
	if v := viper.GetString("endpoint"); v != "" {
		cfg.Endpoint = v
	}
	if v := viper.GetString("api_token"); v != "" {
		cfg.APIToken = v
	}
	// CLI flag overrides (bound in root)
	return cfg, nil
}

func buildClient(cfg *config.Config) *client.Client {
	opts := []client.ClientOption{}
	if cfg.APIToken != "" {
		opts = append(opts, client.WithAPIToken(cfg.APIToken))
	} else if cfg.AccessToken != "" {
		opts = append(opts, client.WithToken(cfg.AccessToken))
		opts = append(opts, client.WithRefreshToken(cfg.RefreshToken))
	}
	return client.New(cfg.Endpoint, opts...)
}
```

- [ ] **Step 3: Write device get/create/update/delete/decommission/stats commands**

Write `01-cli/cmd/iotforge/device/get.go`:

```go
package device

import (
	"context"
	"fmt"
	"strconv"

	"iot-forge-cli/pkg/output"

	"github.com/spf13/cobra"
)

var tree bool

func newGetCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "get <id>",
		Short: "Get device details",
		Args:  cobra.ExactArgs(1),
		RunE:  runGet,
	}
	cmd.Flags().BoolVar(&tree, "tree", false, "Include device composition tree")
	return cmd
}

func runGet(cmd *cobra.Command, args []string) error {
	id, err := strconv.ParseInt(args[0], 10, 64)
	if err != nil {
		return fmt.Errorf("invalid device ID: %s", args[0])
	}

	cfg, err := resolveConfig()
	if err != nil {
		return err
	}

	c := buildClient(cfg)
	device, err := c.GetDevice(context.Background(), id, tree)
	if err != nil {
		return err
	}

	fmtFmt := output.ParseFormat(viper.GetString("output"))
	if fmtFmt == output.FormatTable {
		output.NewTableRenderer(fmtFmt, []output.TableColumn{
			{Name: "FIELD", Func: func(r any) string { return r.([]string)[0] }},
			{Name: "VALUE", Func: func(r any) string { return r.([]string)[1] }},
		}).Render([]any{
			[]string{"ID", fmt.Sprintf("%d", device.ID)},
			[]string{"Name", device.Name},
			[]string{"Type", device.DeviceType},
			[]string{"Status", string(device.Status)},
			[]string{"Serial", device.SerialNumber},
			[]string{"Tenant", device.TenantID},
			[]string{"Created", device.CreatedAt},
		})
	} else {
		output.NewTableRenderer(fmtFmt, nil).Render([]any{device})
	}
	return nil
}
```

Write `01-cli/cmd/iotforge/device/create.go`:

```go
package device

import (
	"context"
	"fmt"
	"os"

	"iot-forge-cli/pkg/client"
	"iot-forge-cli/pkg/dto"
	"iot-forge-cli/pkg/output"

	"github.com/spf13/cobra"
)

var filePath string

func newCreateCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "create -f <file.json>",
		Short: "Create a new device",
		RunE:  runCreate,
	}
	cmd.Flags().StringVarP(&filePath, "file", "f", "", "JSON or YAML file with device data")
	cmd.MarkFlagRequired("file")
	return cmd
}

func runCreate(cmd *cobra.Command, args []string) error {
	data, err := os.ReadFile(filePath)
	if err != nil {
		return fmt.Errorf("read file: %w", err)
	}

	var req dto.DeviceRequest
	if err := parseInput(data, &req); err != nil {
		return err
	}

	cfg, err := resolveConfig()
	if err != nil {
		return err
	}

	c := buildClient(cfg)
	device, err := c.CreateDevice(context.Background(), &req)
	if err != nil {
		return err
	}

	fmtFmt := output.ParseFormat(viper.GetString("output"))
	if fmtFmt == output.FormatTable {
		fmt.Printf("✓ Device created: %s (ID: %d)\n", device.Name, device.ID)
	} else {
		output.NewTableRenderer(fmtFmt, nil).Render([]any{device})
	}
	return nil
}

func parseInput(data []byte, target any) error {
	// Try JSON first, then YAML
	if err := json.Unmarshal(data, target); err == nil {
		return nil
	}
	if err := yaml.Unmarshal(data, target); err == nil {
		return nil
	}
	return fmt.Errorf("file must be valid JSON or YAML")
}
```

Note: Needs `"encoding/json"` and `"sigs.k8s.io/yaml"` imports.

Write `01-cli/cmd/iotforge/device/update.go`:

```go
package device

import (
	"context"
	"fmt"
	"os"
	"strconv"

	"iot-forge-cli/pkg/dto"
	"iot-forge-cli/pkg/output"

	"github.com/spf13/cobra"
)

func newUpdateCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "update <id> -f <file.json>",
		Short: "Update a device",
		Args:  cobra.ExactArgs(1),
		RunE:  runUpdate,
	}
	cmd.Flags().StringVarP(&filePath, "file", "f", "", "JSON or YAML file with device data")
	cmd.MarkFlagRequired("file")
	return cmd
}

func runUpdate(cmd *cobra.Command, args []string) error {
	id, err := strconv.ParseInt(args[0], 10, 64)
	if err != nil {
		return fmt.Errorf("invalid device ID: %s", args[0])
	}

	data, err := os.ReadFile(filePath)
	if err != nil {
		return fmt.Errorf("read file: %w", err)
	}

	var req dto.DeviceRequest
	if err := parseInput(data, &req); err != nil {
		return err
	}

	cfg, err := resolveConfig()
	if err != nil {
		return err
	}

	c := buildClient(cfg)
	device, err := c.UpdateDevice(context.Background(), id, &req)
	if err != nil {
		return err
	}

	fmtFmt := output.ParseFormat(viper.GetString("output"))
	if fmtFmt == output.FormatTable {
		fmt.Printf("✓ Device updated: %s (ID: %d)\n", device.Name, device.ID)
	} else {
		output.NewTableRenderer(fmtFmt, nil).Render([]any{device})
	}
	return nil
}
```

Write `01-cli/cmd/iotforge/device/delete.go`:

```go
package device

import (
	"context"
	"fmt"
	"strconv"

	"github.com/spf13/cobra"
)

func newDeleteCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "delete <id>",
		Short: "Delete a device (fails if device has children)",
		Args:  cobra.ExactArgs(1),
		RunE:  runDelete,
	}
}

func runDelete(cmd *cobra.Command, args []string) error {
	id, err := strconv.ParseInt(args[0], 10, 64)
	if err != nil {
		return fmt.Errorf("invalid device ID: %s", args[0])
	}

	cfg, err := resolveConfig()
	if err != nil {
		return err
	}

	c := buildClient(cfg)
	if err := c.DeleteDevice(context.Background(), id); err != nil {
		return err
	}

	fmt.Printf("✓ Device %d deleted\n", id)
	return nil
}
```

Write `01-cli/cmd/iotforge/device/decommission.go`:

```go
package device

import (
	"context"
	"fmt"
	"strconv"

	"github.com/spf13/cobra"
)

func newDecommissionCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "decommission <id>",
		Short: "Mark a device as decommissioned",
		Args:  cobra.ExactArgs(1),
		RunE:  runDecommission,
	}
}

func runDecommission(cmd *cobra.Command, args []string) error {
	id, err := strconv.ParseInt(args[0], 10, 64)
	if err != nil {
		return fmt.Errorf("invalid device ID: %s", args[0])
	}

	cfg, err := resolveConfig()
	if err != nil {
		return err
	}

	c := buildClient(cfg)
	if err := c.DecommissionDevice(context.Background(), id); err != nil {
		return err
	}

	fmt.Printf("✓ Device %d decommissioned\n", id)
	return nil
}
```

Write `01-cli/cmd/iotforge/device/stats.go`:

```go
package device

import (
	"context"
	"fmt"

	"iot-forge-cli/pkg/output"

	"github.com/spf13/cobra"
)

func newStatsCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "stats",
		Short: "Device statistics summary",
		RunE:  runStats,
	}
}

func runStats(cmd *cobra.Command, args []string) error {
	cfg, err := resolveConfig()
	if err != nil {
		return err
	}

	c := buildClient(cfg)
	stats, err := c.GetDeviceStats(context.Background())
	if err != nil {
		return err
	}

	fmtFmt := output.ParseFormat(viper.GetString("output"))
	if fmtFmt == output.FormatTable {
		var rows []any
		for k, v := range stats.TotalByType {
			rows = append(rows, []string{"Type: " + k, fmt.Sprintf("%d", v)})
		}
		for k, v := range stats.TotalByStatus {
			rows = append(rows, []string{"Status: " + k, fmt.Sprintf("%d", v)})
		}
		rows = append(rows, []string{"Online Rate", fmt.Sprintf("%.1f%%", stats.OnlineRate*100)})
		rows = append(rows, []string{"Open Dispatches", fmt.Sprintf("%d", stats.OpenDispatches)})

		output.NewTableRenderer(fmtFmt, []output.TableColumn{
			{Name: "METRIC", Func: func(r any) string { return r.([]string)[0] }},
			{Name: "VALUE", Func: func(r any) string { return r.([]string)[1] }},
		}).Render(rows)
	} else {
		output.NewTableRenderer(fmtFmt, nil).Render([]any{stats})
	}
	return nil
}
```

- [ ] **Step 4: Register device commands in root**

In `01-cli/cmd/iotforge/root.go`, add import and registration:

```go
import "iot-forge-cli/cmd/iotforge/device"

func init() {
	// ... existing init code ...
	rootCmd.AddCommand(device.NewCmd())
}
```

- [ ] **Step 5: Verify compilation**

```bash
cd /home/kevin/workspaces/side-project/IoT-forge/01-cli
go build ./cmd/iotforge/
echo 'device build ok'
```

- [ ] **Step 6: Commit**

```bash
cd /home/kevin/workspaces/side-project/IoT-forge
git add 01-cli/pkg/client/devices.go 01-cli/cmd/iotforge/device/
git commit -m "feat(cli): implement device SDK + full CRUD CLI commands"

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```

---

### Task 7: Telemetry SDK + CLI query command

**Files:**
- Create: `01-cli/pkg/client/telemetry.go`
- Create: `01-cli/cmd/iotforge/telemetry/telemetry.go`
- Create: `01-cli/cmd/iotforge/telemetry/query.go`

**Interfaces:**
- Produces: `Client.QueryTelemetry(ctx, deviceID, from, to, metric, interval)` → `[]TelemetryReading`; CLI `iotforge telemetry query <device-id>` command
- Consumes: `pkg/client/client.go`, `pkg/dto/telemetry.go`, `pkg/output/`

**Note:** Telemetry and dispatch subcommands do NOT redefine `--output`. They inherit it from root's persistent flag and read the value via `viper.GetString("output")`.

- [ ] **Step 1: Write telemetry SDK**

Write `01-cli/pkg/client/telemetry.go`:

```go
package client

import (
	"context"
	"fmt"
	"net/url"
	"strconv"

	"iot-forge-cli/pkg/dto"
)

type QueryTelemetryFilter struct {
	From     string
	To       string
	Metric   string
	Interval string
}

func (c *Client) QueryTelemetry(ctx context.Context, deviceID int64, filter *QueryTelemetryFilter) (*dto.TelemetryQueryResponse, error) {
	params := url.Values{}
	if filter != nil {
		if filter.From != "" {
			params.Set("from", filter.From)
		}
		if filter.To != "" {
			params.Set("to", filter.To)
		}
		if filter.Metric != "" {
			params.Set("metric", filter.Metric)
		}
		if filter.Interval != "" {
			params.Set("interval", filter.Interval)
		}
	}

	path := fmt.Sprintf("/v1/auth/telemetry/%d?%s", deviceID, params.Encode())
	var resp dto.TelemetryQueryResponse
	if err := c.doRequest(ctx, "GET", path, nil, &resp); err != nil {
		return nil, err
	}
	return &resp, nil
}
```

- [ ] **Step 2: Write telemetry CLI commands**

Write `01-cli/cmd/iotforge/telemetry/telemetry.go`:

```go
package telemetry

import (
	"iot-forge-cli/pkg/client"
	"iot-forge-cli/pkg/config"

	"github.com/spf13/cobra"
	"github.com/spf13/viper"
)

func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "telemetry",
		Short: "Query device telemetry data",
	}
	cmd.AddCommand(newQueryCmd())
	return cmd
}

func resolveConfig() (*config.Config, error) {
	cfg, err := config.Load()
	if err != nil {
		return nil, err
	}
	if v := viper.GetString("endpoint"); v != "" {
		cfg.Endpoint = v
	}
	if v := viper.GetString("api_token"); v != "" {
		cfg.APIToken = v
	}
	return cfg, nil
}
```

Wait, this is duplicating `resolveConfig` and `buildClient`. Better to create a shared helper. Let me put shared helpers in a separate file.

Or better yet, use the same approach: the root command provides the `--output` flag, and each subcommand package can reference global state.

Actually, looking at this, the cleanest approach for v1 is to have a shared `pkg/cliutil/` with common helper functions, or simply pass these through root command persistent flags. Let me keep it simple and have each package import config and build its own client. But to avoid duplication, let me create a small helper.

Create `01-cli/pkg/client/helpers.go`:

```go
package client

import (
	"iot-forge-cli/pkg/config"
)

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
```

Then telemetry and dispatch commands use `client.NewClientFromConfig(cfg)`.

Write `01-cli/cmd/iotforge/telemetry/query.go`:

```go
package telemetry

import (
	"context"
	"fmt"
	"strconv"

	"iot-forge-cli/pkg/client"
	"iot-forge-cli/pkg/config"
	"iot-forge-cli/pkg/dto"
	"iot-forge-cli/pkg/output"

	"github.com/spf13/cobra"
	"github.com/spf13/viper"
)

var (
	from     string
	to       string
	metric   string
	interval string
)

func newQueryCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "query <device-id>",
		Short: "Query telemetry data for a device",
		Args:  cobra.ExactArgs(1),
		RunE:  runQuery,
	}
	cmd.Flags().StringVar(&from, "from", "", "Start time (ISO8601, required)")
	cmd.Flags().StringVar(&to, "to", "", "End time (default: now)")
	cmd.Flags().StringVar(&metric, "metric", "", "Filter specific metric (e.g. temperature)")
	cmd.Flags().StringVar(&interval, "interval", "", "Aggregation interval (e.g. 1m, 5m, 1h)")
	cmd.MarkFlagRequired("from")
	return cmd
}

func runQuery(cmd *cobra.Command, args []string) error {
	deviceID, err := strconv.ParseInt(args[0], 10, 64)
	if err != nil {
		return fmt.Errorf("invalid device ID: %s", args[0])
	}

	cfg, err := resolveConfig()
	if err != nil {
		return err
	}

	c := client.NewClientFromConfig(cfg)
	resp, err := c.QueryTelemetry(context.Background(), deviceID, &client.QueryTelemetryFilter{
		From:     from,
		To:       to,
		Metric:   metric,
		Interval: interval,
	})
	if err != nil {
		return err
	}

	fmtFmt := output.ParseFormat(viper.GetString("output"))
	if fmtFmt == output.FormatTable {
		renderTelemetryTable(resp)
	} else {
		output.NewTableRenderer(fmtFmt, nil).Render([]any{resp})
	}
	return nil
}

func renderTelemetryTable(resp *dto.TelemetryQueryResponse) {
	rows := make([]any, len(resp.Readings))
	for i, r := range resp.Readings {
		rows[i] = []string{
			r.Timestamp,
			r.Metric,
			fmt.Sprintf("%.2f", r.Value),
			r.Unit,
		}
	}
	output.NewTableRenderer(output.FormatTable, []output.TableColumn{
		{Name: "TIMESTAMP", Func: func(r any) string { return r.([]string)[0] }},
		{Name: "METRIC", Func: func(r any) string { return r.([]string)[1] }},
		{Name: "VALUE", Func: func(r any) string { return r.([]string)[2] }},
		{Name: "UNIT", Func: func(r any) string { return r.([]string)[3] }},
	}).Render(rows)
}
```

Add the `NewClientFromConfig` helper to `pkg/client/client.go` and register telemetry in root:

```go
import "iot-forge-cli/cmd/iotforge/telemetry"

func init() {
	rootCmd.AddCommand(device.NewCmd())
	rootCmd.AddCommand(telemetry.NewCmd())
}
```

- [ ] **Step 3: Add the `NewClientFromConfig` helper**

Append to `01-cli/pkg/client/client.go`:

```go
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
```

Import `"iot-forge-cli/pkg/config"` in `client.go`.

- [ ] **Step 4: Verify compilation**

```bash
cd /home/kevin/workspaces/side-project/IoT-forge/01-cli
go build ./cmd/iotforge/
echo 'telemetry build ok'
```

- [ ] **Step 5: Commit**

```bash
cd /home/kevin/workspaces/side-project/IoT-forge
git add 01-cli/pkg/client/telemetry.go 01-cli/cmd/iotforge/telemetry/ 01-cli/pkg/client/client.go
git commit -m "feat(cli): implement telemetry SDK + query CLI command"

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```

---

### Task 8: Dispatch SDK + CLI commands

**Files:**
- Create: `01-cli/pkg/client/dispatch.go`
- Create: `01-cli/cmd/iotforge/dispatch/dispatch.go`
- Create: `01-cli/cmd/iotforge/dispatch/list.go`
- Create: `01-cli/cmd/iotforge/dispatch/get.go`
- Create: `01-cli/cmd/iotforge/dispatch/update.go`

**Interfaces:**
- Produces: `Client.ListDispatches(ctx, filter)` → `[]DispatchResponse`; `Client.GetDispatch(ctx, id)` → `DispatchResponse`; `Client.UpdateDispatch(ctx, id, req)` → `DispatchResponse`; CLI `iotforge dispatch {list|get|update}` commands
- Consumes: `pkg/client/client.go`, `pkg/dto/dispatch.go`, `pkg/output/`

- [ ] **Step 1: Write dispatch SDK**

Write `01-cli/pkg/client/dispatch.go`:

```go
package client

import (
	"context"
	"fmt"
	"net/url"
	"strconv"

	"iot-forge-cli/pkg/dto"
)

type ListDispatchFilter struct {
	Status   dto.DispatchStatus
	Priority dto.DispatchPriority
	Keyword  string
	Page     int
	Size     int
}

func (c *Client) ListDispatches(ctx context.Context, filter *ListDispatchFilter) ([]dto.DispatchResponse, int64, error) {
	params := url.Values{}
	if filter != nil {
		if filter.Status != "" {
			params.Set("status", string(filter.Status))
		}
		if filter.Priority != "" {
			params.Set("priority", string(filter.Priority))
		}
		if filter.Keyword != "" {
			params.Set("keyword", filter.Keyword)
		}
		params.Set("page", strconv.Itoa(filter.Page))
		if filter.Size > 0 {
			params.Set("size", strconv.Itoa(filter.Size))
		}
	}
	if params.Get("size") == "" {
		params.Set("size", "20")
	}

	path := "/v1/auth/dispatch?" + params.Encode()
	var pageResp dto.PageResponse
	if err := c.doRequest(ctx, "GET", path, nil, &pageResp); err != nil {
		return nil, 0, err
	}

	var dispatches []dto.DispatchResponse
	if err := unmarshalRaw(pageResp.Content, &dispatches); err != nil {
		return nil, 0, fmt.Errorf("parse dispatches: %w", err)
	}
	return dispatches, pageResp.TotalElements, nil
}

func (c *Client) GetDispatch(ctx context.Context, id int64) (*dto.DispatchResponse, error) {
	var dispatch dto.DispatchResponse
	if err := c.doRequest(ctx, "GET", fmt.Sprintf("/v1/auth/dispatch/%d", id), nil, &dispatch); err != nil {
		return nil, err
	}
	return &dispatch, nil
}

func (c *Client) UpdateDispatch(ctx context.Context, id int64, req *dto.DispatchRequest) (*dto.DispatchResponse, error) {
	var dispatch dto.DispatchResponse
	if err := c.doRequest(ctx, "PUT", fmt.Sprintf("/v1/auth/dispatch/%d", id), req, &dispatch); err != nil {
		return nil, err
	}
	return &dispatch, nil
}
```

- [ ] **Step 2: Write dispatch CLI commands**

Write `01-cli/cmd/iotforge/dispatch/dispatch.go`:

```go
package dispatch

import (
	"iot-forge-cli/pkg/client"
	"iot-forge-cli/pkg/config"

	"github.com/spf13/cobra"
	"github.com/spf13/viper"
)

func NewCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "dispatch",
		Short: "Manage dispatch work orders",
	}
	cmd.AddCommand(newListCmd())
	cmd.AddCommand(newGetCmd())
	cmd.AddCommand(newUpdateCmd())
	return cmd
}

func resolveConfig() (*config.Config, error) {
	cfg, err := config.Load()
	if err != nil {
		return nil, err
	}
	if v := viper.GetString("endpoint"); v != "" {
		cfg.Endpoint = v
	}
	if v := viper.GetString("api_token"); v != "" {
		cfg.APIToken = v
	}
	return cfg, nil
}

func buildClient(cfg *config.Config) *client.Client {
	return client.NewClientFromConfig(cfg)
}
```

Write `01-cli/cmd/iotforge/dispatch/list.go`:

```go
package dispatch

import (
	"context"
	"fmt"

	"iot-forge-cli/pkg/client"
	"iot-forge-cli/pkg/dto"
	"iot-forge-cli/pkg/output"

	"github.com/spf13/cobra"
		"github.com/spf13/viper"
)

var (
	statusFilter   string
	priorityFilter string
	keywordFilter  string
	page           int
	size           int
)

func newListCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "list",
		Short: "List dispatch work orders",
		RunE:  runList,
	}
	cmd.Flags().StringVar(&statusFilter, "status", "", "Filter by status (PENDING, APPROVED, IN_PROGRESS, COMPLETED, REJECTED)")
	cmd.Flags().StringVar(&priorityFilter, "priority", "", "Filter by priority (LOW, MEDIUM, HIGH, URGENT)")
	cmd.Flags().StringVar(&keywordFilter, "keyword", "", "Search keyword")
	cmd.Flags().IntVar(&page, "page", 0, "Page number (0-based)")
	cmd.Flags().IntVar(&size, "size", 20, "Page size")
	return cmd
}

func runList(cmd *cobra.Command, args []string) error {
	cfg, err := resolveConfig()
	if err != nil {
		return err
	}

	c := buildClient(cfg)
	filter := &client.ListDispatchFilter{
		Keyword: keywordFilter,
		Page:    page,
		Size:    size,
	}
	if statusFilter != "" {
		filter.Status = dto.DispatchStatus(statusFilter)
	}
	if priorityFilter != "" {
		filter.Priority = dto.DispatchPriority(priorityFilter)
	}

	dispatches, total, err := c.ListDispatches(context.Background(), filter)
	if err != nil {
		return err
	}

	fmtFmt := output.ParseFormat(viper.GetString("output"))
	if fmtFmt == output.FormatTable {
		rows := make([]any, len(dispatches))
		for i, d := range dispatches {
			rows[i] = []string{
				fmt.Sprintf("%d", d.ID),
				d.Title,
				string(d.Status),
				string(d.Priority),
				d.AssignedTo,
				d.CreatedAt,
			}
		}
		output.NewTableRenderer(fmtFmt, []output.TableColumn{
			{Name: "ID", Func: func(r any) string { return r.([]string)[0] }},
			{Name: "TITLE", Func: func(r any) string { return r.([]string)[1] }},
			{Name: "STATUS", Func: func(r any) string { return r.([]string)[2] }},
			{Name: "PRIORITY", Func: func(r any) string { return r.([]string)[3] }},
			{Name: "ASSIGNEE", Func: func(r any) string { return r.([]string)[4] }},
			{Name: "CREATED", Func: func(r any) string { return r.([]string)[5] }},
		}).Render(rows)
	} else {
		output.NewTableRenderer(fmtFmt, nil).Render(toAnySlice(dispatches))
	}

	fmt.Printf("\nTotal: %d dispatch orders (page %d)\n", total, page)
	return nil
}

func toAnySlice[T any](s []T) []any {
	result := make([]any, len(s))
	for i, v := range s {
		result[i] = v
	}
	return result
}
```

Write `01-cli/cmd/iotforge/dispatch/get.go`:

```go
package dispatch

import (
	"context"
	"fmt"
	"strconv"

	"iot-forge-cli/pkg/output"

	"github.com/spf13/cobra"
)

func newGetCmd() *cobra.Command {
	return &cobra.Command{
		Use:   "get <id>",
		Short: "Get dispatch work order details",
		Args:  cobra.ExactArgs(1),
		RunE:  runGet,
	}
}

func runGet(cmd *cobra.Command, args []string) error {
	id, err := strconv.ParseInt(args[0], 10, 64)
	if err != nil {
		return fmt.Errorf("invalid dispatch ID: %s", args[0])
	}

	cfg, err := resolveConfig()
	if err != nil {
		return err
	}

	c := buildClient(cfg)
	dispatch, err := c.GetDispatch(context.Background(), id)
	if err != nil {
		return err
	}

	fmtFmt := output.ParseFormat(viper.GetString("output"))
	if fmtFmt == output.FormatTable {
		output.NewTableRenderer(fmtFmt, []output.TableColumn{
			{Name: "FIELD", Func: func(r any) string { return r.([]string)[0] }},
			{Name: "VALUE", Func: func(r any) string { return r.([]string)[1] }},
		}).Render([]any{
			[]string{"ID", fmt.Sprintf("%d", dispatch.ID)},
			[]string{"Title", dispatch.Title},
			[]string{"Description", dispatch.Description},
			[]string{"Status", string(dispatch.Status)},
			[]string{"Priority", string(dispatch.Priority)},
			[]string{"Assignee", dispatch.AssignedTo},
			[]string{"Tenant", dispatch.TenantID},
			[]string{"Created", dispatch.CreatedAt},
		})
	} else {
		output.NewTableRenderer(fmtFmt, nil).Render([]any{dispatch})
	}
	return nil
}
```

Write `01-cli/cmd/iotforge/dispatch/update.go`:

```go
package dispatch

import (
	"context"
	"fmt"
	"os"
	"strconv"

	"iot-forge-cli/pkg/dto"
	"iot-forge-cli/pkg/output"

	"github.com/spf13/cobra"
)

var filePath string

func newUpdateCmd() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "update <id> -f <file.json>",
		Short: "Update a dispatch work order",
		Args:  cobra.ExactArgs(1),
		RunE:  runUpdate,
	}
	cmd.Flags().StringVarP(&filePath, "file", "f", "", "JSON or YAML file with dispatch data")
	cmd.MarkFlagRequired("file")
	return cmd
}

func runUpdate(cmd *cobra.Command, args []string) error {
	id, err := strconv.ParseInt(args[0], 10, 64)
	if err != nil {
		return fmt.Errorf("invalid dispatch ID: %s", args[0])
	}

	data, err := os.ReadFile(filePath)
	if err != nil {
		return fmt.Errorf("read file: %w", err)
	}

	var req dto.DispatchRequest
	if err := parseInput(data, &req); err != nil {
		return err
	}

	cfg, err := resolveConfig()
	if err != nil {
		return err
	}

	c := buildClient(cfg)
	result, err := c.UpdateDispatch(context.Background(), id, &req)
	if err != nil {
		return err
	}

	fmtFmt := output.ParseFormat(viper.GetString("output"))
	if fmtFmt == output.FormatTable {
		fmt.Printf("✓ Dispatch %d updated: %s\n", result.ID, result.Title)
	} else {
		output.NewTableRenderer(fmtFmt, nil).Render([]any{result})
	}
	return nil
}

func parseInput(data []byte, target any) error {
	if err := json.Unmarshal(data, target); err == nil {
		return nil
	}
	if err := yaml.Unmarshal(data, target); err == nil {
		return nil
	}
	return fmt.Errorf("file must be valid JSON or YAML")
}
```

Note: Needs `"encoding/json"` and `"sigs.k8s.io/yaml"` imports.

- [ ] **Step 3: Register dispatch in root**

```go
import "iot-forge-cli/cmd/iotforge/dispatch"

func init() {
	rootCmd.AddCommand(device.NewCmd())
	rootCmd.AddCommand(telemetry.NewCmd())
	rootCmd.AddCommand(dispatch.NewCmd())
}
```

- [ ] **Step 4: Verify compilation**

```bash
cd /home/kevin/workspaces/side-project/IoT-forge/01-cli
go build ./cmd/iotforge/
echo 'dispatch build ok'
```

- [ ] **Step 5: Commit**

```bash
cd /home/kevin/workspaces/side-project/IoT-forge
git add 01-cli/pkg/client/dispatch.go 01-cli/cmd/iotforge/dispatch/
git commit -m "feat(cli): implement dispatch SDK + list/get/update CLI commands"

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```

---

### Task 9: Makefile, README, cross-compilation

**Files:**
- Create: `01-cli/Makefile`
- Create: `01-cli/README.md`
- Modify: `01-cli/.gitignore` (create)

**Interfaces:**
- Produces: build automation and documentation

- [ ] **Step 1: Write Makefile**

Write `01-cli/Makefile`:

```makefile
BINARY_NAME=iotforge
GO_BUILD=CGO_ENABLED=0 go build -ldflags="-s -w"

.PHONY: build build-all test clean

build:
	$(GO_BUILD) -o $(BINARY_NAME) ./cmd/iotforge/

build-linux:
	GOOS=linux GOARCH=amd64 $(GO_BUILD) -o $(BINARY_NAME)-linux ./cmd/iotforge/

build-darwin:
	GOOS=darwin GOARCH=amd64 $(GO_BUILD) -o $(BINARY_NAME)-darwin ./cmd/iotforge/

build-windows:
	GOOS=windows GOARCH=amd64 $(GO_BUILD) -o $(BINARY_NAME).exe ./cmd/iotforge/

build-all: build-linux build-darwin build-windows

test:
	go test ./... -v

clean:
	rm -f $(BINARY_NAME) $(BINARY_NAME)-linux $(BINARY_NAME)-darwin $(BINARY_NAME).exe
	rm -rf dist/

dist: build-all
	mkdir -p dist
	mv $(BINARY_NAME)-linux dist/
	mv $(BINARY_NAME)-darwin dist/
	mv $(BINARY_NAME).exe dist/
	@echo "Binaries in dist/"
```

- [ ] **Step 2: Write .gitignore**

Write `01-cli/.gitignore`:

```
iotforge
iotforge.exe
iotforge-*
dist/
```

- [ ] **Step 3: Write README.md**

Write `01-cli/README.md`:

```markdown
# iotforge CLI

CLI tool for managing IoT-forge platform resources from the terminal.

Supports interactive human login and headless API token mode for AI agents.

## Install

```bash
# Download the binary for your platform from releases
# Or build from source:
go build -o iotforge ./cmd/iotforge/
```

## Quick Start

```bash
# Login interactively
iotforge login

# Check status
iotforge auth status

# List devices
iotforge device list

# Query telemetry
iotforge telemetry query 101 --from 2026-01-01T00:00:00Z --metric temperature

# List dispatch work orders
iotforge dispatch list --status PENDING
```

## AI Agent / Headless Mode

```bash
export IOTFORGE_ENDPOINT=https://iot.example.com
export IOTFORGE_API_TOKEN=your-api-token
export IOTFORGE_TENANT_ID=T001

iotforge device list --output json
```

## Output Formats

All commands support `--output table` (default), `--output json`, and `--output yaml`.

## Global Flags

| Flag | Env | Description |
|---|---|---|
| `--endpoint` | `IOTFORGE_ENDPOINT` | API server URL |
| `--output` | `IOTFORGE_OUTPUT` | Output format (table/json/yaml) |
| `--api-token` | `IOTFORGE_API_TOKEN` | API token for headless mode |
```

- [ ] **Step 4: Build and verify**

```bash
cd /home/kevin/workspaces/side-project/IoT-forge/01-cli
go vet ./... 
go build ./cmd/iotforge/
./iotforge --help
```

Expected: help text with all commands listed.

- [ ] **Step 5: Commit**

```bash
cd /home/kevin/workspaces/side-project/IoT-forge
git add 01-cli/Makefile 01-cli/README.md 01-cli/.gitignore
git commit -m "chore(cli): add Makefile, README, .gitignore"

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```
