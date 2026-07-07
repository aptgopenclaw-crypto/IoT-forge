# iotforge CLI — Design Spec

## Meta

- **Date:** 2026-07-02
- **Status:** Implemented (v1)
- **Authors:** Planning session
- **Related:** IoT-forge backend (multi-tenant IoT platform)
- **Implementation:** 9 tasks, 9 commits, pushed to `main`

## 1. Overview

iotforge is a standalone CLI tool for tenant administrators and AI agents to manage IoT-forge resources from the terminal. It connects to the existing IoT-forge REST API, reusing the complete auth / RBAC / DataScope / multi-tenant security chain on the server side.

### 1.1 Design Goals

- **Security-first**: Same JWT + scope enforcement + tenant isolation as the web UI
- **Dual-mode**: Interactive human login AND headless API-token mode for AI agents
- **RBAC/DataScope transparent**: Every command respects the logged-in user's existing permissions — no reinvention
- **Single binary distribution**: Go-based, no runtime dependencies

## 2. Technology Stack

| Layer | Choice | Rationale |
|---|---|---|
| Language | Go 1.22+ | Single binary, cross-compile, ecosystem standard (cobra, viper) |
| CLI framework | Cobra | De facto Go CLI framework (kubectl, gh, hugo, etc.) |
| Config | Viper | File + env var + flag cascade |
| HTTP | `net/http` + custom client | Lightweight; no heavy framework needed |
| Output | `encoding/json`, table writer | Table (human) + JSON/YAML (machine) |

## 3. High-Level Architecture

```
┌─────────────────────────────────────────────────┐
│  User Terminal / AI Agent                        │
│  ┌───────────────────────────────────────────┐   │
│  │  iotforge <command> [flags]               │   │
│  └──────────────┬────────────────────────────┘   │
│                 │                                │
│            cmd/ layer (Cobra commands)            │
│         - parse flags, call SDK, format output    │
│                 │                                │
│            pkg/client/ layer (Go SDK)            │
│         - typed API methods, auto token refresh   │
│                 │                                │
│            HTTPS + JWT (Authorization: Bearer)    │
└─────────────────┼────────────────────────────────┘
                  │
     ┌────────────▼────────────┐
     │   IoT-forge Backend      │
     │   (Spring Boot)          │
     │                          │
     │  JwtAuthFilter           │
     │  ─> ScopeEnforcement     │
     │  ─> TenantContext         │
     │  ─> RBAC (@PreAuthorize)  │
     │  ─> DataScope             │
     └──────────────────────────┘
```

**Key principle**: CLI is a presentation layer only. All authorization, tenant isolation, and data scoping are enforced server-side. The CLI never trusts its own state for access decisions.

## 4. Directory Structure

```
iot-forge-cli/
├── cmd/
│   └── iotforge/
│       ├── main.go
│       ├── root.go              # Persistent flags (--endpoint, --output, --json)
│       ├── login.go             # iotforge login
│       ├── logout.go            # iotforge logout
│       ├── auth_status.go       # iotforge auth status
│       ├── device/
│       │   ├── device.go        # iotforge device (parent command)
│       │   ├── list.go          # iotforge device list
│       │   ├── get.go           # iotforge device get <id>
│       │   ├── create.go        # iotforge device create -f <file>
│       │   ├── update.go        # iotforge device update <id> -f <file>
│       │   ├── delete.go        # iotforge device delete <id>
│       │   ├── decommission.go  # iotforge device decommission <id>
│       │   └── stats.go         # iotforge device stats
│       ├── telemetry/
│       │   ├── telemetry.go     # iotforge telemetry (parent command)
│       │   └── query.go         # iotforge telemetry query <device-id>
│       └── dispatch/
│           ├── dispatch.go      # iotforge dispatch (parent command)
│           ├── list.go          # iotforge dispatch list
│           ├── get.go           # iotforge dispatch get <id>
│           └── update.go        # iotforge dispatch update <id> -f <file>
├── pkg/
│   ├── client/                  # Go SDK — importable by AI agents
│   │   ├── client.go            # Core HTTP client, doRequest(), auto-refresh
│   │   ├── auth.go              # Login, Logout, RefreshToken, ListTenants, SelectTenant
│   │   ├── devices.go           # Device CRUD methods
│   │   ├── telemetry.go         # Telemetry query methods
│   │   └── dispatch.go          # Dispatch CRUD methods
│   ├── config/
│   │   └── config.go            # ~/.iotforge/config.json management
│   ├── dto/
│   │   ├── device.go            # Device request/response structs
│   │   ├── telemetry.go         # Telemetry data structs
│   │   └── dispatch.go          # Dispatch request/response structs
│   └── output/
│       ├── table.go             # Human-readable table formatting
│       └── json.go              # JSON / YAML output helpers
├── go.mod
├── go.sum
├── Makefile                     # build, test, lint, cross-compile
└── README.md
```

## 5. Authentication Flow

### 5.1 Human Interactive Login

```
$ iotforge login
Username: alice
Password: [hidden input]
✓ Authenticated

Available tenants:
  1. 台北廠 (T001)
  2. 高雄廠 (T002)
Select tenant [1]:

✓ Logged in as alice @ 台北廠 (T001)
```

