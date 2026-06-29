# 多重認證提供者（Multi-Auth Provider）規格書

> 版本：v1.0 · 2026-05-27  
> 範圍：後端 + 資料庫 + REST API + 前端設定頁  
> 適用模組：`auth`、`tenant`、`setting`  
> 前置條件：密碼策略（password-policy-spec.md）Phase 1-3 已完成

---

## 1. 目標與設計原則

讓**租戶管理者**（Tenant Admin）在 UI 上選擇本租戶的認證方式；系統同時支援多種 Identity Provider（IdP），各租戶可獨立配置。

### 1.1 核心設計決策

| # | 決策 | 說明 |
|---|------|------|
| D-1 | **外部認證 + 本地授權** | 不論 IdP 來源，認證完後統一由本系統簽發 JWT、管理 RBAC / menu / permission |
| D-2 | **每個 tenant 一種 primary auth type** | 避免同一租戶混用多種認證造成管理混亂；可選 fallback 到 LOCAL |
| D-3 | **Auto-provision** | 外部 IdP 認證成功時，若本地無對應 user，自動建立（可由 tenant admin 關閉） |
| D-4 | **Credentials 加密儲存** | LDAP bind password、OIDC client secret 等敏感資料使用 AES-256-GCM 加密後存入 DB |
| D-5 | **密碼策略僅適用 LOCAL** | `auth_type != LOCAL` 時，密碼規則由外部 IdP 管理；本地只做 session / JWT 管理 |
| D-6 | **JWT 仍由本系統簽發** | 前端無需改動 token 處理邏輯；OIDC flow 在後端完成 token exchange 後轉為本地 JWT |

### 1.2 非目標（Out of Scope）

- MFA / 2FA（獨立 feature，可疊加在任何 auth type 上）
- Social login（Google / Facebook / GitHub — 若需要可走 OIDC 通道）
- 跨租戶 SSO（single sign-on between tenants）
- SCIM user provisioning（自動同步整個 AD 目錄）

---

## 2. 支援的認證方式

| Auth Type | 協議 | 典型場景 | Phase |
|-----------|------|----------|-------|
| `LOCAL` | 本地帳密 + BCrypt | 預設、小型租戶 | 現有 |
| `LDAP` | LDAP v3 / LDAPS | 企業 Active Directory | A |
| `OIDC` | OpenID Connect 1.0 | Keycloak、Azure AD、Google Workspace | B |
| `SAML` | SAML 2.0 | 政府、大型企業 | C |

---

## 3. 資料庫設計

### 3.1 新增表：`tenant_auth_config`

```sql
CREATE TABLE tenant_auth_config (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       VARCHAR(50)     NOT NULL UNIQUE REFERENCES tenant(tenant_id),
    auth_type       VARCHAR(20)     NOT NULL DEFAULT 'LOCAL',
    -- 加密的 provider 設定（AES-256-GCM，key 由環境變數提供）
    config_encrypted BYTEA,
    -- 明文的非敏感設定（給前端顯示用）
    config_public   JSONB           NOT NULL DEFAULT '{}',
    -- 行為開關
    fallback_local  BOOLEAN         NOT NULL DEFAULT true,
    auto_provision  BOOLEAN         NOT NULL DEFAULT true,
    -- User attribute mapping（外部欄位 → 本地欄位）
    attribute_map   JSONB           NOT NULL DEFAULT '{"username":"preferred_username","email":"email","displayName":"name"}',
    -- Group/role mapping（外部 group → 本地 role code）
    role_map        JSONB           NOT NULL DEFAULT '{}',
    -- 狀態
    enabled         BOOLEAN         NOT NULL DEFAULT true,
    last_test_at    TIMESTAMP,
    last_test_ok    BOOLEAN,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tenant_auth_config_tenant ON tenant_auth_config(tenant_id);

-- 所有既有 tenant 預設 LOCAL
INSERT INTO tenant_auth_config (tenant_id, auth_type)
SELECT tenant_id, 'LOCAL' FROM tenant WHERE tenant_id != '__PLATFORM__'
ON CONFLICT DO NOTHING;
```

