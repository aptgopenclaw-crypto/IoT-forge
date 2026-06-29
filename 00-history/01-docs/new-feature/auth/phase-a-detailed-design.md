# Phase A 細部設計：Strategy 介面 + Dispatcher + LOCAL 重構

> 版本：v1.0 · 2026-05-27  
> 前置：multi-auth-provider-spec.md §11 Phase A  
> 目標：在不改變現有登入行為的前提下，重構認證流程為可擴充的 Strategy 架構

---

## 1. 概述

Phase A 是多重認證 Provider 的**最小可行架構**。完成後：
- 所有 tenant 仍走 LOCAL 認證（行為不變）
- 後端具備 Provider 擴充點，後續 Phase B/C/E 只需新增 class 即可
- `tenant_auth_config` 表已就位，可透過 API 查詢/設定
- `users` 表已具備 `auth_type` + `external_id` 欄位

---

## 2. 新增 Package 結構

```
com.taipei.iot.auth
├── provider/                       ← 新增
│   ├── AuthType.java               (enum)
│   ├── AuthenticationProvider.java  (Strategy 介面)
│   ├── AuthenticationResult.java   (認證結果 DTO)
│   ├── AuthenticationRequest.java  (認證請求 DTO)
│   ├── AuthenticationDispatcher.java (路由器)
│   └── local/
│       └── LocalAuthProvider.java  (LOCAL 實作)
├── config/                         ← 新增
│   ├── entity/
│   │   └── TenantAuthConfigEntity.java
│   ├── repository/
│   │   └── TenantAuthConfigRepository.java
│   ├── service/
│   │   ├── TenantAuthConfigService.java
│   │   └── impl/
│   │       └── TenantAuthConfigServiceImpl.java
│   ├── controller/
│   │   └── TenantAuthConfigController.java
│   ├── dto/
│   │   ├── TenantAuthConfigRequest.java
│   │   └── TenantAuthConfigResponse.java
│   └── crypto/
│       └── AuthConfigEncryptor.java
```

---

## 3. 核心介面設計

### 3.1 `AuthType` 列舉

```java
package com.taipei.iot.auth.provider;

public enum AuthType {
    LOCAL,   // 帳密 + BCrypt（現有）
    LDAP,    // Phase B
    OIDC,    // Phase C
    SAML;    // Phase E

    public static AuthType fromString(String value) {
        if (value == null) return LOCAL;
        return valueOf(value.toUpperCase());
    }
}
```

### 3.2 `AuthenticationRequest`

Provider 通用的認證輸入：

```java
package com.taipei.iot.auth.provider;

import lombok.*;
import java.util.Map;

@Getter @Builder
public class AuthenticationRequest {
    /** 登入帳號（LOCAL/LDAP: email, OIDC/SAML: null） */
    private String identifier;
    /** 密碼（LOCAL/LDAP 使用，OIDC/SAML 為 null） */
    private String credential;
    /** 目標 tenant（可能 null，e.g. 尚未選定） */
    private String tenantId;
    /** Provider-specific params (e.g. OIDC code, SAML response) */
    private Map<String, String> extra;
}
```

### 3.3 `AuthenticationResult`

Provider 認證成功後回傳：

```java
package com.taipei.iot.auth.provider;

import lombok.*;
import java.util.Map;

@Getter @Builder
public class AuthenticationResult {
    /** 在本地 users 表中的 user_id（如果已存在）；null 表示需 auto-provision */
    private String localUserId;
    /** 外部 IdP 的唯一識別（e.g. LDAP DN, OIDC sub, SAML nameId） */
    private String externalId;
    /** 用於 auto-provision 的使用者資料 */
    private String email;
    private String displayName;
    /** IdP 提供的額外 claims（group membership 等），供 role mapping 使用 */
    private Map<String, Object> claims;
}
```

### 3.4 `AuthenticationProvider` 介面

```java
package com.taipei.iot.auth.provider;

/**
 * Strategy interface for pluggable authentication mechanisms.
 * Each concrete provider handles one {@link AuthType}.
 */
public interface AuthenticationProvider {

    /** Which auth type this provider handles */
    AuthType getType();

    /**
     * Authenticate the user.
     *
     * @param request authentication input
     * @param configJson the tenant's provider-specific configuration (decrypted)
     * @return result containing identity info for local user resolution
     * @throws com.taipei.iot.common.exception.BusinessException on auth failure
     */
    AuthenticationResult authenticate(AuthenticationRequest request, String configJson);

    /**
     * Test whether the configuration is valid and connectivity works.
     * Used by admin "Test Connection" button.
     *
     * @param configJson provider config JSON to validate
     * @return true if connection test succeeds
     */
    default boolean testConnection(String configJson) {
        return true; // LOCAL always succeeds
    }
}
```

