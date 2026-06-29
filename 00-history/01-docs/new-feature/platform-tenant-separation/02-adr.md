# ADR — Platform / Tenant 職責分離

> 文件版本：v1.0
> 產出日期：2026-05-31
> 狀態：✅ 已決議，待進入實作階段（`03-phased-plan.md`）
> 前置文件：[01-inventory.md](01-inventory.md)

---

## 背景

目前 `super_admin` 透過 `AuthServiceImpl.resolvePermissions()` 的程式碼旁路自動取得**所有權限（含 TENANT scope）**，導致：

- 平台管理員與租戶管理員共用同一套 UI / 選單，職責混淆
- super_admin 可任意操作租戶內部資料（部門、使用者、公告等），無稽核責任邊界
- 與多租戶 SaaS 業界慣例（GDPR / ISO 27001）不符

本 ADR 記錄 8 個關鍵設計決策，作為後續分階段實作的依據。

---

## 決策核心原則

| 角色 | 職責 |
|------|------|
| **Super Admin（PLATFORM scope）** | 只管租戶 CRUD、訂閱、平台設定、全域稽核、系統健康 |
| **Tenant Admin / User（TENANT scope）** | 只管自己租戶內的資料 |
| **例外通道** | Impersonation — 受控、可稽核、有時限 |

---

## 決策清單

### ADR-001：super_admin 進入 tenant API 採「後端強制禁止」

**決議**：✅ 強制（不是 UI-only 隱藏）

**作法**：
- 移除 `AuthServiceImpl.resolvePermissions()` 中的 super_admin 旁路
- super_admin 的 JWT 只包含 `PLATFORM_*` 權限
- 同步移除 `MenuService.getMyMenus()` 的 super_admin 旁路
- 新增 SecurityFilter：依 JWT `scope` claim + URL path prefix 做 path-based 縱深防禦

**影響**：
- super_admin 呼叫任何 `/v1/auth/**`（非 noauth、非 platform）一律 403
- 唯一例外：透過 impersonation 機制取得的 tenant-scope token

**理由**：UI 隱藏無法防止 API 直呼；既然要做就要做到後端強制。

---

### ADR-002：Impersonation 機制納入 Phase 1

**決議**：✅ 與 ADR-001 同步上線

**設計**：

| 元素 | 規格 |
|------|------|
| API | `POST /v1/platform/impersonations`、`DELETE /v1/platform/impersonations/{id}`、`GET /v1/platform/impersonations` |
| 必填欄位 | `tenantId`、`reason`（稽核用）、`durationMinutes`（上限 60 分鐘） |
| 回傳 | 短期 tenant-scope token（scope=IMPERSONATION） |
| 通知 | 自動寄信給該租戶所有 `ROLE_ADMIN` |
| Audit | `audit_log` 新增 `impersonated_by_user_id` 與 `impersonation_session_id` 欄位 |
| 前端 | tenant console 頂部紅色 banner：「您正以平台管理員身分操作 XXX 租戶，剩餘 XX 分鐘 [結束]」 |

**理由**：避免「Q1 強制禁止後 super_admin 完全無法協助租戶」的窘境；既然要做就一次做齊，避免後門被濫用。

---

### ADR-003：API 路徑命名採「輕量重整」(B)

**決議**：✅ Platform 統一搬到 `/v1/platform/**`；tenant 保留 `/v1/auth/**`

**規則**：

| Prefix | 用途 | scope 要求 |
|--------|------|-----------|
| `/v1/noauth/**` | 公開（登入、captcha、忘記密碼、password policy describe） | 無 |
| `/v1/platform/**` | super_admin 專用 | scope=PLATFORM |
| `/v1/auth/**` | tenant 內部所有功能（含個人 profile、notification） | scope=TENANT 或 IMPERSONATION |

**路徑搬遷對照**（共 11 個 endpoint）：

| 現況 | 改為 |
|------|------|
| `/v1/admin/tenants/**`（4） | `/v1/platform/tenants/**` |
| `/v1/auth/tenant-auth-config`（4） | `/v1/platform/tenants/{tenantId}/auth-config`（見 ADR-006） |
| `/v1/auth/users/{id}/tenant-roles/**`（3） | `/v1/platform/users/{id}/tenant-roles/**` |
| `/v1/platform/password-policy`（2） | （已正確，保留） |

**過渡**：舊路徑保留 6 個月 + 加 `Deprecation` HTTP header；前端逐步遷移。