**Protocol:**
1. `POST /v1/auth/login` with `{username, password}` → temporary token
2. `POST /v1/auth/tenants` with temp token → list of `{id, name}`
3. User selects → `POST /v1/auth/tenant/select` with `{tenantId, temporaryToken}` → `{accessToken, refreshToken, expiresIn}`
4. Store `accessToken`, `refreshToken`, `tokenExpiry`, `currentTenant` in `~/.iotforge/config.json`

### 5.2 AI Agent / Headless Login

```
export IOTFORGE_ENDPOINT=https://iot.example.com
export IOTFORGE_API_TOKEN=taipei-iot-api-token-xxx
export IOTFORGE_TENANT_ID=T001

iotforge device list
```

No interactive prompts. The client reads the token from env var (or config file) and uses it directly.

### 5.3 Token Refresh

- `pkg/client/`'s `doRequest()` checks token expiry before each call
- If expired, calls `POST /v1/auth/refresh` with refresh token
- On success: updates stored access token and new expiry; retries original request
- On refresh failure (expired refresh token): returns a clear "session expired, please login again" error

### 5.4 Logout

```
iotforge logout
→ POST /v1/auth/logout  (invalidate server-side session)
→ Delete ~/.iotforge/config.json
```

## 6. Command Reference (v1)

### 6.1 Global Flags

| Flag | Env Var | Default | Description |
|---|---|---|---|
| `--endpoint` | `IOTFORGE_ENDPOINT` | `http://localhost:8080` | API server URL |
| `--output` | `IOTFORGE_OUTPUT` | `table` | Output format: `table`, `json`, `yaml` |
| `--tenant-id` | `IOTFORGE_TENANT_ID` | (from config) | Override current tenant |
| `--api-token` | `IOTFORGE_API_TOKEN` | (from config) | API token for headless mode |

### 6.2 Auth Commands

```
iotforge login                         Interactive login
iotforge logout                        Logout and clear config
iotforge auth status                   Show current user, tenant, token expiry
```

### 6.3 Device Commands

```
iotforge device list                   
  --type sensor                        Filter by device type
  --status ACTIVE                      Filter by status (ACTIVE/INACTIVE/DECOMMISSIONED)
  --keyword search                     Search keyword (name/serial)
  --page 0                             Page number (0-based)
  --size 20                            Page size

iotforge device get <id>              Get device by ID
  --tree                               Include device composition tree

iotforge device create -f file.json   Create device from JSON/YAML file

iotforge device update <id> -f file.json Update device from file

iotforge device delete <id>           Delete device (fails if children exist)

iotforge device decommission <id>     Mark device as decommissioned

iotforge device stats                 Device statistics summary
```

### 6.4 Telemetry Commands

```
iotforge telemetry query <device-id>
  --from 2026-01-01T00:00:00Z         Start time (required)
  --to   2026-07-02T00:00:00Z         End time (default: now)
  --metric temperature                Optional: filter specific metric
  --interval 5m                       Aggregation interval (e.g., 1m, 5m, 1h)
```

### 6.5 Dispatch Commands

```
iotforge dispatch list
  --status PENDING                     Filter by status
  --priority HIGH                      Filter by priority
  --keyword search                     Search keyword
  --page 0
  --size 20

iotforge dispatch get <id>            Get dispatch work order details

iotforge dispatch update <id> -f file.json Update work order (status, assignee, etc.)
```

## 7. Output Format

### 7.1 Table (default)

```
$ iotforge device list
┌──────┬────────────┬────────┬───────────────┬──────────┬──────────────────────┐
│ ID   │ NAME       │ TYPE   │ STATUS        │ TENANT   │ CREATED              │
├──────┼────────────┼────────┼───────────────┼──────────┼──────────────────────┤
│ 101  │ Sensor-A   │ sensor │ ACTIVE        │ T001     │ 2026-06-15 09:30:00  │
│ 102  │ Actuator-1 │ actuator │ INACTIVE     │ T001     │ 2026-06-20 14:00:00  │
└──────┴────────────┴────────┴───────────────┴──────────┴──────────────────────┘
```

### 7.2 JSON / YAML

```bash
iotforge device list --output json
iotforge device get 101 --output yaml
```

Machine-parseable output for AI agents, scripts, and jq/yq pipelines.

## 8. Error Handling

CLI errors map directly from backend `BaseResponse`:

```
Error: 10031 - 權限不足 (Insufficient permissions)
```

- Non-200 HTTP → display `errorCode` + `errorMsg` from backend
- Network errors → friendly message, suggest `--endpoint` check
- Auth errors (401) → trigger auto-refresh; if refresh fails → "Session expired, please login again"

## 9. Security Considerations

| Concern | Mitigation |
|---|---|
| Token theft | Config file permission 600; no password in process args |
| Tenant isolation | Server-enforced — CLI has no `tenantId` override bypass |
| RBAC bypass | Server-enforced — CLI has no special privilege |
| Refresh token replay | Backend refresh endpoint validates token once (rotation) |
| AI agent scope | API token scoped to specific tenant by backend config |

## 10. Future Considerations (not in v1)

- `iotforge device import` — bulk device import (already exists via `/v1/auth/devices/import`)
- `iotforge event-rule` — rule engine CRUD
- `iotforge announcement` — announcement management
- `iotforge completion` — shell completion (bash/zsh)
- Interactive mode (`iotforge shell`) — repl-like persistent session
- Device provisioning / OTA commands
