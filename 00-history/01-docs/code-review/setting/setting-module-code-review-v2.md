# Setting 模組 Code Review & Security Review v2

> 本文件為 [setting-module-code-review.md](setting-module-code-review.md) 的後續複查與擴充。  
> 重點：(1) 驗證 v1 全部 7 項議題確實已落實於程式碼 (2) 補上 v1 漏看的新問題 (3) 提出值得優化的功能建議。  
> 複查日期：2026-05-27。  
> 審查範圍：`backend/src/main/java/com/taipei/iot/setting/` 全部 controller / service / entity / repository / enum / dto + 2 個測試類；跨模組整合（`TenantScopedRepository`、`TenantFilterAspect`、`AuditEventType`、`ErrorCode`、`SecurityConfig`）；前端 `views/admin/setting/SystemSettingsView.vue`、`composables/useIdleTimeout.ts`、`api/setting/index.ts`、`components/IdleTimeoutDialog.vue`；DB migration `V28` 與 `V54`。

---

## 一、整體評價

| 維度       | v1 評分 | v2 修復前 | v2 修復後 | 變化 |
| ---------- | ------- | ---------- | ---------- | ---- |
| 安全性     | 8.5/10  | 8.0/10 | **9.0/10** | ⬆ 1.0 — N-1（key 白名單 + per-key 驗證）✅ 已修復 |
| 正確性     | 8.5/10  | 7.5/10 | **9.0/10** | ⬆ 1.5 — N-2（新租戶 seed）✅ + N-3（驗證測試補齊，由 N-1 涵蓋）✅ |
| 效能       | 9.5/10  | 9.5/10 | **9.5/10** | ↔ 模組單純，無熱點問題 |
| 可維護性   | 9.0/10  | 8.0/10 | **8.0/10** | ⬇ 1.0 — 驗證邏輯硬編 if-else（N-6）、`getSetting` 死碼（N-5）|
| 可觀測性   | 8.0/10  | 8.0/10 | **8.0/10** | ↔ `@AuditEvent` 已加，但缺 before/after diff |
| **總分**   | **8.7/10** | **8.2/10** | **8.2/10** | ⬇ 0.5 |

> v2 評分下滑並非 v1 修補回歸，而是本輪更深入審查後發現 v1 漏看的議題（未知 key 寫入、新租戶 seed 缺口、驗證測試缺口）。**v1 全部 7 項修補皆於程式碼中確認落實**，模組整體品質仍屬中上。

**結論**：本輪建議優先處理 **N-2（key 白名單）/ N-3（新租戶 seed）/ N-1（補測試）**，三者皆為單次修補即可大幅提升模組成熟度；其餘 M / L 級項目為可逐步排入的可維護性改善。

---

## 二、v1 議題複查

> v1 共識別 9 個問題（編號 1-9），其中 #1-#7 為列入優先級摘要的可動作項目，#5 與 #6 為觀察性註記（前端 token 過期處理已正確、無需動作）。下表逐項驗證。

| # | v1 議題 | 狀態 | 證據 |
| - | ------- | ---- | ---- |
| 1 | `updateSetting()` 通用端點缺 per-key 值域驗證（可繞過 idle-timeout `@Min/@Max`）| ✅ 已驗證 | [SystemSettingService.java](../../../backend/src/main/java/com/taipei/iot/setting/service/SystemSettingService.java) 新增 `validateSettingValue(key, value)`，對 `IDLE_TIMEOUT_MINUTES` 驗證 1-480 並丟 `SETTING_INVALID_VALUE` |
| 2 | `getIdleTimeoutMinutes()` 的 `Integer.parseInt()` 可拋 `NumberFormatException` 致 500 | ✅ 已驗證 | `parseIntOrDefault()` 抽出為 helper，catch `NumberFormatException` 並 log warn 後 fallback 預設值 |
| 3 | `value` 參數無長度上限 | ✅ 已驗證 | [SystemSettingController.java](../../../backend/src/main/java/com/taipei/iot/setting/controller/SystemSettingController.java) `@RequestParam @NotBlank @Size(max = 500) String value` |
| 4 | `updateSetting()` / `updateIdleTimeout()` 缺 `@AuditEvent` | ✅ 已驗證 | 兩個端點皆有 `@AuditEvent(AuditEventType.UPDATE_SETTING)` |
| 5 | 前端 idle logout token 過期處理（v1 為觀察性註記 — 設計合理）| ✅ 維持 | [useIdleTimeout.ts](../../../frontend/src/composables/useIdleTimeout.ts) `try { await idleLogout() } catch {}` 後仍清 local state + 跳 login，行為不變 |
| 6 | 通用 endpoint + 專用 endpoint 並存造成繞過（同 #1 補強）| ✅ 已驗證 | 隨 #1 一併由 `validateSettingValue` 處理；通用端點亦觸發驗證 |
| 7 | Service 層手動 `setUpdatedAt`（與 `@LastModifiedDate` 冗餘）| ✅ 已驗證 | [SystemSettingEntity.java](../../../backend/src/main/java/com/taipei/iot/setting/entity/SystemSettingEntity.java) 保留 `@LastModifiedDate`；Service 已移除手動 `entity.setUpdatedAt(LocalDateTime.now())` |
| 8 | `IllegalStateException` 未被 `GlobalExceptionHandler` 處理 | ✅ 已驗證 | Service 所有 `.orElseThrow(...)` 統一改為 `BusinessException(ErrorCode.SETTING_NOT_FOUND)` |
| 9 | 前端 edit dialog 缺 `maxlength`（送出超長字串）| ✅ 已驗證 | [SystemSettingsView.vue](../../../frontend/src/views/admin/setting/SystemSettingsView.vue) `<el-input v-model="editValue" maxlength="500" show-word-limit />` |

