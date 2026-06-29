# LDAP / AD 認證機制文件

## 目錄

- [架構總覽](#架構總覽)
- [資料庫結構](#資料庫結構)
- [程式碼結構](#程式碼結構)
- [認證流程](#認證流程)
- [路由決策邏輯](#路由決策邏輯)
- [設定管理](#設定管理)
- [LdapConfig 欄位對照](#ldapconfig-欄位對照)
- [加解密機制](#加解密機制)
- [常見錯誤代碼](#常見錯誤代碼)
- [OpenLDAP 測試環境](./openldap-test-env.md)
- [維運手冊](./operations.md)

---

## 架構總覽

```
LoginRequest
     │
     ▼
AuthServiceImpl.login()
     │  resolveLoginTenantId()          ← 從 user_tenant_mapping 預先查出 tenantId
     │
     ▼
AuthenticationDispatcher.dispatch()
     │
     ├─ resolveUserAuthType()           ← 查 users.auth_type
     │    └─ LDAP → 走 LdapAuthProvider
     │    └─ LOCAL → 走 LocalAuthProvider
     │    └─ null → 查 tenant_auth_config (tenant 層級 fallback)
     │
     ├─ resolveConfig(tenantId)         ← 查 tenant_auth_config
     │    └─ decryptConfig()            ← AES-256-GCM 解密
     │
     └─ LdapAuthProvider.authenticate()
          │  parseConfig(decryptedJson)  ← Jackson 反序列化 LdapConfig
          │  findLocalUser(email)        ← 確認本地 users 表存在且 auth_type=LDAP
          │  buildUserDn(email)          ← 套用 user_dn_pattern
          └─ LDAP bind (direct bind)    ← 不做 search，直接用完整 DN bind
```

---

## 資料庫結構

### `users` 表（新增欄位）

| 欄位 | 型別 | 說明 |
|------|------|------|
| `auth_type` | `VARCHAR` | `LOCAL`（本地密碼）/ `LDAP`（AD bind）/ `OIDC` / `SAML` |
| `external_id` | `VARCHAR` | 外部系統 ID（LDAP 為 mail，OIDC 為 sub） |
| `password_hash` | `VARCHAR` | LDAP 用戶設為固定值 `AD_AUTH`，不儲存真實密碼 |

### `tenant_auth_config` 表

| 欄位 | 型別 | 說明 |
|------|------|------|
| `tenant_id` | `VARCHAR` | Tenant ID（UNIQUE） |
| `auth_type` | `ENUM` | `LOCAL` / `LDAP` / `OIDC` / `SAML` |
| `enabled` | `BOOLEAN` | 是否啟用 |
| `config_json` | `TEXT` | **加密後**的 JSON（AES-256-GCM + Base64） |
| `fallback_local` | `BOOLEAN` | 外部認證失敗時是否 fallback 到本地密碼 |

> ⚠️ `config_json` 欄位**必須**透過 API 寫入，不可手動填入明文。

---

## 程式碼結構

```
backend/src/main/java/com/taipei/iot/auth/provider/
├── AuthType.java                        # enum: LOCAL, LDAP, OIDC, SAML
├── AuthenticationProvider.java          # interface
├── AuthenticationDispatcher.java        # 路由入口
├── AuthenticationRequest.java           # 認證請求 DTO
├── AuthenticationResult.java            # 認證結果 DTO
├── config/
│   ├── entity/TenantAuthConfigEntity.java
│   ├── repository/TenantAuthConfigRepository.java
│   ├── service/TenantAuthConfigService.java
│   ├── dto/TenantAuthConfigRequest.java  # config 為 Map<String,Object>
│   └── dto/TenantAuthConfigResponse.java
├── crypto/
│   └── AuthConfigEncryptor.java         # AES-256-GCM 加解密
├── local/
│   └── LocalAuthProvider.java           # BCrypt 本地認證
└── ldap/
    ├── LdapAuthProvider.java            # LDAP direct bind
    └── LdapConfig.java                  # config JSON 反序列化 DTO
```

---

## 認證流程

### LDAP 用戶登入流程

```
1. 前端送出 POST /v1/noauth/login
   { "email": "alice@example.tw", "password": "alice123" }

2. AuthServiceImpl.resolveLoginTenantId(email)
   → 查 user_tenant_mapping 取得 tenantId = "T_XXX"
   → 若用戶屬於多個 tenant，回傳 null（後續再選擇）

3. AuthenticationDispatcher.dispatch()
   a. resolveUserAuthType("alice@example.tw")
      → 查 users 表，找到 auth_type = LDAP
      → 回傳 AuthType.LDAP
   
   b. 因 authType 來自 user（非 tenant config），config = null
      → if (config == null && authType != LOCAL)
          → resolveConfig(tenantId) 查 tenant_auth_config
          → decryptConfig() 解密 AES-GCM → 得到 JSON 字串

4. LdapAuthProvider.authenticate(request, decryptedJson)
   a. parseConfig(json) → LdapConfig
   b. userRepository.findByEmail() → 確認本地帳號存在且 auth_type = LDAP
   c. ldapConfig.buildUserDn("alice@example.tw")
      → "mail={0},ou=Users,DC=example,DC=tw".replace("{0}", "alice@example.tw")
      → "mail=alice@example.tw,ou=Users,DC=example,DC=tw"
   d. LDAP bind: LdapUtils.newLdapContext(env, null)
      → 成功 → 回傳 AuthenticationResult(localUserId)
      → 失敗（javax.naming.AuthenticationException）→ LDAP_AUTH_FAILED

5. 後續與 LOCAL 相同：發行 JWT Token
```

---

## 路由決策邏輯

```
user.auth_type 不為 null → 使用 user.auth_type（優先）
user.auth_type 為 null   → 使用 tenant_auth_config.auth_type（繼承）
```

| `user.auth_type` | Tenant 設定 | 實際路由 |
|-----------------|------------|---------|
| `LOCAL` | 任何值 | LocalAuthProvider |
| `LDAP` | 任何值 | LdapAuthProvider |
| `null` | `LOCAL` or 無設定 | LocalAuthProvider |
| `null` | `LDAP` | LdapAuthProvider（繼承 tenant） |

**意義**：把 tenant 整體改為 LDAP，不會影響已設定 `auth_type=LOCAL` 的帳號。

---

## 設定管理

### 透過 API 設定 LDAP（正確方式）

```bash
# 需要 PLATFORM_TENANT_MANAGE 權限（superadmin token）
curl -X PUT http://localhost:8080/v1/auth/tenant-auth-config \
  -H "Authorization: Bearer <superadmin-token>" \
  -H "X-Tenant-Id: <tenantId>" \
  -H "Content-Type: application/json" \
  -d '{
    "authType": "LDAP",
    "config": {
      "url": "ldap://localhost:389",
      "base_dn": "DC=example,DC=tw",
      "user_dn_pattern": "mail={0},ou=Users,DC=example,DC=tw",
      "connect_timeout_ms": 5000,
      "read_timeout_ms": 10000
    },
    "fallbackLocal": false
  }'
```

> Service 會自動呼叫 `AuthConfigEncryptor.encrypt()` 後寫入 DB。

### 新增 LDAP 用戶（透過 API）

```bash
curl -X POST http://localhost:8080/v1/admin/users \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "alice@example.tw",
    "displayName": "Alice Chen",
    "roleId": "<roleId>",
    "authType": "LDAP"
  }'
```

LDAP 用戶不需要 `initialPassword`，系統會自動設定 `password_hash = "AD_AUTH"`。

---

## LdapConfig 欄位對照

> ⚠️ **JSON key 必須使用 snake_case**，對應 `LdapConfig.java` 的 `@JsonProperty`

| JSON key | Java field | 說明 | 必填 |
|----------|-----------|------|------|
| `url` | `url` | LDAP 連線 URL（`ldap://` 或 `ldaps://`） | ✅ |
| `base_dn` | `baseDn` | 搜尋基底 DN | ✅ |
| `user_dn_pattern` | `userDnPattern` | Bind DN 樣板，`{0}` 替換為 identifier | 選填 |
| `connect_timeout_ms` | `connectTimeoutMs` | 連線逾時（毫秒），預設 5000 | 選填 |
| `read_timeout_ms` | `readTimeoutMs` | 讀取逾時（毫秒），預設 10000 | 選填 |

**`user_dn_pattern` 範例：**

```
# 以 mail 屬性為 RDN
"user_dn_pattern": "mail={0},ou=Users,DC=example,DC=tw"

# 以 uid 屬性為 RDN
"user_dn_pattern": "uid={0},ou=Users,DC=example,DC=tw"

# 以 sAMAccountName（AD 常見）
"user_dn_pattern": "CN={0},OU=Users,DC=corp,DC=example,DC=com"
```

---

## 加解密機制

- 演算法：AES-256-GCM（authenticated encryption）
- Key 長度：32 bytes（256 bits）
- IV 長度：12 bytes（隨機生成，每次加密不同）
- 儲存格式：`Base64( IV[12] || ciphertext || GCM-tag[16] )`
- Key 設定：`app.auth.config-secret-key`（application-dev.yml / 環境變數）

### Key 設定範例

```yaml
# application-dev.yml
app:
  auth:
    config-secret-key: ${AUTH_CONFIG_SECRET_KEY:your-base64-encoded-32-byte-key}
```

### 產生新 Key

```bash
python3 -c "import secrets,base64; print(base64.b64encode(secrets.token_bytes(32)).decode())"
```

> **生產環境**：透過環境變數 `AUTH_CONFIG_SECRET_KEY` 注入，不寫入程式碼。
