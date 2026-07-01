要理解這個設定，我們需要先了解什麼是 **Referrer (Referer)**，以及它為什麼會成為資安漏洞。

### 1. 什麼是 Referrer (Referer)？
當你在「網頁 A」點擊一個連結，跳轉到「網頁 B」時，瀏覽器在請求網頁 B 時，會自動在 HTTP 標頭（Header）中附帶一個 `Referer` 欄位，告訴網頁 B：**「我是從網頁 A 過來的」**。
*(註：HTTP 規範中拼寫為 Referer，少了一個 r，但業界通常讀作 Referrer)*。

### 2. 為什麼會有「敏感資訊洩漏」的風險？
在實際的 Web 開發中，URL 裡經常會包含敏感資訊，例如：
*   **重置密碼的 Token**：`https://your-system.com/reset-password?token=eyJhbGci...`
*   **會話 ID 或 API Key**：`https://your-system.com/dashboard?session_id=abc123`
*   **機密的路徑或用戶 ID**：`https://your-system.com/admin/users/secret-project/1001`

**攻擊場景：**
假設您的系統中有一篇公告，裡面包含了一個導向「外部第三方網站」（例如外部論壇、第三方統計服務、或是被駭客注入的惡意連結）的超連結。
當使用者點擊這個外部連結時，**瀏覽器預設會把「當前頁面的完整 URL（包含路徑和所有查詢參數）」作為 Referer 發送給第三方網站**。
結果就是：第三方網站的伺服器日誌中，清清楚楚地記錄下了您系統使用者的 `token` 或機密路徑，造成嚴重的資訊洩漏。

### 3. Referrer-Policy 的作用
**Referrer-Policy** 就是一個 HTTP 響應標頭，用來告訴瀏覽器：**「當你要跳轉到外部網站時，關於『你從哪裡來』，請按照我規定的規則來透露資訊，不要亂說話。」**

### 4. 程式碼中的設定解析
在您的 `SecurityConfig.java` 中，設定了以下策略：
```java
.referrerPolicy(referrer -> referrer
    .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
```

`STRICT_ORIGIN_WHEN_CROSS_ORIGIN` 是目前業界（包含 Google、GitHub 等）最推薦、也是最安全的**平衡做法**。它的運作規則如下：

1.  **同源請求（Same-Origin）：發送完整 URL**
    *   **情境**：從 `https://a.your-system.com` 跳轉到 `https://b.your-system.com`（同屬您的系統內部）。
    *   **行為**：瀏覽器會發送**完整的 URL**（包含路徑和參數）。
    *   **目的**：確保系統內部的日誌追蹤、路由分析、除錯不受影響。
2.  **跨源請求（Cross-Origin）：只發送 Origin（網域）**
    *   **情境**：從您的系統點擊連結，跳轉到外部網站（如 `https://external-site.com`）。
    *   **行為**：瀏覽器**只會發送 Origin（即 `https://your-system.com`）**，**絕對不會**帶上後面的具體路徑和查詢參數（如 `/reset-password?token=xxx`）。
    *   **目的**：完美阻斷敏感資訊（Token、Session、機密 ID）洩漏給第三方網站。
3.  **安全性降級（HTTPS 跳轉到 HTTP）：完全不發送**
    *   **情境**：您的系統是安全的 HTTPS，但點擊的連結是不安全的 HTTP。
    *   **行為**：瀏覽器會**完全不發送** Referer 標頭。
    *   **目的**：防止敏感資訊在不安全的明文網路中被攔截。

### 總結
這個設定就像是給瀏覽器戴上了 **「防偷窺面罩」**：
在自家地盤（同源）時，可以清楚看到你是誰、從哪條具體路徑來；但一旦要出門（跨源去第三方網站），就只告訴對方 **「我來自您的系統網域」**，絕不透露具體的門牌號碼（路徑）和身上帶的機密文件（URL 參數/Token）。這能有效滿足資安健檢中對於「防止敏感資訊透過 Referer 洩漏」的合規要求。