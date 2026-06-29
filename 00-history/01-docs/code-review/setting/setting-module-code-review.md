# Setting 模組 Code Review & Security Review

> 審查日期：2026-05-20
> 審查範圍：`backend/src/main/java/com/taipei/iot/setting/` 全部子套件 + 前端相關檔案

---

## 模組結構總覽

```
setting/
├── controller/
│   └── SystemSettingController.java     # REST API（list / update / idle-timeout）
├── dto/
│   └── SystemSettingDto.java            # 回應 DTO（key + value + description）
├── entity/
│   └── SystemSettingEntity.java         # JPA 實體（tenant_id + setting_key + setting_value）
├── enums/
│   └── SettingKey.java                  # Enum 定義已知 key + 預設值
├── repository/
│   └── SystemSettingRepository.java     # JPA Repository + TenantScopedRepository
└── service/
    └── SystemSettingService.java        # CRUD 業務邏輯
```

**前端檔案**：`api/setting/index.ts`、`views/admin/setting/SystemSettingsView.vue`、`composables/useIdleTimeout.ts`、`components/IdleTimeoutDialog.vue`

**測試覆蓋**：2 個測試檔案（`SystemSettingControllerTest` 9 tests、`SystemSettingServiceTest` 7 tests）

**DB Migration**：`V28__setting__create_tables.sql`（含 UNIQUE(tenant_id, setting_key) 約束）

---

## 總體評價

Setting 模組設計**簡潔且安全**，核心亮點：

- **租戶隔離完整**：Entity 實作 `TenantAware`、Repository 繼承 `TenantScopedRepository`、Hibernate `@Filter` 自動注入
- **權限控管明確**：讀取需 `SYSTEM_SETTINGS_VIEW`、寫入需 `SYSTEM_SETTINGS_MANAGE`、idle-timeout 讀取開放給所有認證使用者
- **Controller 層驗證**：`@Validated` + `@Min(1) @Max(480)` 限制 idle-timeout 範圍、`@NotBlank` 防空值
- **前端 Idle Timeout 設計精巧**：BroadcastChannel 跨 Tab 同步、throttle 防過度觸發、visibility change 處理
- **DB 約束**：`UNIQUE(tenant_id, setting_key)` 防重複、FK 到 tenant 表

---

## 安全審查（Security Review）

### ✅ 防護到位的項目

| 防護類別 | 實作方式 | 評估 |
|----------|----------|------|
| 認證 | 所有 API 在 `/v1/auth/` 下，JWT Filter 保護 | ✅ |
| 讀取授權 | `@PreAuthorize("hasAuthority('SYSTEM_SETTINGS_VIEW')")` on GET / | ✅ |
| 寫入授權 | `@PreAuthorize("hasAuthority('SYSTEM_SETTINGS_MANAGE')")` on PUT | ✅ |
| idle-timeout 讀取 | 無 @PreAuthorize → 所有已認證使用者都能查（合理，前端需要） | ✅ |
| 租戶隔離 | `@Filter(tenantFilter)` + `TenantScopedRepository` | ✅ |
| 輸入驗證 | `@Min(1) @Max(480)` on minutes、`@NotBlank` on key/value | ✅ |
| DB 約束 | `UNIQUE(tenant_id, setting_key)` 防重複 key | ✅ |
| 前端 idle logout | BroadcastChannel 跨 Tab 同步登出 | ✅ |
| Server-side idle logout | `idleLogout()` API 呼叫通知後端 | ✅ |

---

### 需要注意的安全問題

#### 1. [中等] `updateSetting()` 通用端點無值域驗證

```java
@PutMapping("/{key}")
@PreAuthorize("hasAuthority('SYSTEM_SETTINGS_MANAGE')")
public BaseResponse<SystemSettingDto> updateSetting(
        @PathVariable @NotBlank String key,
        @RequestParam @NotBlank String value) {
    return BaseResponse.success(settingService.updateSetting(key, value));
}
```

**風險**：
- `idle_timeout_minutes` 可透過通用端點繞過 `@Min(1) @Max(480)` 限制（例如設為 `"0"` 或 `"-1"` 或 `"abc"`）
- 雖然有專門的 `/idle-timeout` 端點有 `@Min/@Max`，但通用 `PUT /{key}` 不受此限制
- 如果未來新增其他 setting key，也缺少對應的值域驗證