> **小結**：v1 全部 9 項議題，**9/9 ✅ 已於程式碼層級確認**；其中 1 項屬觀察性註記（#5）維持原設計。無回歸。

---

## 三、本輪新發現問題

### 🔴 高風險

#### N-1. 通用 `PUT /{key}` 端點未限制 key 白名單 — 可寫入任意 key ✅ 已修復

- **檔案**：[SystemSettingService.java](../../../backend/src/main/java/com/taipei/iot/setting/service/SystemSettingService.java)
- **問題**：`validateSettingValue()` 僅對 `idle_timeout_minutes` 驗值，未知 key 直接通過；其他已知 key（`audit_retention_days`、`frontend_base_url`、`notification_retention_days`）完全無驗證。
- **修復方式**：重寫 `validateSettingValue()`：
  1. 第一步以 `SettingKey` enum 做白名單檢查，未知 key 拋 `SETTING_INVALID_VALUE("Unknown setting key: ...")`。
  2. 每個已知 key 走 switch 分支呼叫對應驗證：`IDLE_TIMEOUT_MINUTES` → `validateIntRange(1, 480)`；`AUDIT_RETENTION_DAYS` / `NOTIFICATION_RETENTION_DAYS` → `validateIntRange(1, 3650)`；`FRONTEND_BASE_URL` → `validateUrl()`（`java.net.URL` 解析）。
- **測試**：`SystemSettingValidationTest`（27 tests）— 涵蓋未知 key 拒絕（4 值）、idle_timeout 非數字/超出範圍/合法、audit_retention 非數字/超出範圍/合法、notification_retention 超出範圍/合法、frontend_base_url 非法 URL/合法 URL。
- **優先級**：🔴 高 → ✅ 已修復

---

#### N-2. 新建立的場域不會自動 seed 全部 settings — `updateSetting` 對新場域可能 404 ✅ 已修復

- **檔案**：[TenantAdminService.java](../../../backend/src/main/java/com/taipei/iot/tenant/service/TenantAdminService.java)、[SettingKey.java](../../../backend/src/main/java/com/taipei/iot/setting/enums/SettingKey.java)
- **問題**：V28 / V54 等 migration 僅為 migration 執行當下已存在的 tenant seed settings。tenant 建立流程未對新 tenant 同步寫入 settings，導致新場域管理員無法調整任何設定（觸發 `SETTING_NOT_FOUND`）。
- **修復方式**：採方案 A —— `TenantAdminService.createTenant()` 在保存 tenant 後呼叫 `seedDefaultSettings(tenantId)`，foreach `SettingKey.values()` 寫入預設值 row（含 description）。同時 `SettingKey` enum 新增 `description` 欄位，確保 seed 時 description 與 migration 一致。
- **測試**：`TenantAdminServiceSeedSettingsTest`（5 tests）— 驗證 seed 數量 = SettingKey.values().length、tenantId 正確、各 key 預設值 / description 正確、與 admin 建立流程共存。
- **優先級**：🔴 高 → ✅ 已修復

---

### 🟠 中風險

#### N-3. ✅ 驗證錯誤路徑（`SETTING_INVALID_VALUE`）完全沒有測試

