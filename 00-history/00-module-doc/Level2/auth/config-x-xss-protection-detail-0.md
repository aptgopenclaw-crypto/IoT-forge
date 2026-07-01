## X-XSS-Protection：為什麼要「主動關閉」一個安全功能？

這可能是整個 SecurityConfig 中**最違反直覺**的設定。乍看之下，關閉一個名字叫「XSS 保護」的功能，似乎是在降低安全性。但事實上，**關閉它反而是更安全的做法**。

---

### 1. X-XSS-Protection 是什麼？

它是早期瀏覽器（Chrome、IE、舊版 Safari）內建的一道 **「自動 XSS 過濾器」**，稱為 **XSS Auditor（XSS 審計器）**。

它的運作原理很簡單：
> 瀏覽器檢查 URL 中的參數或表單提交的內容，如果發現這些內容和頁面中的 HTML/JS 結構「長得很像」（例如 `<script>alert(1)</script>`），瀏覽器就會**自動攔截或修改**這段內容，防止 XSS 執行。

設定值有三種：
| 值 | 行為 |
|---|---|
| `0` | **關閉** XSS Auditor |
| `1` | **開啟**，嘗試「修復」可疑內容（刪除危險部分） |
| `1; mode=block` | **開啟**，直接阻止整個頁面渲染 |

---

### 2. 為什麼要顯式關閉它？（它「有缺陷」在哪裡？）

這個 XSS Auditor 看似好心，但它有三個**致命的設計問題**：

#### 問題一：它本身就是一個攻擊武器 🔫

XSS Auditor 的「修復」邏輯是可以被駭客**反向利用**的。

**攻擊場景舉例：**
假設您的頁面有一段合法的 JS：
```javascript
var apiKey = "sk-abc123secret";
console.log(apiKey);
```

駭客在 URL 中故意塞入一段**部分匹配**的內容：
```
https://your-system.com/page?param=var+apiKey+%3D+%22sk-abc123secret%22
```

XSS Auditor 看到 URL 中的內容和頁面中的 JS「長得很像」，它會認為這是一個 XSS 攻擊，於是**自動刪除頁面中的那行 JS**。

結果：駭客成功地讓瀏覽器**刪除了您頁面中合法的重要程式碼**，導致頁面功能異常，甚至可能藉此繞過某些前端安全檢查。

這叫做 **XSS Auditor 導致的資訊洩漏 / 功能破壞攻擊**。

#### 問題二：它給人「虛假的安全感」 😴

開發者看到瀏覽器有內建的 XSS 保護，可能就不會認真去做真正的 XSS 防禦（如輸入過濾、輸出編碼、CSP）。但 XSS Auditor **只能防護最簡單的反射型 XSS**，對儲存量 XSS、DOM-based XSS 幾乎毫無作用。

#### 問題三：主流瀏覽器已經棄用它 🗑️

| 瀏覽器 | 處理方式 |
|---|---|
| **Chrome** | 2019 年（Chrome 78）**完全移除**了 XSS Auditor |
| **Edge (Chromium)** | 跟隨 Chrome，已移除 |
| **Firefox** | **從未實作過**這個功能 |
| **Safari** | 2020 年後也逐步棄用 |

既然現代瀏覽器都已經不用它了，如果我們不顯式關閉，**只有那些過時的舊版瀏覽器會啟用這個有缺陷的功能**，反而帶來風險。

---

### 3. 程式碼中的設定

```java
// N-7: X-XSS-Protection: 0 — 顯式關閉舊瀏覽器 buggy XSS auditor（OWASP 建議）
.xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.DISABLED))
```

這行程式碼會在 HTTP 響應中加上：
```
X-XSS-Protection: 0
```

明確告訴所有瀏覽器：**「不要啟用你的內建 XSS 審計器，我自己有更可靠的防禦機制。」**

---

### 4. 那現在靠什麼防禦 XSS？

答案就是同一份設定中已經配置的 **CSP（Content Security Policy）**：

| 舊方案（已淘汰） | 新方案（現代標準） |
|---|---|
| X-XSS-Protection (XSS Auditor) | **CSP (Content Security Policy)** |
| 瀏覽器自動「猜測」並攔截 | 開發者明確定義白名單規則 |
| 容易被反向利用 | 極難被繞過 |
| 只能防簡單反射型 XSS | 能防反射型、儲存型、DOM-based XSS |
| 各瀏覽器行為不一致 | W3C 標準，行為一致 |

---

### 總結

這行設定是一個 **「拆除壞掉的舊鎖，換上新鎖」** 的動作：

> 顯式關閉有缺陷且已被主流瀏覽器棄用的 XSS Auditor（舊鎖），同時依靠同一設定中更強大、更可靠的 **CSP**（新鎖）來防禦 XSS。

這完全符合 **OWASP 官方建議**以及資安規範（代號 N-7）的要求，是經過深思熟慮的安全決策，而非疏忽。