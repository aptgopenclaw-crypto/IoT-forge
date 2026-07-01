**Permissions-Policy（權限政策）** 是現代瀏覽器提供的一項強大安全機制，你可以把它理解為瀏覽器功能的 **「權限管理員」** 或 **「硬體開關」**。

它的前身是 `Feature-Policy`，主要目的是**精細控制網頁（以及網頁中嵌入的 iframe）可以使用哪些瀏覽器的原生 API 與硬體功能**。

---

### 1. 為什麼需要 Permissions-Policy？（潛在的資安風險）

現代瀏覽器為了支援豐富的 Web 應用，開放了許多強大的 API，例如：
*   **硬體/感測器**：攝影機 (Camera)、麥克風 (Microphone)、GPS 定位 (Geolocation)、藍牙、USB。
*   **隱私/行為**：剪貼簿讀寫、全螢幕、自動播放影片、網路支付 (Payment Request)。

**攻擊場景：**
假設您的網站不幸被注入了 **XSS（跨站腳本攻擊）**，或者您的網頁嵌入了**不受信任的第三方 iframe**（例如來路不明的廣告、外掛）。
如果沒有 Permissions-Policy 保護，這些惡意程式碼可以在背景偷偷執行以下行為：
1.  **偷偷開啟麥克風/攝影機**：在使用者不知情的情況下錄音或錄影。
2.  **竊取精準位置**：不斷呼叫 GPS API 追蹤使用者的物理位置。
3.  **惡意扣款/釣魚**：觸發 Payment API 彈出假的付款請求，誘騙使用者輸入信用卡號。
4.  **耗盡裝置資源**：惡意腳本瘋狂讀取手機的加速計或陀螺儀資料，導致手機發燙、耗電甚至當機。

### 2. 程式碼中的設定解析

在您的 `SecurityConfig.java` 中，設定了以下策略：
```java
.addHeaderWriter(
    new PermissionsPolicyHeaderWriter("camera=(), microphone=(), geolocation=(), payment=()"))
```

這裡使用了 Permissions-Policy 的語法，**`()` 代表「空集合」（Empty Allowlist），也就是「不允許任何來源（包含網頁自己本身）使用」**。

逐一拆解如下：
*   **`camera=()`**：**全面禁用攝影機**。網頁中的任何 JS 程式碼都無法呼叫 `navigator.mediaDevices.getUserMedia({video: true})`。
*   **`microphone=()`**：**全面禁用麥克風**。無法進行任何網頁錄音或語音通話功能。
*   **`geolocation=()`**：**全面禁用 GPS 定位**。無法呼叫 `navigator.geolocation.getCurrentPosition()` 獲取使用者位置。
*   **`payment=()`**：**全面禁用 Payment Request API**。防止惡意腳本偽造瀏覽器原生的支付彈窗。

*(補充：如果未來業務需要，例如系統本身有合法的「掃碼功能」或「地圖定位」，可以將其改為 `camera=(self)`，代表「只允許同源網域使用，禁止第三方 iframe 使用」。但目前的設定 `()` 是最嚴格的「一刀切」全面封殺。)*

### 3. Permissions-Policy 與 CSP 的黃金組合

在前面我們討論過 **CSP (Content Security Policy)**。這兩者通常搭配使用，構成現代 Web 安全的雙重防線：

*   **CSP 負責「防外」**：控制 **「程式碼能不能載入與執行」**。（例如：不允許載入外部惡意 JS，不允許執行 inline script）。
*   **Permissions-Policy 負責「防內」**：控制 **「程式碼執行後，能不能呼叫敏感硬體」**。（即使駭客成功注入了 JS 程式碼，當他試圖呼叫麥克風 API 時，瀏覽器會直接拒絕並拋出錯誤）。

### 總結
這個設定確保了您的 IoT/後台管理系統**絕對不需要**（也不應該）在背景偷偷使用使用者的攝影機、麥克風或 GPS。這不僅保護了使用者的隱私，也滿足了嚴格的資安健檢要求（防止 API 被濫用），是企業級 Web 應用非常標準且必要的安全標頭配置。