### 3.2 `config_encrypted` 各 auth_type 的 JSON 結構

#### LDAP

```json
{
  "host": "ldap.example.com",
  "port": 636,
  "useSsl": true,
  "baseDn": "dc=example,dc=com",
  "bindDn": "cn=readonly,dc=example,dc=com",
  "bindPassword": "***",
  "userSearchBase": "ou=users",
  "userSearchFilter": "(sAMAccountName={0})",
  "groupSearchBase": "ou=groups",
  "groupSearchFilter": "(member={0})",
  "groupRoleAttribute": "cn",
  "connectionTimeout": 5000,
  "readTimeout": 10000,
  "referral": "follow"
}
```

#### OIDC（Keycloak / Azure AD）

```json
{
  "issuerUrl": "https://keycloak.example.com/realms/my-realm",
  "clientId": "taipei-iot",
  "clientSecret": "***",
  "scopes": ["openid", "profile", "email"],
  "usernameClaim": "preferred_username",
  "emailClaim": "email",
  "displayNameClaim": "name",
  "groupsClaim": "groups",
  "endSessionEndpoint": "https://keycloak.example.com/realms/my-realm/protocol/openid-connect/logout",
  "pkceEnabled": true
}
```

#### SAML

```json
{
  "metadataUrl": "https://idp.example.com/metadata.xml",
  "entityId": "taipei-iot-sp",
  "assertionConsumerServiceUrl": "https://app.example.com/v1/noauth/saml/callback",
  "signRequests": true,
  "wantAssertionsSigned": true,
  "nameIdFormat": "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress",
  "attributeStatements": {
    "username": "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name",
    "email": "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress"
  },
  "signingCertificate": "-----BEGIN CERTIFICATE-----\n...",
  "signingKey": "-----BEGIN PRIVATE KEY-----\n..."
}
```

### 3.3 `config_public`（不加密，可回傳前端）

```json
{
  "host": "ldap.example.com",
  "port": 636,
  "useSsl": true,
  "issuerUrl": "https://keycloak.example.com/realms/my-realm",
  "clientId": "taipei-iot"
}
```

> `config_public` 由後端在寫入時從完整 config 提取非敏感欄位自動填入。

---

## 4. 後端設計

### 4.1 模組結構

```
backend/src/main/java/com/taipei/iot/auth/
├── provider/
│   ├── AuthenticationProvider.java          # Strategy 介面
│   ├── AuthenticationResult.java            # 認證結果 VO
│   ├── AuthenticationDispatcher.java        # Resolver: tenantId → provider
│   ├── local/
│   │   └── LocalAuthProvider.java           # 現有邏輯抽取
│   ├── ldap/
│   │   ├── LdapAuthProvider.java            # LDAP bind + search
│   │   └── LdapConnectionFactory.java       # 連線池管理
│   ├── oidc/
│   │   ├── OidcAuthProvider.java            # Authorization Code + PKCE
│   │   ├── OidcTokenExchanger.java          # 與 IdP 交換 token
│   │   └── OidcUserInfoFetcher.java         # 從 userinfo endpoint 取使用者資訊
│   └── saml/
│       ├── SamlAuthProvider.java            # SAML assertion 解析
│       └── SamlMetadataResolver.java        # IdP metadata 快取
├── provision/
│   ├── UserProvisionService.java            # Auto-provision + attribute/role mapping
│   └── GroupRoleMapper.java                 # 外部 group → 本地 role 對映
├── config/
│   ├── TenantAuthConfigEntity.java          # JPA entity
│   ├── TenantAuthConfigRepository.java      # Repository
│   ├── TenantAuthConfigService.java         # CRUD + 加解密 + 連線測試
│   └── AuthConfigEncryptor.java             # AES-256-GCM 加解密工具
└── controller/
    └── TenantAuthConfigController.java      # CRUD API + test-connection
```

