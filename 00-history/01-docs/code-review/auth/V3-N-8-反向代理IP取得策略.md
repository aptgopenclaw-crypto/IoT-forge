這是一個非常典型的**「部署環境差異導致資安防線失效」**的通報。簡單來說：**您的程式碼在本地開發時能正確取得使用者 IP，但一旦放到 Nginx、AWS ALB 或 Cloudflare 等反向代理/負載平衡器後面，所有取得的 IP 都會變成代理伺服器的 IP，導致限流、稽核、資安監控全面失效。**

以下為您拆解技術原理、實際影響與修復方式：

### 🔍 為什麼會發生這個問題？
1. **`request.getRemoteAddr()` 的局限性**
   在本地開發時，瀏覽器直接連線到 Spring Boot，`getRemoteAddr()` 會拿到使用者電腦的真實 IP。
   但在正式環境，流量通常會先經過反向代理（Nginx / LB / CDN）。代理伺服器會建立一條**新的 TCP 連線**轉發給您的應用，此時 `getRemoteAddr()` 拿到的只會是**代理伺服器的內網 IP**（例如 `10.x.x.x` 或 `127.0.0.1`）。

2. **真實 IP 其實藏在 HTTP Header 裡**
   標準的反向代理會把使用者的真實 IP 寫在 `X-Forwarded-For` (XFF) 或 `X-Real-IP` Header 中。但 Spring Boot **預設不會自動讀取這些 Header 來覆寫 `getRemoteAddr()`**，必須手動開啟。

3. **您的程式碼寫法**
   審計指出 `RateLimitInterceptor`、`AuthServiceImpl`、`SecurityLogger` 全鏈都使用 `request.getRemoteAddr()`。這在架構上是**正確的寫法**（不該到處硬解析 Header），但前提是必須讓 Spring 的過濾器先介入處理 Header。

### ⚠️ 不修正的實際風險（為什麼是 🟠 部署前必做？）
一旦上線到代理環境後，會發生以下連鎖效應：
| 模組 | 失效現象 | 資安/營運影響 |
|:---|:---|:---|
| `RateLimitInterceptor` (API 限流) | 所有使用者的 IP 都顯示為同一個 Proxy IP | **限流直接失效**。攻擊者可用無數台設備發送請求，但伺服器認為是同一台機器，觸發不了頻率限制。 |
| `AuthServiceImpl.recordSession` / `logLoginEvent` | 登入紀錄、Session 紀錄的 `ip` 欄位全部變成 Proxy IP | **稽核日誌失去追蹤價值**。發生暴力破解或帳號異常登入時，您完全不知道攻擊者從哪來，無法封鎖或通報。 |
| `SecurityLogger` (資安事件) | 所有 Warning/Error 的來源 IP 坍縮 | 資安監控儀表板（如 ELK、Splunk）無法繪製威脅地圖，異常偵測規則（如：同一 IP 短時間登入多個帳號）全部誤報或漏報。 |

### 🛠️ 如何修復？（照做即可，無需大改 Java 程式碼）
審計建議的解法非常標準且正確，只需兩步驟：

#### 1. Spring Boot 端：開啟 Forwarded Header 解析
在您的 `application.yml` 的 `server:` 區塊下加入：
```yaml
server:
  port: 8080
  forward-headers-strategy: NATIVE  # 👈 新增這行
```
* **作用**：啟用 Spring Boot 內建的 `ForwardedHeaderFilter`（或委派給 Tomcat 原生支援）。它會自動讀取 `X-Forwarded-For` 等 Header，並將**真實 IP 覆寫回 `request.getRemoteAddr()`**。
* **好處**：您的 `RateLimitInterceptor.java` 和其他 Service **一行 Java 都不用改**，繼續呼叫 `getRemoteAddr()` 就會自動拿到正確的使用者 IP。

#### 2. Nginx / 反向代理端：信任來源並防偽造
僅僅開啟 Spring 設定是不夠的。如果攻擊者知道您依賴 `X-Forwarded-For`，他可以自行在請求中帶入假的 Header 來偽造 IP，繞過限流或隱藏行蹤。
必須在 Nginx 設定中嚴格限制誰可以設定 XFF：
```nginx
# Nginx 範例
set_real_ip_from 10.0.0.0/8;      # 信任內網 LB / Proxy 網段
set_real_ip_from 172.16.0.0/12;
set_real_ip_from 192.168.0.0/16;
real_ip_header X-Forwarded-For;
real_ip_recursive on;
```
這樣 Nginx 只會接受來自可信 Proxy 的 XFF Header，外部直接打進來的請求若自帶 XFF 會被忽略或修正。

### 💡 總結
* **這不是程式邏輯錯誤**，而是**框架預設行為與部署架構不匹配**的典型問題。
* **修復成本極低**：只需在 `application.yml` 加一行設定，並確保 Nginx/LB 有正確轉發 Header。
* **為什麼列為 Release Blocker？** 因為在開發環境完全測不出來，但一上正式機，**限流、稽核、資安日誌三大防護會瞬間同時失效**，屬於典型的「環境切換導致資安控制措施歸零」，必須在發布前完成。