- **狀態**：✅ 已修復（與 N-1 連動）
- **修復內容**：
  1. **Service 層**：N-1 建立的 `SystemSettingValidationTest`（27 tests）已完整覆蓋 unknown key / 非數字 / 超出範圍 / URL 格式錯誤等路徑
  2. **Controller 層**：`SystemSettingControllerTest` 新增 4 個驗證錯誤路徑測試：
     - `updateSetting_valueOver500Chars_shouldReturn400` — `@Size(max=500)` 攔截
     - `updateSetting_blankValue_shouldReturn400` — `@NotBlank` 攔截
     - `updateSetting_invalidValue_serviceThrows_shouldReturnError` — Service 拋 `BusinessException` 回傳 400
     - `updateSetting_unknownKey_serviceThrows_shouldReturnError` — 未知 key 回傳 400
  3. 同時修復 `SystemSettingControllerTest` 因 `CorsProperties` 缺失導致的 Context 啟動失敗（新增 `@Import(CorsProperties.class)` + `@TestPropertySource`）
- **測試品質提升**：7.0 → 9.0

---

#### N-4. ✅ `SystemSettingEntity` 缺 `@Version` — 多人同時改設定會 Lost Update

- **檔案**：[SystemSettingEntity.java](../../../backend/src/main/java/com/taipei/iot/setting/entity/SystemSettingEntity.java)、[SystemSettingService.java](../../../backend/src/main/java/com/taipei/iot/setting/service/SystemSettingService.java)
- **狀態**：✅ 已修復
- **修復內容**：
  1. `SystemSettingEntity` 新增 `@Version private Integer version;` 欄位，Hibernate 自動進行 optimistic locking。
  2. `SystemSettingService.updateSetting()` 及 `updateIdleTimeoutMinutes()` 改用 `saveAndFlush()`，catch `OptimisticLockingFailureException` 轉為 `BusinessException(SETTING_VERSION_CONFLICT)`（HTTP 409）。
  3. `ErrorCode` 新增 `SETTING_VERSION_CONFLICT("56010", 409, "設定已被他人修改，請重新載入")`。
  4. DB migration `V57__setting__add_version_column.sql`：`ALTER TABLE system_settings ADD COLUMN version INTEGER NOT NULL DEFAULT 0`。
- **測試**：
  - `SystemSettingServiceTest`：新增 2 個測試（`updateSetting_versionConflict_shouldThrowSettingVersionConflict`、`updateIdleTimeoutMinutes_versionConflict_shouldThrowSettingVersionConflict`）
  - `SystemSettingEntityVersionTest`：3 個測試（反射驗證 `@Version` 註解、builder 設值、setter 設值）
- **正確性提升**：7.5 → 9.0

---

### 🟡 低風險 / 建議

#### N-5. ✅ `SystemSettingService.getSetting(String)` 為死碼

- **檔案**：[SystemSettingService.java](../../../backend/src/main/java/com/taipei/iot/setting/service/SystemSettingService.java)
- **狀態**：✅ 已修復（選項 2 — 刪除）
- **修復內容**：移除 `public String getSetting(String key)` 方法。經 grep 驗證整個 codebase 無任何呼叫者。
- **測試**：`SystemSettingServiceTest.getSetting_singleKeyMethod_shouldNotExist` — 反射驗證該方法已不存在，防止未來誤加回。
- **可維護性提升**：減少 API 表面積，消除混淆。

#### N-6. ✅ `validateSettingValue` 採 flat if-else，新增 key 易遺漏驗證

- **檔案**：[SettingKey.java](../../../backend/src/main/java/com/taipei/iot/setting/enums/SettingKey.java)、[SettingValidator.java](../../../backend/src/main/java/com/taipei/iot/setting/enums/SettingValidator.java)、[SystemSettingService.java](../../../backend/src/main/java/com/taipei/iot/setting/service/SystemSettingService.java)
- **狀態**：✅ 已修復
- **修復內容**：
  1. 新建 `SettingValidator` functional interface，提供工廠方法 `intRange(min, max)` 和 `url()`。
  2. `SettingKey` enum 新增第 4 個建構參數 `SettingValidator validator`，每個 key 定義時即綁定驗證器（編譯時強制完整）。
  3. `SystemSettingService.validateSettingValue()` 簡化為 `known.getValidator().validate(key, value)`，移除 switch 和 private helper methods。
- **測試**：`SettingValidatorTest`（16 個測試）：
  - `everySettingKey_shouldHaveNonNullValidator` — 遍歷所有 enum 值，確認 validator 不為 null
  - `intRange` 系列（6 個）：邊界值、超範圍、非數字
  - `url` 系列（4 個）：有效 http/https、無效 URL、空字串
  - enum 委派測試（2 個）：驗證 SettingKey 正確委派給 validator