---

## 4. `AuthenticationDispatcher` 設計

### 4.1 責任

1. 根據 **email domain** 或 **前端傳入的 tenantId** 查詢 `tenant_auth_config`
2. 取得 `auth_type` → 找到對應 `AuthenticationProvider`
3. 解密 config → 呼叫 provider
4. 如果 config 不存在或 disabled → fallback 到 LOCAL

### 4.2 類別圖

```
┌────────────────────┐       ┌───────────────────────────┐
│  AuthServiceImpl   │──────▶│  AuthenticationDispatcher  │
└────────────────────┘       └───────────────────────────┘
                                        │
                                        ▼
                              ┌─────────────────────┐
                              │  Provider Registry   │
                              │  (List<Provider>)    │
                              └─────────────────────┘
                                   │         │
                          ┌────────┘         └────────┐
                          ▼                           ▼
                ┌──────────────────┐       ┌──────────────────┐
                │ LocalAuthProvider │       │  (Future LDAP)   │
                └──────────────────┘       └──────────────────┘
```

### 4.3 虛擬碼

```java
@Component
@RequiredArgsConstructor
public class AuthenticationDispatcher {

    private final List<AuthenticationProvider> providers;
    private final TenantAuthConfigRepository configRepo;
    private final AuthConfigEncryptor encryptor;

    public AuthenticationResult dispatch(AuthenticationRequest request) {
        // 1. Resolve tenant auth config
        TenantAuthConfigEntity config = resolveConfig(request.getTenantId());

        // 2. Determine auth type (default LOCAL)
        AuthType authType = (config != null && config.getEnabled())
                ? config.getAuthType()
                : AuthType.LOCAL;

        // 3. Find matching provider
        AuthenticationProvider provider = providers.stream()
                .filter(p -> p.getType() == authType)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_PROVIDER_NOT_FOUND));

        // 4. Decrypt config and delegate
        String decryptedConfig = (config != null)
                ? encryptor.decrypt(config.getConfigJson())
                : null;

        return provider.authenticate(request, decryptedConfig);
    }
}
```

---

## 5. `LocalAuthProvider` 設計

### 5.1 抽取自 `AuthServiceImpl`

目前 `AuthServiceImpl.login()` 第 2–6 步（find user → check enabled → check locked → verify password → reset fail count）將被移至 `LocalAuthProvider.authenticate()`。

### 5.2 職責劃分

| 原 AuthServiceImpl 步驟 | 移至何處 | 說明 |
|-------------------------|---------|------|
| 1. verifyCaptcha | 留在 AuthServiceImpl | captcha 是 UI 層邏輯，不屬於 provider |
| 2. findByEmail | LocalAuthProvider | provider 負責用戶查找 |
| 3. check enabled | LocalAuthProvider | |
| 4. check locked (+ auto-unlock) | LocalAuthProvider | |
| 5. verify password | LocalAuthProvider | core authentication |
| 6. reset fail count | LocalAuthProvider | |
| 7. query mappings | 留在 AuthServiceImpl | 跨 provider 共用 |
| 7b. password expiry check | 留在 AuthServiceImpl | policy 層邏輯 |
| 8. build response | 留在 AuthServiceImpl | JWT 簽發不屬於 provider |

### 5.3 `LocalAuthProvider` 回傳

```java
return AuthenticationResult.builder()
        .localUserId(user.getUserId())
        .email(user.getEmail())
        .displayName(user.getDisplayName())
        .build();
```

---

## 6. 資料庫 Migration

### 6.1 V50：`tenant_auth_config` 表

```sql
-- V50__auth__tenant_auth_config.sql
CREATE TABLE tenant_auth_config (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       VARCHAR(50)     NOT NULL UNIQUE,
    auth_type       VARCHAR(20)     NOT NULL DEFAULT 'LOCAL',
    enabled         BOOLEAN         NOT NULL DEFAULT TRUE,
    config_json     TEXT,           -- AES-256-GCM 加密後的 JSON
    fallback_local  BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_tenant_auth_config_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenant(tenant_id)
);

CREATE INDEX idx_tenant_auth_config_tenant ON tenant_auth_config(tenant_id);

COMMENT ON TABLE tenant_auth_config IS '租戶認證方式配置（每個租戶最多一筆）';
COMMENT ON COLUMN tenant_auth_config.config_json IS '加密後的 provider 設定 JSON';
COMMENT ON COLUMN tenant_auth_config.fallback_local IS '外部 IdP 失敗時是否允許退回本地帳密';
```