### 4.2 `AuthenticationProvider` 介面

```java
public interface AuthenticationProvider {

    /**
     * 執行認證。
     *
     * @param username 使用者輸入的帳號
     * @param password 使用者輸入的密碼（OIDC 時為 null，走 redirect flow）
     * @param config   解密後的 provider 設定
     * @return 認證結果（成功時含外部使用者資訊）
     */
    AuthenticationResult authenticate(String username, String password, JsonNode config);

    /** 此 provider 支援的 auth type */
    String supportedType();

    /**
     * 測試連線（不做認證，僅驗證設定可通）。
     * 用於租戶 admin 設定完後的「Test Connection」按鈕。
     */
    TestConnectionResult testConnection(JsonNode config);
}
```

### 4.3 `AuthenticationResult` VO

```java
@Value
@Builder
public class AuthenticationResult {
    boolean success;
    String errorMessage;

    // 外部 IdP 提供的使用者資訊
    String externalId;       // 外部系統的 unique ID
    String username;
    String email;
    String displayName;
    List<String> groups;     // 外部 group 名稱（用於 role mapping）
    Map<String, Object> rawAttributes; // 完整 attribute map（備用）
}
```

### 4.4 `AuthenticationDispatcher`（核心路由）

```java
@Service
@RequiredArgsConstructor
public class AuthenticationDispatcher {

    private final List<AuthenticationProvider> providers;
    private final TenantAuthConfigService configService;
    private final UserProvisionService provisionService;

    /**
     * 依 tenant 設定路由到對應的 AuthenticationProvider。
     * 成功後執行 auto-provision（若啟用）。
     */
    public AuthDispatchResult dispatch(String tenantId, String username, String password) {
        TenantAuthConfig config = configService.getConfig(tenantId);

        if (!config.isEnabled() || "LOCAL".equals(config.getAuthType())) {
            // 走既有 local flow（不經過 dispatcher）
            return AuthDispatchResult.useLocal();
        }

        AuthenticationProvider provider = providers.stream()
                .filter(p -> p.supportedType().equals(config.getAuthType()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_PROVIDER_NOT_CONFIGURED));

        JsonNode decryptedConfig = configService.decryptConfig(config);
        AuthenticationResult result = provider.authenticate(username, password, decryptedConfig);

        if (!result.isSuccess()) {
            if (config.isFallbackLocal()) {
                return AuthDispatchResult.useLocal(); // fallback
            }
            throw new BusinessException(ErrorCode.AUTH_EXTERNAL_FAILED, result.getErrorMessage());
        }

        // Auto-provision: 確保本地有對應 user
        UserEntity localUser = provisionService.ensureUser(tenantId, result, config);

        return AuthDispatchResult.authenticated(localUser);
    }
}
```

### 4.5 整合點：`AuthServiceImpl.login()` 修改

```java
public LoginResult login(LoginRequest request) {
    String tenantId = resolveTenantId(request);

    // ===== NEW: multi-auth dispatch =====
    AuthDispatchResult dispatchResult = authDispatcher.dispatch(
            tenantId, request.getAccount(), request.getPassword());

    UserEntity user;
    if (dispatchResult.isUseLocal()) {
        // 現有邏輯：DB lookup + BCrypt matches + fail count ...
        user = localLogin(request);
    } else {
        // 外部認證已完成，user 已 provision
        user = dispatchResult.getUser();
        // 清除 fail count（外部認證成功）
        clearFailCount(user);
    }
    // ===== END NEW =====

    // 以下不變：expiry check → JWT 簽發 → session → audit
    checkPasswordExpiry(user, tenantId);
    return issueTokens(user, tenantId);
}
```

### 4.6 OIDC 流程（Browser Redirect）

