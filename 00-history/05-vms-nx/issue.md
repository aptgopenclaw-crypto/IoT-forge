

---

## 資安疑慮分析

### 🔴 高風險

**1. 明文密碼寫入版控（A02 Cryptographic Failures）**

`application-prod.yml` 與 `application.yml` 直接硬寫：
```yaml
# application-prod.yml
username: admin
password: Iot+12345   # ← 明文密碼進入 git 歷史
```
任何能讀 repo 的人都能拿到 NX Server admin 密碼。

---

**2. NX Admin Token 暴露給所有前端使用者（A01 Broken Access Control）**

`GET /v1/auth/nx/getToken` 用後端的 admin 帳密向 NX 登入，然後把 admin 等級的 `token` 完整回傳給前端：

```java
// NxController.java
jsonObject.addProperty("username", nxServerUsername);  // admin
jsonObject.addProperty("password", nxServerPassword);
JsonNode result = nxHttpService.httppost(nxUrl, ...);
return BaseResponse.success(result);  // ← token 完整傳給每個登入的用戶
```

前端任何已登入的普通使用者都能拿到 NX admin token，進而直接操作 NX Server API（不經過本系統的權限控制）。

---

**3. HLS Proxy 端點無認證（A01 Broken Access Control）**

```java
// HlsProxyController.java
@GetMapping("/v1/noauth/hls/{sessionToken}/ts")   // noauth 路徑
@GetMapping("/v1/noauth/hls/{sessionToken}/master")
// Access-Control-Allow-Origin: *
response.setHeader("Access-Control-Allow-Origin", "*");
```

- `/v1/noauth/**` 路徑被 Spring Security 完全排除驗證
- `sessionToken` 是唯一存取控制，若可被猜測或洩漏，任何人（含未登入者）都能串流影像
- CORS 設 `*` 代表任意網站都可跨域存取這些影像串流

---

### 🟠 中風險

**4. Token 寫入應用 Log（A09 Security Logging Failures）**

多處 `log.info` 直接記錄 `nxToken` 值：
```java
log.info("userId: {} , getAllNxToken By GUID: {}", getActionUserId(), nxToken);
log.info("getNxDeviceimage BY deviceId: {}, nxToken: {}, ...", deviceId, nxToken, ...);
```
Log 檔若被旁人存取（如 Splunk、ELK 未設存取控管），等同 token 外洩。

---

**5. `path` 參數未驗證（A03 Injection / Path Traversal）**

```java
// HlsProxyController.java
String decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8);
// 直接帶入 hlsProxyService 發送請求，未驗證是否為合法 NX 路徑
byte[] m3u8 = hlsProxyService.fetchAndRewriteM3u8(sessionToken, proxyBaseUrl, decodedPath);
```
若 `hlsProxyService` 內部直接拼接 URL 而未限制 hostname，可能被用來做 SSRF（讓後端向內網其他服務發請求）。

---

**6. 所有用戶共用同一 NX admin 帳號（A07 Identification & Authentication Failures）**

NX Server 的 audit log 只會看到同一個 `admin` 帳號在操作，無法追蹤是哪個系統用戶執行了哪個動作。

---

**7. NX Token 無 refresh 機制（A07）**

```ts
// streamStore/index.ts - store 初始化時只取一次
getNXToken().then((res) => { nxToken.value = res.token })
```
Token 過期（依 NX 的 `expiresInS`）後播放直接失敗，但更嚴重的是：沒有強制 logout 機制，使用者的 session 結束後 NX token 也不會被廢除（`delNxToken` API 存在但沒有在登出流程中呼叫）。

---

### 建議修正方向

| 問題 | 建議 |
|---|---|
| 明文密碼 | 改用環境變數或 Vault，從 yml 移除 |
| Token 暴露給前端 | **後端應自己持有 NxToken**，前端請求 HLS 時由後端附加，前端只需攜帶自己的 JWT |
| noauth 端點 | 改用短效、一次性、與用戶 session 綁定的 sessionToken，並在 noauth 層做 rate limit |
| CORS `*` | 限縮為系統自有 domain |
| Log 中的 token | 改為 log token 的前 8 碼（`token.substring(0, 8) + "..."`) |
| path 參數 | 驗證必須以 `/hls/` 開頭且不含 `..` |
| 登出時廢除 NX Token | 在登出 / session invalidation 時呼叫 `DELETE /v1/auth/nx/delToken/{token}` |