**理由**：完全重整（70+ tenant endpoints 全改路徑）CP 值低；保留 `/v1/auth/**` 為 tenant 預設可避免破壞性。未來若需升級為「完全重整」，由本決議無痛過渡。

---

### ADR-004：前端架構採「同 SPA + 雙 Layout」(A)

**決議**：✅ 同一個 Vue 專案；依路徑套用不同 Layout

**路由結構**：

```
公開 (NoAuthLayout)
  /login, /select-tenant, /forgot-password, /reset-password, /force-change-password

平台 (PlatformLayout) — scope=PLATFORM 才可進入
  /platform/tenants
  /platform/tenants/:id/auth-config
  /platform/password-policy
  /platform/users/cross-tenant
  /platform/impersonations

租戶 (TenantLayout) — scope=TENANT 或 IMPERSONATION
  /                       Dashboard
  /admin/users, /admin/dept, /admin/roles, /admin/menus, ...
  /profile, /change-password, /my/activity, /announcements
```

**登入後分流**：
- scope=PLATFORM → 導向 `/platform/tenants`
- scope=TENANT → 導向 `/`
- scope=IMPERSONATION → 導向 `/?impersonating=1` 並顯示紅色 banner

**Layout 視覺差異**：
- **PlatformLayout**：深色（紫/灰），Logo 加「Platform」標示，sidebar 只列平台選單
- **TenantLayout**：維持綠色系，頂部顯示當前 tenant 名稱
- **Impersonation 模式**：TenantLayout 頂端固定紅色 banner

**理由**：成本最小、共用元件最多；若未來真要拆兩個 app，由此架構過渡最容易。

---

### ADR-005：`ROLE_DEPT_ADMIN` 補完 seed (B)

**決議**：✅ 明確定義為「部門管理員」角色

**權限集合**（待 Phase 1 實作時微調）：
- `USER_LIST` / `USER_UPDATE`（搭配 `dataScope=DEPT` 限制範圍）
- `DEPT_LIST`
- `AUDIT_LIST`（自己部門範圍）

**預設設定**：
- `dataScope=DEPT`
- 加入 i18n 顯示名稱（zh-TW / en）
- 「角色管理」UI 確認顯示

**理由**：保留「部門主管」這個常見實務角色，使用既有 dataScope 機制即可達成範圍限制。

---

### ADR-006：`TenantAuthConfigController` 搬到巢狀路徑 (A)

**決議**：✅ 改為 `/v1/platform/tenants/{tenantId}/auth-config`

**新路徑**：

| 現況 | 改為 |
|------|------|
| GET `/v1/auth/tenant-auth-config` | GET `/v1/platform/tenants/{tenantId}/auth-config` |
| PUT `/v1/auth/tenant-auth-config` | PUT `/v1/platform/tenants/{tenantId}/auth-config` |
| DELETE `/v1/auth/tenant-auth-config` | DELETE `/v1/platform/tenants/{tenantId}/auth-config` |
| POST `/v1/auth/tenant-auth-config/test-connection` | POST `/v1/platform/tenants/{tenantId}/auth-config/test-connection` |

**理由**：super_admin 沒有自身 tenantId，目標租戶必須顯式帶在 URL；符合業界慣例（GitHub `/orgs/{id}/...`、AWS `/accounts/{id}/...`）。未來其他 platform-level 設定（訂閱、配額、限流）可套相同模式。

---

### ADR-007：JWT 加顯式 `scope` claim (A)

**決議**：✅ 新增 `scope` claim

**Claims 設計**：

```jsonc
{
  "uid": "user-super-001",
  "scope": "PLATFORM",          // ← 新增：PLATFORM | TENANT | IMPERSONATION
  "tenantId": null,             // PLATFORM=null；TENANT/IMPERSONATION 必有值
  "roles": ["ROLE_SUPER_ADMIN"],
  "permissions": ["PLATFORM_TENANT_MANAGE", ...],
  "impersonation": {            // ← 新增：僅 scope=IMPERSONATION 時存在
    "originalUserId": "user-super-001",
    "sessionId": "imp-abc123",
    "expiresAt": 1717200000
  }
}
```

**Filter 規則**：

| Path | 必要 scope |
|------|-----------|
| `/v1/platform/**` | PLATFORM |
| `/v1/auth/**` | TENANT 或 IMPERSONATION |
| `/v1/noauth/**` | 不檢查 |