OIDC 和 SAML 不走帳密直接提交，而是瀏覽器跳轉：

```
1. 前端 → GET /v1/noauth/auth/oidc/authorize?tenantId=X
2. 後端 → 302 Redirect to IdP authorization endpoint
3. 使用者在 IdP 登入
4. IdP → 302 Redirect to /v1/noauth/auth/oidc/callback?code=xxx&state=yyy
5. 後端 → exchange code for tokens → validate → provision → 簽發本地 JWT
6. 後端 → 302 Redirect to frontend /login/callback?token=xxx
7. 前端 → 存 token → 正常登入流程
```

### 4.7 加密機制

```java
@Component
public class AuthConfigEncryptor {
    private final SecretKey key; // 從環境變數 AUTH_CONFIG_SECRET_KEY 載入

    public byte[] encrypt(String plainJson) {
        // AES-256-GCM, random 12-byte IV prepended to ciphertext
    }

    public String decrypt(byte[] encrypted) {
        // Extract IV → decrypt → return JSON string
    }
}
```

環境變數：
```yaml
auth:
  config-secret-key: ${AUTH_CONFIG_SECRET_KEY}  # 32-byte base64 encoded
```

### 4.8 User Provision

```java
@Service
@RequiredArgsConstructor
public class UserProvisionService {

    public UserEntity ensureUser(String tenantId, AuthenticationResult extResult,
                                  TenantAuthConfig config) {
        // 1. 找既有 user（by email or externalId）
        UserEntity existing = userRepository.findByEmailAndTenantId(
                extResult.getEmail(), tenantId).orElse(null);

        if (existing != null) {
            // 更新 displayName 等（若 attribute 有變）
            syncAttributes(existing, extResult, config.getAttributeMap());
            return existing;
        }

        if (!config.isAutoProvision()) {
            throw new BusinessException(ErrorCode.AUTH_USER_NOT_PROVISIONED);
        }

        // 2. 自動建立
        UserEntity newUser = UserEntity.builder()
                .email(extResult.getEmail())
                .displayName(extResult.getDisplayName())
                .tenantId(tenantId)
                .passwordHash(null)  // 外部認證無本地密碼
                .authType(config.getAuthType())
                .externalId(extResult.getExternalId())
                .enabled(true)
                .build();

        userRepository.save(newUser);

        // 3. Role mapping
        groupRoleMapper.assignRoles(newUser, extResult.getGroups(), config.getRoleMap());

        return newUser;
    }
}
```

---

## 5. REST API

### 5.1 認證設定管理（TENANT_ADMIN / SUPER_ADMIN）

| Method | Path | 說明 |
|--------|------|------|
| `GET` | `/v1/auth/auth-config` | 取得本租戶的認證設定（`config_public` only） |
| `PUT` | `/v1/auth/auth-config` | 更新認證設定（含 encrypted secrets） |
| `POST` | `/v1/auth/auth-config/test` | 測試連線（LDAP bind / OIDC discovery / SAML metadata fetch） |

### 5.2 OIDC 流程端點（No Auth）

| Method | Path | 說明 |
|--------|------|------|
| `GET` | `/v1/noauth/auth/oidc/authorize` | 產生 authorization URL 並 redirect |
| `GET` | `/v1/noauth/auth/oidc/callback` | IdP callback → token exchange → 發本地 JWT |

### 5.3 SAML 流程端點（No Auth）

| Method | Path | 說明 |
|--------|------|------|
| `GET` | `/v1/noauth/auth/saml/login` | 產生 SAML AuthnRequest 並 redirect |
| `POST` | `/v1/noauth/auth/saml/callback` | IdP POST binding callback → assertion 驗證 |
| `GET` | `/v1/noauth/auth/saml/metadata` | 本系統作為 SP 的 metadata XML |

### 5.4 公開端點（Login 頁需要）