**建議**：在 Service 層加入 per-key 的值域驗證：
```java
public SystemSettingDto updateSetting(String key, String value) {
    validateSettingValue(key, value);  // ← 新增
    // ...
}

private void validateSettingValue(String key, String value) {
    if (SettingKey.IDLE_TIMEOUT_MINUTES.getKey().equals(key)) {
        int minutes = Integer.parseInt(value); // 可能 NumberFormatException
        if (minutes < 1 || minutes > 480) throw new BusinessException(ErrorCode.INVALID_PARAMETER);
    }
}
```

#### 2. [中等] `getIdleTimeoutMinutes()` 的 `Integer.parseInt()` 可能拋 NumberFormatException

```java
public int getIdleTimeoutMinutes() {
    return settingRepository.findBySettingKey(SettingKey.IDLE_TIMEOUT_MINUTES.getKey())
            .map(e -> Integer.parseInt(e.getSettingValue()))  // ← 若 DB 中存了非數字
            .orElse(Integer.parseInt(SettingKey.IDLE_TIMEOUT_MINUTES.getDefaultValue()));
}
```

**風險**：如果通過通用 `updateSetting()` 將 `idle_timeout_minutes` 設為非數字字串（如 `"abc"`），所有使用者查詢 idle timeout 時會觸發 500 Internal Server Error。

**建議**：加 try-catch 降級為預設值：
```java
.map(e -> {
    try { return Integer.parseInt(e.getSettingValue()); }
    catch (NumberFormatException ex) { return Integer.parseInt(SettingKey.IDLE_TIMEOUT_MINUTES.getDefaultValue()); }
})
```

#### 3. [低] `value` 參數長度無上限

```java
@RequestParam @NotBlank String value  // 無 @Size 限制
```

**風險**：DB column 為 `VARCHAR(500)`，超過長度會觸發 DB error 而非 400 Bad Request。

**建議**：加 `@Size(max = 500)`。

#### 4. [低] `updateSetting()` 缺少 `@AuditEvent` 稽核

系統設定的變更（特別是安全相關設定如 idle timeout）應留下稽核記錄。

**建議**：在 `updateSetting()` 和 `updateIdleTimeout()` 加 `@AuditEvent` 註解。

#### 5. [低] 前端 `useIdleTimeout` 中 token 過期處理

```typescript
try {
    await idleLogout()
} catch {
    // fire-and-forget: still clear local state
}
authStore.clearAuth()
```

**評估**：idle logout 時 token 可能已過期（導致 401），但程式碼已正確處理（catch 後仍清除本地狀態並跳轉 login）。設計合理。

---

## 程式碼品質審查（Code Review）

### ✅ 優點

#### 1. SettingKey Enum — 集中管理已知 key 和預設值

```java
public enum SettingKey {
    IDLE_TIMEOUT_MINUTES("idle_timeout_minutes", "15"),
    FRONTEND_BASE_URL("frontend_base_url", "http://localhost:5173");

    private final String key;
    private final String defaultValue;
}
```

- 所有已知 key 集中定義，避免 magic string 散落
- 預設值隨 enum 定義，查詢找不到時有 fallback

#### 2. 前端 `useIdleTimeout` — 全方位 Idle 偵測

```typescript
const EVENTS = ['mousemove', 'mousedown', 'keydown', 'scroll', 'touchstart'] as const
```

- **多種事件偵測**：滑鼠、鍵盤、觸控、捲動
- **30 秒 throttle**：避免每次 mousemove 都重置
- **BroadcastChannel 跨 Tab 同步**：一個 Tab 操作，所有 Tab 重置計時器
- **2 分鐘預警 Dialog**：使用者有機會續用
- **visibility change 處理**：Tab 重新可見時立即檢查
- **idle_logout 跨 Tab 廣播**：一個 Tab 超時，所有 Tab 同步登出

#### 3. Controller 層 `@Validated` + 精確約束

```java
@PutMapping("/idle-timeout")
@PreAuthorize("hasAuthority('SYSTEM_SETTINGS_MANAGE')")
public BaseResponse<Integer> updateIdleTimeout(
        @RequestParam @Min(1) @Max(480) int minutes) {
```

