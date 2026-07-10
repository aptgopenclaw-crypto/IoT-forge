針對 `AnnouncementReadService` 的單元測試設計，我們可以從「**測試目標**」、「**依賴隔離**」、「**場景覆蓋**」與「**驗證策略**」四個面向來規劃。以下是以你提供的程式碼為基礎，歸納出的完整設計原則與實務建議。

---

## 一、單元測試的核心目標

- **只測 Service 層的商業邏輯**，不測 Repository 或資料庫行為。
- **隔離所有外部依賴**（Repository、工具類、靜態方法），使用 Mock 模擬其行為。
- **驗證在各種輸入與情境下，Service 的回應與行為是否符合預期**，包括：
  - 正常流程（Happy Path）
  - 異常流程（BusinessException）
  - 邊界條件（null、空集合、極值）
  - 安全性（租戶隔離、權限檢查）

---

## 二、測試設計基本步驟（每支測試方法通用模板）

| 步驟 | 動作 | 說明 |
|------|------|------|
| 1. **設定測試環境** | `setUp` / `@BeforeEach` | 初始化 TenantContext、SecurityContext（使用者身份、部門、權限） |
| 2. **模擬依賴行為** | `when(...).thenReturn(...)` | 針對 Repository 或工具類方法，給定預期的回傳值或例外 |
| 3. **執行被測方法** | `service.methodUnderTest(...)` | 呼叫 Service 的真實方法 |
| 4. **斷言結果** | `assertEquals`, `assertThrows`, `assertTrue` | 檢查回傳值或拋出的例外是否符合預期 |
| 5. **驗證互動** | `verify(mock, times(n)).method(...)` | 確認相依元件是否被正確呼叫、參數是否正確、呼叫次數是否符合 |

---

## 三、針對各方法設計測試案例（範例與要點）

### 1. `getUnreadCount()`
- **正常**：使用者有部門 ID → Repository 回傳某數字 → 回應正確。
- **邊界**：使用者部門 ID 為 `null` → Service 應傳 `-1L` 給 Repository。
- **安全性**：確認 `countUnread` 被呼叫時，使用的 `userId` 與 `deptId` 來自 `SecurityContext`（你的測試已涵蓋）。

### 2. `markAsRead(Long announcementId)`
- **正常**：公告存在 → 呼叫 Repository 的 `markAsRead`。
- **冪等性**：重複呼叫兩次 → 不拋出例外，且 `markAsRead` 被呼叫兩次（由 Repository 的 `ON CONFLICT DO NOTHING` 保證冪等，Service 層無需特殊處理）。
- **異常**：公告不存在（或屬於其他租戶）→ 拋出 `BusinessException(ANNOUNCEMENT_NOT_FOUND)`，且**絕不**呼叫 `markAsRead`（防止跨租戶寫入）。
- **關鍵驗證**：確認 `announcementRepository.existsById(announcementId)` 被呼叫，且 `markAsRead` 的參數正確（用戶 ID 來自 `SecurityContext`）。

### 3. `markAllAsRead()`
- **正常**：使用者有部門 → 呼叫 Repository 的 `markAllAsRead(userId, tenantId, deptId)`。
- **邊界**：部門為 `null` → 傳 `-1L`。
- **租戶安全（最重要）**：
  - 必須使用 `TenantContext.getCurrentTenantId()` 的值，不得從其他來源取得（測試已用 `ArgumentCaptor` 驗證）。
  - 若 `TenantContext` 為 `null` → Service 允許傳 `null`（測試也涵蓋），雖然資料庫會因 `WHERE tenant_id = NULL` 導致無任何插入，但屬於 **fail-closed**（安全失敗），可接受。

### 4. `getReadStats(Long announcementId)`
- **正常（ALL 公告）**：公告 `scope='ALL'` → 計算總受眾人數、已讀人數，計算比例。
- **正常（DEPT 公告）**：公告 `scope='DEPT'` → 取得關聯部門清單，並基於部門計算受眾與已讀。
- **邊界（DEPT 但無部門關聯）**：`announcementDeptRepository` 回傳空清單 → 總受眾與已讀皆為 0，比例 0。
- **權限檢查**：使用者為 DEPT_ADMIN（`DataScope=DEPT`）且不是該公告的創建者 → 拋出 `PERMISSION_DENIED`。ADMIN 可看任何公告。
- **驗證**：確認 `announcementDeptRepository.findByAnnouncementId()` 僅在 `scope='DEPT'` 時被呼叫。

### 5. `getUnreadUsers(Long announcementId, String keyword, int page, int size)`
- **正常（ALL）** → 調用 `statsRepository.findUnreadUsersAll`。
- **正常（DEPT）** → 調用 `statsRepository.findUnreadUsersDept`，且傳入正確部門清單。
- **邊界（DEPT 但無部門）** → 回傳空分頁（不呼叫 Repository）。
- **權限檢查**：同 `getReadStats`（共用 `loadAndCheckManage`）。
- **分頁與關鍵字**：測試關鍵字為 `null`、空白、正常值，驗證傳遞無誤。