### 6.2 V51：`users` 表擴充

```sql
-- V51__auth__users_add_auth_type.sql
ALTER TABLE users
    ADD COLUMN auth_type    VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    ADD COLUMN external_id  VARCHAR(255);

CREATE INDEX idx_users_external_id ON users(external_id) WHERE external_id IS NOT NULL;

COMMENT ON COLUMN users.auth_type IS '此帳號的認證來源：LOCAL / LDAP / OIDC / SAML';
COMMENT ON COLUMN users.external_id IS '外部 IdP 的唯一識別（LDAP DN / OIDC sub / SAML nameId）';
```

---

## 7. `TenantAuthConfigService` API 設計

### 7.1 端點

| Method | Path | 說明 | 權限 |
|--------|------|------|------|
| GET | `/v1/tenant-auth-config` | 取得目前 tenant 的 auth config | ADMIN |
| PUT | `/v1/tenant-auth-config` | 建立/更新 auth config | ADMIN |
| DELETE | `/v1/tenant-auth-config` | 刪除（回復為 LOCAL） | ADMIN |
| POST | `/v1/tenant-auth-config/test-connection` | 測試連線 | ADMIN |
| GET | `/v1/platform/tenant-auth-config/{tenantId}` | 平台端查看任意 tenant | SUPER_ADMIN |

### 7.2 Request DTO

```java
@Getter @Setter
public class TenantAuthConfigRequest {
    @NotNull
    private AuthType authType;

    /** Provider-specific JSON config (e.g. LDAP url/baseDn, OIDC issuer/clientId) */
    private Map<String, Object> config;

    private Boolean fallbackLocal;
}
```

### 7.3 Response DTO

```java
@Getter @Setter @Builder
public class TenantAuthConfigResponse {
    private Long id;
    private String tenantId;
    private AuthType authType;
    private Boolean enabled;
    /** 脫敏後的 config（password/secret 以 "***" 替代） */
    private Map<String, Object> config;
    private Boolean fallbackLocal;
    private LocalDateTime updatedAt;
}
```

---

## 8. 加密機制：`AuthConfigEncryptor`

### 8.1 演算法

- **AES-256-GCM**（Authenticated Encryption）
- Key 來源：環境變數 `AUTH_CONFIG_SECRET_KEY`（Base64 encoded, 32 bytes）
- 每次加密產生隨機 12-byte IV
- 儲存格式：`base64(IV || ciphertext || tag)`

### 8.2 介面

```java
@Component
public class AuthConfigEncryptor {
    public String encrypt(String plainJson) { ... }
    public String decrypt(String encryptedBase64) { ... }
}
```

### 8.3 Key 不存在時的行為

- 啟動時檢查：若 `AUTH_CONFIG_SECRET_KEY` 未設定，印出 WARN log
- `encrypt()` / `decrypt()`：拋出 `IllegalStateException`
- LOCAL provider 不需要 config_json → 不觸發加密 → Phase A 階段不強制要求設定 key

---

## 9. `AuthServiceImpl.login()` 改造

### 9.1 Before (現有)

```java
public LoginResult login(LoginRequest request, HttpServletRequest httpRequest) {
    verifyCaptcha(request, httpRequest);
    UserEntity user = findAndValidateUser(request.getEmail(), request.getPassword());
    // ... password expiry check, build result
}
```

### 9.2 After (Phase A)

```java
public LoginResult login(LoginRequest request, HttpServletRequest httpRequest) {
    // 1. Captcha 仍由 AuthServiceImpl 處理
    verifyCaptcha(request, httpRequest);

    // 2. Delegate to dispatcher
    AuthenticationRequest authReq = AuthenticationRequest.builder()
            .identifier(request.getEmail())
            .credential(request.getPassword())
            .build();

    AuthenticationResult authResult = authenticationDispatcher.dispatch(authReq);

    // 3. Resolve local user (from provider result)
    UserEntity user = userRepository.findById(authResult.getLocalUserId())
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

    // 4. Password expiry check (only for LOCAL)
    if (user.getAuthType() == AuthType.LOCAL) {
        // existing expiry logic...
    }

    // 5. Build login result (mappings, JWT, etc.) — unchanged
    return buildPostAuthLoginResult(user, mappings, httpRequest);
}
```