| Method | Path | 說明 |
|--------|------|------|
| `GET` | `/v1/noauth/auth/config/type` | 依 `?tenantId=` 回傳 auth_type + 是否顯示 SSO 按鈕 |

### 5.5 Request / Response 範例

```http
PUT /v1/auth/auth-config
Authorization: Bearer ...
Content-Type: application/json

{
  "authType": "OIDC",
  "config": {
    "issuerUrl": "https://keycloak.example.com/realms/my-realm",
    "clientId": "taipei-iot",
    "clientSecret": "my-secret",
    "scopes": ["openid", "profile", "email"],
    "usernameClaim": "preferred_username",
    "pkceEnabled": true
  },
  "fallbackLocal": true,
  "autoProvision": true,
  "attributeMap": {
    "username": "preferred_username",
    "email": "email",
    "displayName": "name"
  },
  "roleMap": {
    "iot-admin": "ADMIN",
    "iot-viewer": "VIEWER"
  }
}

200 OK
{
  "errorCode": "00000",
  "body": {
    "authType": "OIDC",
    "configPublic": {
      "issuerUrl": "https://keycloak.example.com/realms/my-realm",
      "clientId": "taipei-iot"
    },
    "fallbackLocal": true,
    "autoProvision": true,
    "enabled": true,
    "lastTestAt": null,
    "lastTestOk": null
  }
}
```

```http
POST /v1/auth/auth-config/test
Authorization: Bearer ...

200 OK
{
  "errorCode": "00000",
  "body": {
    "success": true,
    "message": "OIDC discovery successful. Endpoints verified.",
    "details": {
      "authorizationEndpoint": "https://keycloak.example.com/.../auth",
      "tokenEndpoint": "https://keycloak.example.com/.../token",
      "userinfoEndpoint": "https://keycloak.example.com/.../userinfo"
    }
  }
}
```

---

## 6. 前端設計

### 6.1 頁面

| 頁面 | 路由 | 角色 |
|------|------|------|
| 認證設定頁 | `/admin/security/auth-config` | `TENANT_ADMIN` |
| Login 頁擴充 | `/login` | 所有人 |

### 6.2 認證設定頁 UI

