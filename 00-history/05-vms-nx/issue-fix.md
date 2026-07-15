Updated todo list

## 理想的三層設計

---

### 核心原則

> **前端只認識後端，後端只認識 NX，NX token 永遠不離開後端。**

---

### 現況 vs 理想對比

Updated todo list

Mermaid 不支援在同一張圖裡並排兩個獨立流程。拆成兩張獨立圖：

---

### 現況（有問題）

```mermaid
sequenceDiagram
    participant FE as 前端
    participant BE as 後端
    participant NX as NX Server

    FE->>BE: GET /v1/auth/nx/getToken (JWT)
    BE->>NX: POST /login/sessions (admin / Iot+12345)
    NX-->>BE: { token: "abc123..." }
    BE-->>FE: ⚠️ 把 NX admin token 回傳給前端

    FE->>FE: nxToken.value = "abc123..." 存在 Pinia

    FE->>BE: GET /v1/auth/nx/{deviceId}/hls<br/>Header: x-runtime-guid: abc123...
    BE->>NX: 轉發 + x-runtime-guid: abc123...
    NX-->>BE: m3u8
    BE-->>FE: m3u8

    FE->>BE: GET /v1/noauth/hls/{sessionToken}/ts ← 無需認證
    BE->>NX: 轉發
    NX-->>BE: .ts bytes
    BE-->>FE: .ts bytes
```

---

### 理想設計

```mermaid
sequenceDiagram
    participant FE as 前端
    participant BE as 後端（持有 NX token）
    participant NX as NX Server

    Note over BE,NX: 後端啟動 / 定時自動刷新
    BE->>NX: POST /login/sessions (admin帳密)
    NX-->>BE: { token, expiresInS }
    BE->>BE: token 存入記憶體/Redis<br/>排程自動刷新，永不外傳

    Note over FE,BE: 使用者播放影像
    FE->>BE: GET /v1/auth/nx/{deviceId}/hls<br/>Header: Authorization: Bearer {JWT}
    BE->>BE: 驗證 JWT + 檢查 device 權限
    BE->>NX: 用內部 NX token 建立 HLS session
    NX-->>BE: session 建立成功
    BE->>BE: 產生 sessionToken（綁定 userId）
    BE-->>FE: { sessionToken } ← 非 NX token

    Note over FE,BE: hls.js 取得影像片段
    FE->>BE: GET /v1/auth/hls/{sessionToken}/ts?path=...<br/>Header: Authorization: Bearer {JWT}
    BE->>BE: 驗證 JWT + sessionToken 屬於此用戶
    BE->>NX: 代理請求，後端自動附加 NX token
    NX-->>BE: .ts bytes
    BE-->>FE: .ts bytes
```

---

### 理想流程設計

```mermaid
sequenceDiagram
    participant FE as 前端
    participant BE as 後端
    participant NX as NX Server

    Note over BE,NX: 啟動時 / 定時自動刷新
    BE->>NX: POST /login/sessions (admin帳密)
    NX-->>BE: { token, expiresInS }
    BE->>BE: 儲存至 memory/Redis<br/>排程 expiresInS × 0.9 後自動刷新

    Note over FE,BE: 使用者播放影像
    FE->>BE: GET /v1/auth/nx/{deviceId}/hls<br/>Header: Authorization: Bearer {JWT}
    BE->>BE: 1. 驗證 JWT（使用者身份）<br/>2. 檢查該用戶是否有權限看此 device<br/>3. 用內部持有的 NX token 向 NX 建立 HLS session<br/>4. 產生短效 sessionToken（UUID + 綁定 userId）
    BE-->>FE: { sessionToken } ← 不是 NX token

    Note over FE,BE: hls.js 取得影像片段
    FE->>BE: GET /v1/auth/hls/{sessionToken}/ts?path=...<br/>Header: Authorization: Bearer {JWT}
    BE->>BE: 驗證 JWT + sessionToken 屬於此用戶
    BE->>NX: 代理請求 + 附加 NX token（後端自動加）
    NX-->>BE: ts segment bytes
    BE-->>FE: ts segment bytes
```

---

### 各層職責

| 層 | 持有的憑證 | 對外暴露 | 職責 |
|---|---|---|---|
| **前端** | 自己的 JWT | 是 | 只帶 JWT；不知道 NX 的存在 |
| **後端** | NX admin token（記憶體/Redis） | **否** | 驗證使用者身份 + 權限；代理所有 NX 請求；管理 NX token 生命週期 |
| **NX Server** | — | 不對外 | 只接受來自後端的請求 |

---

### 需要的具體改動

**後端：移除 `getToken` API**
```java
// ❌ 刪除這個 endpoint，NX token 不應給前端
@GetMapping("/v1/auth/nx/getToken")
public BaseResponse<Object> getNxToken() { ... }
```

**後端：NX Token 集中管理**
```java
@Component
public class NxTokenManager {
    private volatile String cachedToken;
    
    @Scheduled(fixedDelayString = "#{nxTokenManager.refreshIntervalMs()}")
    public void refreshToken() {
        // 向 NX 登入，更新 cachedToken
    }
    
    public String getToken() { return cachedToken; }
}
```

**後端：HLS Proxy 端點加回 JWT 驗證**
```java
// ❌ 現況：/v1/noauth/hls/{sessionToken}/ts  → 完全無認證
// ✅ 改為：/v1/auth/hls/{sessionToken}/ts   → 需要 JWT
//    並驗證 sessionToken 是否屬於目前的 JWT 用戶
```

**後端：sessionToken 需綁定使用者**
```java
public String createSession(String userId, String deviceId, String hlsPath) {
    String sessionToken = UUID.randomUUID().toString();
    sessions.put(sessionToken, new HlsSession(
        userId,        // ← 綁定 userId
        deviceId,
        hlsPath,
        nxTokenManager.getToken(),  // ← NX token 在後端持有，不外傳
        Instant.now().plusSeconds(SESSION_TTL_SECONDS)
    ));
    return sessionToken;
}
```

**前端：移除 NX Token 相關邏輯**
```ts
// ❌ 刪除
import { getNXToken } from '@/api/nx'
getNXToken().then((res) => { nxToken.value = res.token })

// ❌ 刪除 streamStore 中的 nxToken ref
// ❌ NXStreamPlayer 不再需要傳入 nxToken 參數
// ✅ hls.js 只需帶 JWT，不需要 x-runtime-guid
```

---

### 改動後的安全邊界

```
Internet
   │
   ▼
[前端] ── JWT ──▶ [後端 /v1/auth/**]
                      │
                      │ 內網，NX token 永遠在此層
                      ▼
                 [NX Server]（不對外暴露）
```

- 即使前端 JWT 被竊，攻擊者也只能呼叫後端 API，無法直接操作 NX
- NX admin 密碼和 token 完全不出現在前端、log（需移除 token logging）、網路封包中
- 每個 HLS session 都有使用者歸屬，可完整稽核「誰在什麼時間看了哪支攝影機」