### 9.3 變更影響分析

| 改動點 | 風險 | 緩解 |
|--------|------|------|
| 抽取 find+validate 到 LocalAuthProvider | 中 | 保留完全相同的邏輯 + 原有 unit test 仍 pass |
| 新增 Dispatcher 呼叫 | 低 | 無 config 時 default 到 LOCAL |
| UserEntity 新增 authType 欄位 | 低 | default='LOCAL'，現有 row 不受影響 |

---

## 10. UserEntity 擴充

```java
// 新增欄位
@Column(name = "auth_type", nullable = false)
@Enumerated(EnumType.STRING)
private AuthType authType = AuthType.LOCAL;

@Column(name = "external_id")
private String externalId;
```

---

## 11. SecurityConfig 變更

Phase A 暫不新增公開端點（OIDC callback 在 Phase C）。  
僅在 `TenantAuthConfigController` 上加 `@PreAuthorize`：

```java
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
```

---

## 12. 測試計畫

### 12.1 單元測試

| 測試類別 | 涵蓋 |
|---------|------|
| `LocalAuthProviderTest` | ① 正常認證 ② user not found ③ disabled ④ locked (未過期) ⑤ locked (已過期自動解鎖) ⑥ 密碼錯誤 + fail count 遞增 ⑦ fail count 達閥值觸發 lock |
| `AuthenticationDispatcherTest` | ① 無 config → fallback LOCAL ② config disabled → fallback LOCAL ③ config enabled LOCAL → 正確路由 ④ 未知 auth type → exception |
| `AuthConfigEncryptorTest` | ① encrypt → decrypt round-trip ② 錯誤 key → exception ③ 篡改密文 → GCM auth fail |
| `TenantAuthConfigServiceTest` | ① create ② update ③ delete ④ get (脫敏) ⑤ test-connection |
| `AuthServiceImplTest` (既有，補充) | ① login 走 dispatcher 路徑 ② authType=LOCAL 仍觸發 password expiry |

### 12.2 整合測試

- Migration V50/V51 在乾淨 DB 執行成功
- `tenant_auth_config` CRUD 完整流程（含加密驗證）

---

## 13. 驗收標準（Definition of Done）

- [ ] 所有既有 auth 相關測試（458 tests）仍 pass
- [ ] 新增單元測試 ≥ 15 個，全部 pass
- [ ] `mvn clean compile` 無 error
- [ ] Flyway V50 / V51 migration 正常執行
- [ ] 現有登入流程行為完全不變（manual smoke test）
- [ ] `tenant_auth_config` CRUD API 可透過 Swagger 操作
- [ ] `AuthConfigEncryptor` encrypt / decrypt round-trip 通過
- [ ] Code review：無安全性疑慮（secrets 不外洩、GCM 正確使用）

---

## 14. 實作順序

```
Step 1: V50 migration (tenant_auth_config table)
Step 2: V51 migration (users add auth_type / external_id)
Step 3: AuthType enum
Step 4: AuthenticationRequest / AuthenticationResult DTOs
Step 5: AuthenticationProvider interface
Step 6: AuthConfigEncryptor
Step 7: TenantAuthConfigEntity + Repository
Step 8: TenantAuthConfigService (CRUD + 加密)
Step 9: TenantAuthConfigController
Step 10: LocalAuthProvider (extract from AuthServiceImpl)
Step 11: AuthenticationDispatcher
Step 12: Wire dispatcher into AuthServiceImpl.login()
Step 13: UserEntity add authType/externalId fields
Step 14: Unit tests
Step 15: Integration test + full regression
```

---

## 15. 風險與注意事項

| 項目 | 說明 |
|------|------|
| `AuthServiceImpl` 高耦合 | login() 約 200 行，audit / lock 邏輯與 validation 交織。抽取時需逐步替換、小步前進 |
| 環境變數尚未配置 | `AUTH_CONFIG_SECRET_KEY` 在 Phase A 非必須（LOCAL 不需加密），但 Service 需 graceful handle |
| Flyway out-of-order | 已啟用 out-of-order。V50/V51 可安全插入 |
| 既有測試依賴 | AuthServiceImplTest mock 了 PasswordEncoder / UserRepository；需新增 Dispatcher mock |