- Idle timeout 有嚴格範圍限制（1-480 分鐘）
- `@Validated` 在 class 層啟用，所有參數驗證生效

#### 4. DB 設計 — UNIQUE 約束確保一致性

```sql
UNIQUE (tenant_id, setting_key)
```

- 每個租戶每個 key 只能有一筆 → 不會出現重複設定
- Seed 初始值透過 `SELECT ... FROM tenant` 確保所有租戶都有

#### 5. 測試覆蓋完整

- **Controller**：9 個測試（auth/authz/happy path 全覆蓋）
- **Service**：7 個測試（found/notFound/default fallback）
- 測試正確驗證 403（無權限）、401（無 token）、200（正常）

#### 6. 前端 UI — 極簡但完整

- Table 顯示所有設定
- Edit Dialog 修改值
- 無 v-html → 無 XSS
- i18n 三語系支援

---

### 需要改進的問題

#### 6. [中等] 通用 `updateSetting()` 和專用 `updateIdleTimeout()` 並存造成繞過

見安全審查 #1。通用端點缺少 per-key 驗證，可繞過專用端點的 `@Min/@Max`。

#### 7. [低] Service 層 `updateSetting()` 手動設 `updatedAt`

```java
entity.setSettingValue(value);
entity.setUpdatedAt(LocalDateTime.now());
settingRepository.save(entity);
```

**問題**：Entity 已有 `@LastModifiedDate`（JPA Auditing 自動填入），手動設 `updatedAt` 是冗餘的。

**建議**：移除 `entity.setUpdatedAt(LocalDateTime.now())`，讓 JPA Auditing 自動處理。

#### 8. [低] `updateSetting()` 的 `IllegalStateException` 未被 GlobalExceptionHandler 處理

```java
.orElseThrow(() -> new IllegalStateException("Setting not found: " + key));
```

**問題**：`IllegalStateException` 通常不在 `GlobalExceptionHandler` 的 catch 範圍內，可能回傳 500 而非 404。

**建議**：改用 `BusinessException(ErrorCode.SETTING_NOT_FOUND)` 或類似的業務異常。

#### 9. [低] 前端 edit dialog 未限制 value 長度

```vue
<el-input v-model="editValue" />
```

**問題**：使用者可輸入超長字串，送出時後端 DB 會拋 error（非 400）。

**建議**：加 `maxlength="500"` 屬性。

---

## 架構總評

| 維度 | 評分 | 說明 |
|------|------|------|
| 安全性 | **8.5/10** | 租戶隔離完整、權限控管明確、idle-timeout 有 min/max 限制。扣分：通用端點可繞過值域驗證、parseInt 未防呆。 |
| 正確性 | **8.5/10** | CRUD 邏輯正確、UNIQUE 約束防重複。扣分：NumberFormatException 未處理、updatedAt 手動設定冗餘。 |
| 效能 | **9.5/10** | 單一 key-value 查詢效率高、前端 throttle 避免過度請求。模組簡單，無效能瓶頸。 |
| 可維護性 | **9/10** | SettingKey enum 集中管理、分層清晰、測試完整。 |
| 可測試性 | **9/10** | 16 個測試覆蓋 Controller + Service、含 auth/authz 場景。 |

---

## 建議優先級摘要

| 優先級 | 項目 | 類型 | 狀態 |
|--------|------|------|------|
| **P2** | `updateSetting()` 通用端點加 per-key 值域驗證（防繞過 idle-timeout 限制） | Security | ✅ 已修正 |
| **P3** | `getIdleTimeoutMinutes()` 的 parseInt 加 try-catch fallback | Reliability | ✅ 已修正 |
| **P4** | `value` 參數加 `@Size(max=500)` | Security / Validation | ✅ 已修正 |
| **P4** | `updateSetting()` / `updateIdleTimeout()` 加 `@AuditEvent` | Observability | ✅ 已修正 |
| **P5** | 移除手動 `setUpdatedAt`（JPA Auditing 自動處理） | Maintainability | ✅ 已修正 |
| **P5** | `IllegalStateException` 改用 `BusinessException` | Correctness | ✅ 已修正 |
| **P5** | 前端 edit dialog 加 `maxlength="500"` | UX | ✅ 已修正 |