---

## 四、你的既有測試的優點與可改進之處

### ✅ 優點（值得參考的設計）

1. **環境隔離**：使用 `@ExtendWith(MockitoExtension.class)` 與 `@Mock`，只測 Service，不啟動 Spring Context。
2. **安全意識**：特別測試了跨租戶防護（`markAsRead_crossTenantAnnouncementId_throwsNotFound`、`markAllAsRead_usesTenantContextStrictly`），這是高風險業務的核心。
3. **冪等性測試**：對 `markAsRead` 測試重複呼叫不拋錯。
4. **邊界條件**：覆蓋 `deptId = null` 的情境。
5. **驗證互動**：使用 `verify` 確認方法被呼叫，並用 `ArgumentCaptor` 鎖定租戶 ID。

### ⚠️ 可以補充的測試案例（提升覆蓋率與信心）

| 方法 | 遺漏場景 | 建議補充 |
|------|---------|---------|
| `markAsRead` | 公告存在但屬於其他租戶（雖已由 `existsById` 防護，但可模擬 `existsById` 回傳 `false`） | 已有 `crossTenantAnnouncementId` 涵蓋。 |
| `getReadStats` | 當 `requiresAck` 為 `true` 時，應在回應中標示 | 測試 `requiresAck = true` 與 `false` 的 case，檢查回應的 `requiresAck` 欄位。 |
| `getReadStats` | 當總受眾人數為 0 時，比例應為 `0.0000`（而非 `NaN`） | 模擬 `countAudienceAll` 回傳 0，驗證 `readRatio` 為 `0`。 |
| `getReadStats` | 當 `DataScope=ALL` 的使用者（ADMIN）可看任何公告 | 可模擬 ADMIN 與 DEPT_ADMIN 兩種角色，驗證權限邏輯。 |
| `getUnreadUsers` | 關鍵字搜尋有 `%` 或 `_` 等特殊字元時，Repository 是否正確轉義 | Service 層僅傳遞 `safeKeyword`，建議測試 `keyword` 包含空白、前後空白等清理邏輯。 |
| `getUnreadUsers` | 分頁參數為負數或極大值時，Service 是否正確傳遞（通常由 Controller 層處理，但 Service 也應防禦） | 可測試 `page=-1` 時，Spring Data 會如何反應（建議業務層盡量不依賴框架，若由 Controller 保證則可不測）。 |
| `loadAndCheckManage` | 公告存在但使用者為 `null`（即未登入）— 但此情況應由 Controller 層攔截，若 Service 直接暴露可能需測 | 可考慮新增測試：模擬 `SecurityContextUtils.getUserInfo()` 拋出例外。 |
| 所有方法 | 當 `TenantContext` 未設定時的失敗模式 | 已有 `markAllAsRead` 測試，其他方法也應確保傳入 `null` 時的處理不拋 NPE，而是拋出明確錯誤或回傳空。 |

---

## 五、推薦的測試設計模式（提升維護性）

- **使用工廠方法**：在測試類中建立 `createUserInfo()`、`createAnnouncement()` 等輔助方法，避免重複建立物件的 boilerplate。
- **採用 `@Nested` 分組**：將不同情境（如「正常流程」、「異常流程」、「邊界條件」）用內嵌類別組織，提升可讀性。
- **明確的測試命名**：遵循 `should_[expectedBehavior]_when_[condition]` 格式，例如 `should_returnUnreadCount_whenUserHasDept()`。
- **避免過度 Mock**：若某個依賴的方法總是固定回傳，可考慮使用 `@MockBean` 或直接設定預設行為（如你用了 `LENIENT`，避免不必要的 stubbing 錯誤）。

---

## 六、總結：撰寫單元測試的核心理念

| 原則 | 說明 |
|------|------|
| **隔離性** | 只測當前類別，所有依賴都用 Mock。 |
| **可重複性** | 測試不依賴外部狀態（如資料庫時間），使用 `any()` 或固定時間。 |
| **全面性** | 涵蓋成功、失敗、邊界、安全（租戶、權限）情境。 |
| **可讀性** | 測試名稱清楚，步驟分明（Given-When-Then）。 |
| **實用性** | 測試失敗時能快速定位問題（例如使用 `ArgumentCaptor` 驗證關鍵參數）。 |

你的現有測試已經具備了很高的品質，特別是在租戶安全方面做了嚴謹的驗證，這在實際生產環境中極為重要。只要再補上 `getReadStats` 與 `getUnreadUsers` 的更多邊界案例，就能達到很高的信心水準。如果需要我協助撰寫特定測試案例的程式碼片段，也可以提出！