**過渡相容**：舊 token（無 `scope`）視為 TENANT 並記 warning log；一個 refresh cycle 後全部 token 帶上新 claim。

**理由**：隱含判斷（`tenantId == null`）無法區分 IMPERSONATION 與真正 TENANT user；顯式 claim 讓 filter O(1) 判斷、邏輯可讀、可測試。

---

### ADR-008：補強 `MenuController` 寫操作權限 (A)

**決議**：✅ 加上 `@PreAuthorize`（順便清查同類問題）

**改動**：

```java
@PostMapping
@PreAuthorize("hasAuthority('MENU_CREATE')")
public Result<MenuVo> createMenu(...) { ... }

@PutMapping
@PreAuthorize("hasAuthority('MENU_UPDATE')")
public Result<MenuVo> updateMenu(...) { ... }

@DeleteMapping("/{menuId}")
@PreAuthorize("hasAuthority('MENU_DELETE')")
public Result<Void> deleteMenu(...) { ... }

@PatchMapping("/{menuId}/visible")
@PreAuthorize("hasAuthority('MENU_UPDATE')")
public Result<Void> toggleVisible(...) { ... }
```

**順帶補強**：
- `DeptController.getDeptTree/options/scope-options/getDeptById` 加 `DEPT_LIST`
- `RoleController.listRoles/listAssignableRoles/getRolePermissions` 加 `ROLE_LIST`
- `MenuController.getMenuTree` 加 `MENU_LIST`

**理由**：權限代碼早在 V58 就存在，只是 controller 沒套；一個小改動補多個安全漏洞，CP 值極高。

---

## 決策總表

| # | 議題 | 決議 |
|---|------|------|
| ADR-001 | super_admin 進入 tenant API | **後端強制禁止** |
| ADR-002 | Impersonation 時機 | **Phase 1 就做** |
| ADR-003 | API 路徑命名 | **輕量重整**（Platform → `/v1/platform/**`、Tenant 保留 `/v1/auth/**`） |
| ADR-004 | 前端架構 | **同 SPA + 雙 Layout** |
| ADR-005 | `ROLE_DEPT_ADMIN` | **補完 seed** |
| ADR-006 | `TenantAuthConfigController` 路徑 | **巢狀於 tenant**：`/v1/platform/tenants/{id}/auth-config` |
| ADR-007 | JWT `scope` claim | **新增**（PLATFORM / TENANT / IMPERSONATION） |
| ADR-008 | MenuController 權限 | **補上 @PreAuthorize**（順帶清查 DeptController 等同類問題） |

---

## 風險與緩解

| 風險 | 緩解措施 |
|------|---------|
| super_admin 失去 tenant 權限後無法協助租戶 | ADR-002 Impersonation 同步上線 |
| 路徑搬遷破壞既有 client | ADR-003 舊路徑保留 6 個月 + Deprecation header |
| 舊 JWT 無 `scope` claim 導致 403 | ADR-007 過渡相容（視為 TENANT） |
| 既有 E2E 測試以 super_admin 操作 tenant API | Phase 1 收尾前修改測試帳號為 tenant_admin |
| ROLE_DEPT_ADMIN 補 seed 後既有資料衝突 | 用 `INSERT ... ON CONFLICT DO NOTHING` |
| 補 @PreAuthorize 後某些既有使用者突然 403 | Phase 1 上線前先跑全套 E2E 測試確認權限分配正確 |

---

## 下一步

→ 撰寫 [`03-phased-plan.md`](03-phased-plan.md)，根據本 ADR 規劃 Phase 1–4 的具體工作項目、依賴順序、驗收標準。

**建議 Phase 切分（預覽）**：

| Phase | 範圍 | 對應 ADR |
|-------|------|---------|
| **Phase 1：基礎建設** | JWT scope claim、SecurityFilter、Impersonation API、ROLE_DEPT_ADMIN seed、補 @PreAuthorize | 002 / 005 / 007 / 008 |
| **Phase 2：路徑搬遷** | platform endpoint 改路徑、舊路徑保留 + deprecation | 003 / 006 |
| **Phase 3：強制隔離** | 移除 super_admin 旁路、SecurityFilter 強制檢查 | 001 |
| **Phase 4：前端分流** | 雙 layout、登入後分流、Impersonation banner | 004 |
| **Phase 5：清理** | 移除舊路徑、移除舊 super_admin 邏輯、補 E2E 測試 | — |