- **可維護性提升**：8.0 → 9.5（新增 key 時 validator 為必填構造參數，無法遺漏）

#### N-7. 前端設定描述未走 i18n — 多語系下仍顯示中文 ✅

- **檔案**：[SystemSettingsView.vue](../../../frontend/src/views/admin/setting/SystemSettingsView.vue)
- **問題**：description 直接取 DB `system_settings.description` 欄位（V28 / V54 寫入時為中文）。切到英文 UI 時其他欄位翻譯，但 description 仍為中文。
- **修復方式**：
  1. 三語系 locale 檔（zh-TW / en / zh-CN）各新增 `setting.keys.<settingKey>` 共 4 個 key。
  2. `SystemSettingsView.vue` 表格欄位改為 `t(\`setting.keys.\${row.settingKey}\`, row.description)`，以 DB description 為 fallback。
  3. 編輯 Dialog 同步套用相同 i18n pattern。
- **測試**：`SystemSettingsView.test.ts`（5 tests）— 驗證三語系 locale 皆含所有 key、template 使用 i18n 而非 raw description。

#### N-8. `useIdleTimeout.ts` 對非 401 錯誤靜默吞掉 ✅

- **檔案**：[useIdleTimeout.ts](../../../frontend/src/composables/useIdleTimeout.ts)
- **問題**：`getIdleTimeout()` 失敗時走 fallback 預設值是正確的 graceful degradation，但**任何錯誤都不記錄**，運維難以察覺後端 500 / 網路問題。
- **修復方式**：catch 區塊改為具名參數 `err`，檢查 `response.status`：若非 401 / 403 則 `console.warn('[useIdleTimeout] getIdleTimeout failed, using default', err)`；仍保留 fallback 預設值行為。
- **測試**：`useIdleTimeoutErrorLogging.test.ts`（4 tests）— 驗證含 console.warn、檢查 status、保留 fallback、非空 catch。

#### N-9. `SecurityConfig` 對 `/v1/auth/idle-logout` 未明列 ✅

- **檔案**：[SecurityConfig.java](../../../backend/src/main/java/com/taipei/iot/config/SecurityConfig.java)
- **問題**：該端點目前靠 `.anyRequest().authenticated()` catch-all 保護，**功能上正確**但可讀性較差；安全審計時須一個個追路由列表才能確認。
- **修復方式**：在 `authorizeHttpRequests` 鏈中 USER self-service 區塊後新增 `.requestMatchers(HttpMethod.POST, "/v1/auth/idle-logout").authenticated()`，配以 `// IDLE-LOGOUT: explicit (authenticated)` 註解。
- **測試**：`SecurityConfigIdleLogoutRouteTest.java`（3 tests）— 驗證明確列出路由、指定 POST method、要求 authenticated。

---

## 四、安全性總結

| 面向 | v1 評估 | v2 評估 | 變化原因 |
|------|---------|---------|----------|
| 認證 / 授權 | ✅ | ✅ | `@PreAuthorize` 全到位 |
| 多租戶隔離 | ✅ | ✅ | `TenantScopedRepository` + `TenantFilterAspect` 自動覆蓋所有 query 路徑（已驗證 `findBySettingKey` 也受 filter 保護）|
| 輸入驗證 | ⚠ 部分 | ⚠ **退步發現** | v1 #1 修補只覆蓋 `idle_timeout_minutes`；**N-1 揭露其他 key 完全無驗證 + key 本身未限白名單** |
| Audit 追蹤 | ✅ | ✅ | `@AuditEvent` 已加 |
| 競態條件 | — | ⚠ 低 | 新指出 N-4 lost update |
| Configuration tampering | ✅（誤判）| ⚠ **退步發現** | N-1 對 `frontend_base_url` 為實質 phishing 跳板潛在風險 |

---

## 五、值得優化的功能建議

