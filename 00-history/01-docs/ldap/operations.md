# LDAP 認證維運手冊

## 常見錯誤代碼

| 錯誤碼 | 原因 | 解法 |
|--------|------|------|
| `LDAP_CONFIG_INVALID` | config_json 解密失敗或欄位缺少 | 確認 `app.auth.config-secret-key` 正確；重新透過 API 設定 config |
| `LDAP_AUTH_FAILED` | LDAP bind 失敗（密碼錯或 DN 不存在） | 用 `ldapwhoami` 手動測試 bind |
| `LDAP_CONNECTION_FAILED` | 無法連到 LDAP server | 確認 `url`、防火牆、container 狀態 |
| `LDAP_USER_NOT_FOUND` | 本地 users 表找不到這個 email | 先在系統建立 LDAP 用戶（`authType=LDAP`） |
| `LDAP_USER_NOT_LDAP_TYPE` | 本地用戶 `auth_type != LDAP` | 確認用戶建立時有帶 `authType: LDAP` |

---

## 診斷步驟

### 1. 確認 tenant 設定是否存在

```sql
SELECT tenant_id, auth_type, enabled, fallback_local, 
       (config_json IS NOT NULL) AS has_config
FROM iot_workflowdb.tenant_auth_config
WHERE tenant_id = '<tenantId>';
```

### 2. 確認用戶的 auth_type

```sql
SELECT user_id, email, auth_type, password_hash
FROM iot_workflowdb.users
WHERE email = '<email>';
```

正確的 LDAP 用戶應有：
- `auth_type = 'LDAP'`
- `password_hash = 'AD_AUTH'`

### 3. 測試 LDAP bind（不經過後端）

```bash
ldapwhoami \
  -x -H ldap://<ldap-host>:389 \
  -D "<完整DN>" \
  -w "<密碼>"
```

### 4. 確認加密 key 有效

開發環境檢查：

```bash
grep -A2 "config-secret-key" \
  backend/src/main/resources/application-dev.yml
```

若 key 為空或不正確，LDAP config 將無法解密，回傳 `LDAP_CONFIG_INVALID`。

---

## 新增 LDAP Tenant 設定（完整步驟）

### Step 1：確認 key 已設定

```bash
# dev 環境
cat backend/src/main/resources/application-dev.yml | grep config-secret-key
```

### Step 2：取得 Superadmin Token

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/v1/noauth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"superadmin@example.com","password":"<pw>"}' \
  | jq -r '.data.accessToken')
```

### Step 3：設定 Tenant LDAP Config

```bash
curl -s -X PUT http://localhost:8080/v1/auth/tenant-auth-config \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-Id: T_XXXXXXXX" \
  -H "Content-Type: application/json" \
  -d '{
    "authType": "LDAP",
    "config": {
      "url": "ldap://192.168.x.x:389",
      "base_dn": "DC=corp,DC=example,DC=com",
      "user_dn_pattern": "mail={0},ou=Users,DC=corp,DC=example,DC=com",
      "connect_timeout_ms": 5000,
      "read_timeout_ms": 10000
    },
    "fallbackLocal": false
  }'
```

> `fallbackLocal: false`：建議生產環境關閉，避免 LDAP 帳號被本地密碼繞過。  
> `fallbackLocal: true`：除錯用，LDAP 失敗時改走本地密碼（需 `password_hash` 有有效值）。

### Step 4：建立 LDAP 用戶

```bash
curl -s -X POST http://localhost:8080/v1/admin/users \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "bob@corp.example.com",
    "displayName": "Bob Wang",
    "roleId": "<roleId>",
    "authType": "LDAP"
  }'
```

### Step 5：測試登入

```bash
curl -s -X POST http://localhost:8080/v1/noauth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"bob@corp.example.com","password":"<ldap-password>"}'
```

---

## 混合環境說明（部分 LDAP / 部分 LOCAL）

**場景**：Tenant 設定為 LDAP，但希望保留某些服務帳號使用本地密碼。

解法：在建立本地服務帳號時，顯式設定 `authType: LOCAL`：

```bash
curl -X POST http://localhost:8080/v1/admin/users \
  -d '{ ..., "authType": "LOCAL", "initialPassword": "..." }'
```

路由決策：`user.auth_type = LOCAL` → 永遠走 LocalAuthProvider，不受 tenant LDAP 設定影響。

---

## 生產環境注意事項

1. **Key 管理**：`AUTH_CONFIG_SECRET_KEY` 透過 Kubernetes Secret 或 Vault 注入，不可寫入程式碼
2. **LDAPS**：生產環境應使用 `ldaps://` 加密傳輸，需匯入自簽憑證至 JVM truststore
3. **fallback_local**：生產設為 `false`，防止帳號管控繞過
4. **Key 輪換**：更換 key 前需先用舊 key 解密所有 `tenant_auth_config`，以新 key 重加密後更新 DB

---

## 相關原始碼位置

| 功能 | 路徑 |
|------|------|
| 路由入口 | `backend/src/main/java/com/taipei/iot/auth/provider/AuthenticationDispatcher.java` |
| LDAP Provider | `backend/src/main/java/com/taipei/iot/auth/provider/ldap/LdapAuthProvider.java` |
| LDAP Config DTO | `backend/src/main/java/com/taipei/iot/auth/provider/ldap/LdapConfig.java` |
| AES 加解密 | `backend/src/main/java/com/taipei/iot/auth/provider/crypto/AuthConfigEncryptor.java` |
| Tenant Config API | `backend/src/main/java/com/taipei/iot/auth/provider/config/` |
| 用戶建立（帶 authType）| `backend/src/main/java/com/taipei/iot/user/service/UserAdminService.java` |
| 建立用戶前端頁面 | `frontend/src/views/admin/CreateUserView.vue` |
