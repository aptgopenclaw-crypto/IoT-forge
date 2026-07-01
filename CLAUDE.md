# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

### Backend (Spring Boot 3.4.1 / Java 21 / Maven)

```bash
# Build (compile + tests)
cd backend && ./mvn clean verify

# Build skipping tests
./mvn clean verify -DskipTests

# Run only unit tests (excludes @Tag("integration"))
./mvn test

# Run a single test class
./mvn test -Dtest=AnnouncementServiceTest

# Run a single test method
./mvn test -Dtest=AnnouncementServiceTest#createAnnouncement_shouldSucceed

# Run integration tests only
./mvn verify -DskipUTs -Dtest='*IntegrationTest' -DfailIfNoTests=false

# Start the app (needs PostgreSQL, Redis; see application.yml)
./mvn spring-boot:run

# Code formatting check (spring-javaformat, runs on validate)
./mvn validate
```

### Frontend (Vue 3 / TypeScript / Vite)

```bash
cd frontend && npm run dev          # Start dev server on :5173, proxies /v1 → :8080
npm run build                       # Production build (type-check + vite)
npm run type-check                  # vue-tsc --noEmit
npm run test                        # Vitest (watch mode)
npm run test:run                    # Vitest single run
npm run lint:i18n                   # i18n validation tests
npm run lint:a11y                   # Accessibility tests (axe-core)
```

## MCP Tools (development)

Two MCP servers are available during development. Prefer these over raw grep/Read/SQL-clients.

| Server | Tool | Use |
|---|---|---|
| **codegraph** | `codegraph_explore` | Primary: understand code, trace call paths, locate symbols. One call returns verbatim source + call graph — use BEFORE grep/Read. |
| **codegraph** | `codegraph_node` | Read a file (like `Read`) or fetch one symbol's source + callers/callees. |
| **codegraph** | `codegraph_search` | Quick symbol-name search (returns locations only, no source). |
| **codegraph** | `codegraph_callers` | List functions that call a given symbol. |
| **postgres** | `query` | Run read-only SQL against the database. Use during dev to verify data, check migrations, or debug queries without leaving the conversation. |

The project is already indexed (`.codegraph/` exists at the repo root); postgres connects via the MCP server configured in `mcp.json`.

## High-Level Architecture

### Backend Package Structure (`com.taipei.iot`)

The backend is a multi-tenant IoT platform organized by domain module:

| Package | Responsibility |
|---|---|
| `auth` | Authentication: JWT login/refresh/logout, LDAP + local auth providers, scope enforcement, impersonation |
| `tenant` | Tenant (場域) CRUD, `TenantContext` (ThreadLocal), tenant lifecycle |
| `rbac` | Role-based access control: roles, permissions, menus |
| `dept` | Department tree, data scope filters (exact / hierarchy prefix) |
| `user` | User management: CRUD, password policy enforcement, password history |
| `announcement` | Announcements with i18n translations, pinning, read tracking, attachments |
| `audit` | Envers-based audit tables + login-log + async audit writing |
| `workflow` | Workflow engine: definitions, instances, approval steps, delegations |
| `assettransfer` | Asset transfer applications with approval workflows |
| `device` | IoT device registry, templates, telemetry schemas |
| `dispatch` | Work order dispatch/approval/task management |
| `notification` | Push notifications (email, WebSocket STOMP) |
| `platform` | Platform-scoped: tenant management, impersonation, platform announcements |
| `common` | Cross-cutting: `BaseResponse`, `ErrorCode` enum, `SecurityLogger`, virus scanning, file storage, tenant-aware JPQL |
| `config` | `SecurityConfig` (primary filter chain), `WebMvcConfig`, CORS, scheduling |
| `setting` | System/tenant settings (password policy, auth config) |
| `schema` | Device data schemas/templates |

### Multi-Tenancy

- **TenantContext**: ThreadLocal holds current tenant ID. Set by `JwtAuthenticationFilter` from JWT `tenantId` claim. Cleared in finally block.
- **TenantFilter**: JPA-level filter scopes all queries to the current tenant unless `TenantContext.isSystemContext()`.
- **System context**: `@RunInSystemTenantContext` annotation (AOP-driven) or `TenantContext.runInSystemContext()` for cross-tenant operations (scheduled jobs, login flow).
- **Modes**: `multi` (default) requires tenant selection; `single` mode pins to a fixed tenant ID.

### Platform/Tenant Separation (ADR-007)

- JWT tokens carry a `scope` claim: `TENANT`, `PLATFORM`, or `IMPERSONATION`.
- Platform-scoped routes (`/platform/*`) use `PlatformLayout` (dark theme) and require `PLATFORM` scope.
- Tenant-scoped routes (`/*`, `/admin/*`) use `TenantLayout` (green theme).
- `ScopeEnforcementFilter` rejects requests whose path prefix doesn't match the token's scope.
- `SUPER_ADMIN` role gets auto-granted all platform permissions.

### Auth Flow

1. Login → validates credentials (local or LDAP) → issues temporary token
2. Select tenant → exchanges temporary token for access + refresh JWT pair
3. Access token (30min) in memory; refresh token (7 days) in httpOnly secure cookie
4. JWT filter extracts userId, tenantId, roles, permissions, scope → sets Spring Security context
5. Frontend auto-refreshes access token 60s before expiry using the refresh cookie

### Database

- PostgreSQL + PostGIS (Hibernate Spatial for geometry columns)
- Flyway migrations (`backend/src/main/resources/db/migration/`), out-of-order enabled
- Schema: `iot_forgedb` (configured in `application.yml`)
- Hibernate Envers for audit tables on key entities

### Frontend Architecture

- **Router** (`src/router/index.ts`): Guards bootstrap auth, menus, scope enforcement on each navigation. Static routes for admin views; dynamic routes injected by `menuStore` from backend menu tree.
- **Stores** (Pinia): `authStore` (tokens, user info, session restore), `menuStore` (backend-driven navigation), `tenantStore` (tenant selection/switch), `deptStore` (department tree options).
- **API layer** (`src/api/`): Axios instance with interceptors for token injection, auto-refresh, error toast handling. Generated error codes at `src/generated/error-codes.json`.
- **Layouts**: `TenantLayout.vue` (green sidebar, breadcrumbs) and `PlatformLayout.vue` (dark theme, platform admin shell).
- **i18n**: `vue-i18n` with locale files in `src/locales/` (zh-TW, en).

### Response Format

All API responses use `BaseResponse<T>`:
```json
{ "errorCode": "00000", "errorMsg": "操作成功", "timestamp": 1719000000, "body": {...} }
```
Error codes are defined in `ErrorCode` enum. A build-time Maven plugin generates `error-codes.json` for the frontend from this enum.

### Key Config Files

- `backend/src/main/resources/application.yml` — Main config (DS, Redis, JWT, MQTT, file limits, virus scan)
- `frontend/vite.config.ts` — Dev server proxy, CSP headers, esbuild drop console in prod
- `frontend/src/router/index.ts` — Route definitions, navigation guards
- Environment: `.env` support via `spring-dotenv`; secrets via env vars (`DB_PASSWORD`, `JWT_SECRET`, etc.)