```
┌─────────────────────────────────────────────────────────┐
│  認證方式設定                                             │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  認證方式：  ○ 本地帳密 (LOCAL)                           │
│             ○ LDAP / Active Directory                   │
│             ○ OpenID Connect (Keycloak / Azure AD)      │
│             ○ SAML 2.0                                  │
│                                                         │
│  ┌─ LDAP 設定 ──────────────────────────────────────┐   │
│  │  主機：[ldap.example.com    ]  埠號：[636 ]       │   │
│  │  ☑ 使用 SSL (LDAPS)                              │   │
│  │  Base DN：[dc=example,dc=com           ]          │   │
│  │  Bind DN：[cn=readonly,dc=example...   ]          │   │
│  │  Bind 密碼：[••••••••]                            │   │
│  │  使用者搜尋 Filter：[(sAMAccountName={0})]        │   │
│  └───────────────────────────────────────────────────┘   │
│                                                         │
│  ☑ 外部認證失敗時允許本地帳密 fallback                    │
│  ☑ 自動建立使用者（Auto-provision）                      │
│                                                         │
│  ┌─ 屬性對映 ───────────────────────────────────────┐   │
│  │  外部 username 欄位：[sAMAccountName  ]           │   │
│  │  外部 email 欄位：   [mail            ]           │   │
│  │  外部顯示名稱欄位：  [displayName     ]           │   │
│  └───────────────────────────────────────────────────┘   │
│                                                         │
│  ┌─ 角色對映 ───────────────────────────────────────┐   │
│  │  外部群組           →   本地角色                   │   │
│  │  [Domain Admins   ] →   [ADMIN    ▼]             │   │
│  │  [IOT Viewers     ] →   [VIEWER   ▼]             │   │
│  │  [+ 新增對映]                                     │   │
│  └───────────────────────────────────────────────────┘   │
│                                                         │
│  [ 測試連線 ]          [ 儲存 ]                          │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### 6.3 Login 頁擴充

```
┌──────────────────────────────┐
│        Taipei IoT            │
│                              │
│  帳號：[________________]    │
│  密碼：[________________]    │
│                              │
│  [ 登入 ]                    │
│                              │  ← auth_type = LOCAL 時只顯示這些
│  ─────── 或 ──────────       │
│                              │  ← auth_type = OIDC/SAML 時顯示 SSO 按鈕
│  [ 🔑 使用企業 SSO 登入 ]    │
│                              │
│  忘記密碼？                   │
└──────────────────────────────┘
```

前端登入頁 mount 時呼叫 `GET /v1/noauth/auth/config/type?tenantId=X`：
- `LOCAL` → 只顯示帳密表單
- `OIDC` / `SAML` → 顯示 SSO 按鈕 + 帳密表單（若 `fallbackLocal=true`）
- `LDAP` → 仍走帳密表單，後端路由到 LDAP bind（前端無感）

---

## 7. 安全考量

| 項目 | 措施 |
|------|------|
| Secret 儲存 | AES-256-GCM 加密；master key 存環境變數，不入 DB |
| OIDC state | 使用 random `state` 參數防 CSRF；存入 server-side session（Redis，TTL 5 min） |
| PKCE | OIDC 預設啟用 PKCE（S256）防 authorization code interception |
| SAML signature | 強制驗證 IdP 簽名；SP 簽署 AuthnRequest |
| LDAP injection | Escape `username` input before inserting into search filter |
| Token 時效 | OIDC callback 的 `code` 一次性使用；SAML assertion 檢查 `NotOnOrAfter` |
| 連線測試 | Rate limit（每 tenant 1 次 / 10 秒）防濫用 |
| 密碼洩漏 | `config_encrypted` 欄位在 API response 中永遠不回傳；只回傳 `config_public` |

---

## 8. `users` 表擴充

```sql
ALTER TABLE users
    ADD COLUMN auth_type     VARCHAR(20) DEFAULT 'LOCAL',
    ADD COLUMN external_id   VARCHAR(255);