| # | 功能 | 價值 | 概要 |
| - | ---- | ---- | ---- |
| F-1 | **Validator Strategy Registry**（呼應 N-6）| 維護 ★★★ | 把 validator 收進 `SettingKey` enum 或獨立 `SettingValidatorRegistry` bean，新增 key 強制掛 validator |
| F-2 | **設定分類 / Categories** | UX ★★ | `SettingKey` 加 `SettingCategory`（SECURITY / AUDIT / UI / NOTIFICATION），前端依分類顯示 tab 或 collapsible group |
| F-3 | **敏感設定加密**（at-rest）| 安全 ★★★ | 未來若放 SMTP password / API key 等，`SettingKey` 加 `sensitive=true` 旗標；`@Convert(EncryptedStringConverter.class)` 自動加解密；列表 API 對敏感欄位回傳遮罩 |
| F-4 | **設定變更歷史 / Diff View** | 稽核 ★★★ | 新表 `setting_change_log(tenantId, key, oldValue, newValue, changedBy, changedAt)`；每次 `updateSetting` 寫一筆；前端 timeline 顯示。比通用 `audit_log` 更聚焦 |
| F-5 | **設定匯入 / 匯出** | 維運 ★★ | `GET /export?format=json`、`POST /import` 多 part 檔；協助新場域套用樣板、跨環境同步 |
| F-6 | **租戶級預設覆寫** | 彈性 ★ | 不同租戶可有不同 default（例如醫療類較嚴 idle timeout）；`tenant_setting_defaults` 表，新場域 seed 時優先採用 |
| F-7 | **設定 deprecation 流程** | 維運 ★ | `SettingKey.deprecated(replacementKey)` 標記；啟動時自動遷移舊 key → 新 key（含值轉換 callback）|
| F-8 | **設定即時推播** | UX ★★ | 改 idle timeout 後透過 SSE / WebSocket / BroadcastChannel 通知所有在線使用者，免重新登入即生效 |

---

## 六、修復路線圖建議

### Sprint 1 — 補強驗證與 onboarding
1. **N-1** — `validateSettingValue` 加 key 白名單 + 補 `audit_retention_days` / `frontend_base_url` 驗證規則（順便落實 F-1 / N-6 strategy pattern）。
2. **N-2** — tenant 建立流程同步 seed 全部 `SettingKey` 預設值（**方案 A**）；`updateSetting` 額外採 upsert 兜底（**方案 B**）。
3. **N-3** — 補 3-4 個驗證錯誤路徑測試。

### Sprint 2 — 健壯性
4. **N-4** — `SystemSettingEntity` 加 `@Version` + Controller 處理 409。
5. **N-5** — 移除死碼 `getSetting(String)`。
6. **N-9** — `SecurityConfig` 明列 `/v1/auth/idle-logout`。

### Sprint 3+ — 體驗與架構
7. **N-7 + N-8** — 前端 i18n 與錯誤觀察性。
8. F-2 設定分類、F-4 設定變更歷史、F-5 匯入匯出。
9. F-3 敏感設定加密（等實際有需求時再做）。

---

## 七、附錄：本次複查涵蓋的檔案

### Backend
- [SystemSettingController.java](../../../backend/src/main/java/com/taipei/iot/setting/controller/SystemSettingController.java)
- [SystemSettingService.java](../../../backend/src/main/java/com/taipei/iot/setting/service/SystemSettingService.java)
- [SystemSettingEntity.java](../../../backend/src/main/java/com/taipei/iot/setting/entity/SystemSettingEntity.java)
- [SystemSettingDto.java](../../../backend/src/main/java/com/taipei/iot/setting/dto/SystemSettingDto.java)
- [SystemSettingRepository.java](../../../backend/src/main/java/com/taipei/iot/setting/repository/SystemSettingRepository.java)
- [SettingKey.java](../../../backend/src/main/java/com/taipei/iot/setting/enums/SettingKey.java)

### 測試
- [SystemSettingControllerTest.java](../../../backend/src/test/java/com/taipei/iot/setting/controller/SystemSettingControllerTest.java)（9 tests）
- [SystemSettingServiceTest.java](../../../backend/src/test/java/com/taipei/iot/setting/service/SystemSettingServiceTest.java)（7 tests）

### DB Migration
- [V28__setting__create_tables.sql](../../../backend/src/main/resources/db/migration/V28__setting__create_tables.sql)
- `V54__audit__add_retention_days_setting.sql`（如已建立）

### 跨模組整合
- `common/multitenancy/TenantAware.java`、`TenantScopedRepository.java`、`TenantFilterAspect.java`
- [SecurityConfig.java](../../../backend/src/main/java/com/taipei/iot/config/SecurityConfig.java)
- [ErrorCode.java](../../../backend/src/main/java/com/taipei/iot/common/enums/ErrorCode.java)（`SETTING_NOT_FOUND` / `SETTING_INVALID_VALUE` 已存在）
- `audit/enums/AuditEventType.java`（`UPDATE_SETTING` 已存在）

### 前端
- [SystemSettingsView.vue](../../../frontend/src/views/admin/setting/SystemSettingsView.vue)
- [useIdleTimeout.ts](../../../frontend/src/composables/useIdleTimeout.ts)
- [IdleTimeoutDialog.vue](../../../frontend/src/components/IdleTimeoutDialog.vue)
- [api/setting/index.ts](../../../frontend/src/api/setting/index.ts)
