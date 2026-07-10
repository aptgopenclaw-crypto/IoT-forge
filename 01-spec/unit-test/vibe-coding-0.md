在 **Vibe Coding（快速迭代、AI 輔助、功能優先）** 的階段，設計單元測試的確是個大難題。最大的痛點在於：**需求頻繁變動，程式碼重構頻繁，如果測試寫得太「死」、太早，會變成重構的絆腳石；但如果完全不寫，重構時又缺乏安全感。**

針對 Vibe Coding 的節奏，我建議你採用 **「合約優先、由外而內」** 的設計策略。不要急著覆蓋 100% 的程式碼，而是先鎖定 **「輸入（Input）」** 與 **「輸出（Output / 副作用）」**。

以下是我為你整理的一套**針對 Service 層的實戰測試設計框架**，直接套用你的 `AnnouncementReadService` 來舉例：

---

### 🧠 核心心法：把 Method 視為「黑盒子」

在 Vibe Coding 階段設計測試時，**先不要看 Method 內部的程式碼怎麼寫**（避免 bias），而是先問自己三個問題：

1. **給定什麼輸入**（參數、當前使用者 Session、TenantContext）？
2. **預期回傳什麼**（Return Value）？
3. **預期產生什麼副作用**（Side Effect，例如呼叫了 Repository 的某個方法）？

---

### 🛠️ 設計 4 大步驟（適用於任何 method）

#### Step 1：定義「成功路徑（Happy Path）」—— 證明功能能用
這是第一個要寫的測試，也是 Vibe Coding 最核心的目標。

- **目標**：驗證在一切正常的情況下，Method 不會拋錯，且回傳正確的資料。
- **設計要點**：只 Mock 最必要的 Repository 回傳值。
- **例如 `getReadStats`**：
  - Given：公告存在（`scope='ALL'`），Mock `countAudienceAll` 回傳 100，`countReadAll` 回傳 30。
  - When：呼叫 `getReadStats(1L)`。
  - Then：回傳的 `totalAudience` 是 100，`readCount` 是 30，`readRatio` 是 0.3000。

#### Step 2：定義「分支邏輯（Branch Logic）」—— 驗證 if/else 走對
Vibe Coding 最容易寫出邏輯漏洞，測試要確保每個 `if` 都有被觸發。

- **目標**：確保 Service 根據不同條件，呼叫了正確的 Repository 方法。
- **設計要點**：使用 `verify` 來確認呼叫的對象。
- **例如 `getReadStats`**：
  - 情境 A（`scope='DEPT'`）：驗證呼叫了 `countAudienceDept` 和 `countReadDept`，**且沒有**呼叫 `countAudienceAll`。
  - 情境 B（`scope='ALL'`）：驗證**沒有**呼叫 `announcementDeptRepository.findByAnnouncementId`（因為 ALL 不需要查部門關聯表，省效能）。

#### Step 3：定義「安全邊界（Security Boundary）」—— 驗證權限與租戶
這是你的系統中最敏感的部分，測試務必涵蓋。

- **目標**：確保非授權或跨租戶的請求被擋下。
- **設計要點**：驗證 `BusinessException` 是否拋出，且關鍵的寫入方法（如 `markAsRead`）**絕對沒有被呼叫 (`never()`)**。
- **例如 `markAsRead`**：你已經寫了很棒的例子：當 `existsById` 回傳 false 時，驗證拋出 `ANNOUNCEMENT_NOT_FOUND`，且 `verify(announcementReadRepository, never()).markAsRead(...)`。

#### Step 4：定義「邊界值（Boundary Values）」—— 處理 null 與空集合
Vibe Coding 最常噴 `NullPointerException` 的地方。

- **目標**：驗證傳入 null 或回傳空集合時，程式不會爆炸。
- **設計要點**：`null` 部門 ID、`null` 關鍵字、空 List。
- **例如 `getReadStats`**：
  - 當 `scope='DEPT'` 但 `announcementDeptRepository.findByAnnouncementId` 回傳空 List 時，`totalAudience` 應為 0，而不是去執行 `countAudienceDept(..., emptyList)` 導致噴 SQL 錯誤。

---

### 🧩 進階技巧：如何面對「重構頻繁」的困境

在 Vibe Coding 階段，Method 名稱或內部邏輯常變。為了讓測試不變成「重構的絆腳石」，請掌握以下 3 個原則：

1. **盡量不要 Mock 靜態方法（Utility）**：
   - 在你的測試中，`SecurityContextUtils.getUserInfo()` 是靜態方法。建議**不要**用 PowerMock 去 mock 它，而是在 `@BeforeEach` 直接塞真實的 `SecurityContext`（你已經這麼做了，這是好習慣！），這樣無論將來 `getUserInfo` 內部怎麼改，測試都不會受影響。

2. **斷言「結果」多於斷言「過程」**：
   - 對 `getReadStats`，優先斷言 `readRatio` 計算的 `BigDecimal` 是否正確（結果），少斷言內部調用了幾次 `LocalDateTime.now()`（過程）。只有**安全相關**（如跨租戶）才需要嚴格斷言過程（`verify`）。

3. **優先採用「參數化測試 (@ParameterizedTest)」**：
   - 當你覺得同一個邏輯有很多種輸入變化（例如 status 有 5 種），別複製貼上 5 個測試，改用 `@CsvSource` 或 `@EnumSource`。這樣當需求變動時，只需改動測試資料表格，維護成本極低。

---

### 🤖 給 AI（Vibe Coding）的具體下指令範例

既然你在 Vibe Coding，你可以直接對 AI 下達這樣的指令，讓它幫你生成高品質的測試框架：

> "請為 `AnnouncementReadService.getReadStats` 設計單元測試。請依照下列順序產生測試方法：
> 1. 情境：公告為 ALL 類型，計算比例成功。
> 2. 情境：公告為 DEPT 類型，且有關聯部門，計算比例成功。
> 3. 情境：公告為 DEPT 類型，但無關聯部門，受眾數應回傳 0。
> 4. 情境：使用者為 DEPT_ADMIN 但不是建立者，應拋出 PERMISSION_DENIED。
> 請使用 `@Nested` 將『計算邏輯』與『權限邏輯』分組，且只 mock Repository。"

---

### 📊 總結：你該專注測試的優先級

| 優先級 | 測試目標 | 對應你的 Method | 策略 |
| :--- | :--- | :--- | :--- |
| **P0 (最重要)** | **Happy Path + 權限/租戶防護** | `markAsRead` (防跨租戶) <br> `getReadStats` (比例計算) | 確保核心業務不崩潰，資料不外洩 |
| **P1** | **邊界值 (null / empty)** | `getUnreadCount` (deptId null) <br> `getReadStats` (部門清單 empty) | 避免生產環境噴 NPE |
| **P2** | **重構保護 (邏輯分支)** | `markAllAsRead` (確認使用正確 tenantId) | 確保 AOP 或 Context 傳遞正確 |
| **P3 (可不寫)** | **Getter/Setter 或極簡單 CRUD** | 僅呼叫 `save()` 無任何邏輯 | 依賴整合測試或直接跳過 |

你現在的 `AnnouncementReadServiceTest` 其實已經抓到 P0 和 P1 的精髓了（特別是針對租戶的嚴苛測試）。接下來的 Vibe Coding，**先寫 P0 測試再寫 Code（或同時進行）**，你會發現重構時膽子會大很多！如果針對某個特定 method（如 `getReadStats` 的比例計算邏輯）不知道如何設計邊界案例，可以貼出來，我幫你條列測試清單。