CREATE INDEX idx_users_external_id ON users(external_id) WHERE external_id IS NOT NULL;
```

- `auth_type`：該 user 是由哪種方式建立/認證的
- `external_id`：外部 IdP 中的 unique identifier（LDAP DN / OIDC sub / SAML NameID）
- `password_hash` 允許 NULL（外部認證的 user 無本地密碼）

---

## 9. 密碼策略相容性

| 情境 | 行為 |
|------|------|
| `auth_type=LOCAL` | 完整套用密碼策略（Phase 1-3） |
| `auth_type=LDAP` | 登入走 LDAP bind；改密功能隱藏；密碼策略由 AD 管理 |
| `auth_type=OIDC/SAML` | 無本地密碼；改密/密碼過期/強制改密全部跳過 |
| `fallback_local=true` 且外部失敗 | 改走 LOCAL flow → 適用密碼策略 |
| Auto-provisioned user | `password_hash=NULL`，若切回 LOCAL 需走「忘記密碼」設定密碼 |

---

## 10. 測試計畫

### 10.1 單元測試

| 測試類 | 涵蓋 |
|--------|------|
| `LdapAuthProviderTest` | bind 成功 / 失敗 / 連線逾時 / filter injection 防護 |
| `OidcAuthProviderTest` | code exchange 成功 / invalid code / token 過期 |
| `AuthenticationDispatcherTest` | LOCAL fallback / provider routing / disabled config |
| `UserProvisionServiceTest` | 新建 / 已存在更新 / auto-provision off 拒絕 / role mapping |
| `AuthConfigEncryptorTest` | encrypt → decrypt round-trip / 錯誤 key 拒絕 |
| `TenantAuthConfigServiceTest` | CRUD / test-connection / 敏感欄位不外洩 |

### 10.2 整合測試

- Testcontainers + OpenLDAP 容器：真實 LDAP bind
- Testcontainers + Keycloak 容器：真實 OIDC flow
- SAML：mock IdP（如 samling）
- Migration 驗證：V50 在乾淨 DB 和既有 DB 都能跑

---

## 11. 實作分期

| Phase | 範圍 | 依賴 | 估計複雜度 |
|-------|------|------|-----------|
| **A** | Strategy 介面 + `tenant_auth_config` 表 + Dispatcher + LOCAL provider 重構 + 設定 API + 加密機制 | 無 | 低 |
| **B** | LDAP provider（bind + search + group mapping + connection pool） | A | 中 |
| **C** | OIDC provider（authorization code + PKCE + token exchange + userinfo） | A | 中 |
| **D** | 前端設定 UI + Login 頁 SSO 按鈕 + 連線測試 | A | 中 |
| **E** | SAML provider（SP metadata + assertion parsing + signature verification） | A | 高 |
| **F** | User provision 進階：定期同步 / 停用不存在的 user / SCIM | B or C | 高 |

### Phase A 交付物（最小可行）

1. `AuthenticationProvider` 介面 + `LocalAuthProvider`（抽取自現有 `AuthServiceImpl`）
2. `AuthenticationDispatcher`
3. `tenant_auth_config` migration（V50）
4. `TenantAuthConfigService` + Controller（CRUD + 加密）
5. `AuthServiceImpl.login()` 接入 dispatcher
6. `users` 表加 `auth_type` / `external_id`（V51）
7. 單元測試 ×10

---

## 12. 環境變數新增

| 變數 | 說明 | 範例 |
|------|------|------|
| `AUTH_CONFIG_SECRET_KEY` | AES-256 master key（base64） | `dGhpcyBpcyBhIDMyLWJ5dGUga2V5ISEhISEhISEh` |
| `OIDC_REDIRECT_BASE_URL` | OIDC callback 的外部可達 URL | `https://app.example.com` |

---

## 13. 風險與緩解

| 風險 | 機率 | 緩解 |
|------|------|------|
| LDAP 服務不穩定導致登入失敗 | 中 | `fallbackLocal=true` + connection timeout 5s + retry 1 次 |
| IdP 憑證過期 | 中 | 定期 test-connection cron job + 告警 |
| Master key 遺失 → 所有 config 無法解密 | 低 | 文件化 key rotation 流程；支援多 key versioning |
| OIDC state 被重播 | 低 | state 存 Redis TTL 5 min + 一次性消費 |
| Auto-provision 建立大量垃圾 user | 低 | 限制 provision 頻率 + audit log + admin 可禁用 |
| 切換 auth_type 後既有 user 無法登入 | 中 | 保留 `fallback_local`；切換時提示 admin 確認 |

---

## 14. 對照現有系統的影響

| 模組 | 影響 |
|------|------|
| `AuthServiceImpl` | 在 `login()` 入口加 dispatcher 呼叫；其餘（JWT 簽發、session、audit）不變 |
| `PasswordPolicy` | 加 `auth_type` 判斷：非 LOCAL 跳過密碼規則 |
| `PasswordExpiryChecker` | 非 LOCAL user 直接回 `OK` |
| `UserAdminService` | `createUser` 需支援 `auth_type` 欄位；外部 user 的 `resetPassword` 禁用 |
| `SecurityConfig` | 新增 noauth 路徑：`/v1/noauth/auth/oidc/**`、`/v1/noauth/auth/saml/**` |
| 前端 `LoginView` | Mount 時查詢 auth config → 條件渲染 SSO 按鈕 |
| 前端 Router | 新增 `/login/callback` 處理 OIDC/SAML redirect 回來的 token |
