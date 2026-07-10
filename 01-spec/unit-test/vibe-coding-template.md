

---

# [功能模組名稱] 需求規格書

> **FC 編號**：______（例如：F1, F2, F3...）
> **所屬專案**：______

> **AI Agent 指令**：請依據此文件中的「需求敘述」、「Use Case」與「功能完成條件（測試合約）」實作完整的程式碼與對應的單元測試。測試必須涵蓋 Happy Path、邊界值與安全防護（權限 / 租戶隔離）。

---

## 1. 需求概述 (High-Level Goal)
> 用 1~2 句話說明此功能要解決什麼問題、達到什麼目的。

**範例**：提供使用者「全部標為已讀」功能，讓使用者可一鍵清除所有未讀公告的紅點提示，提升操作效率。

**請填寫**：
```
[在此填寫功能的整體目標]
```

---

## 2. Use Case (業務流程)
> 定義誰（Actor）在什麼條件下（Precondition）做什麼動作，以及系統如何回應（正常/替代流程）。

| 項目 | 說明 |
| :--- | :--- |
| **Actor** | [誰使用這個功能，例如：一般使用者、部門管理員] |
| **Precondition** | [執行前必須滿足的條件，例如：使用者已登入、租戶 Context 已設定] |
| **正常流程 (Happy Path)** | 1. [步驟1]<br>2. [步驟2]<br>3. [系統回應] |
| **替代流程 (Alternate Flow)** | [例如：當沒有任何未讀公告時，系統應優雅結束不回報錯誤] |
| **例外流程 (Exception Flow)** | [例如：當租戶 Context 遺失時，系統應拋出具體錯誤] |

**請填寫**：
```
Actor：__________
Precondition：__________
正常流程：
1. __________
2. __________
3. __________
替代流程：__________
例外流程：__________
```

---

## 3. 功能完成條件（測試合約 / Acceptance Criteria）
> **這裡的每一條都必須被轉換為一個或多個 `@Test` 方法來驗證，否則視為未完成。**

> **AC 編號格式**：`[FC編號]-AC[流水號]`，例如 `F1-AC1`、`F3-AC7`；流水號從 1 開始，每個 FC 獨立編號。

> **AC 撰寫指南**：
> - 每一條 AC 必須是**可驗證的具體情境**，避免模糊用語（如「系統要穩」）。
> - 建議採用 **GIVEN-WHEN-THEN** 結構撰寫：
>   - **GIVEN**：前置條件（例如「使用者有 3 筆未讀公告」）
>   - **WHEN**：執行動作（例如「呼叫 markAllAsRead」）
>   - **THEN**：預期結果（例如「成功寫入 3 筆閱讀記錄」）
>
> **範例**：
> - ❌ 模糊：`系統應能正確標記已讀。`
> - ✅ 具體：`GIVEN 使用者有 3 筆未讀公告，WHEN 呼叫 markAllAsRead，THEN 成功寫入 3 筆閱讀記錄，且不拋出例外。`

請依以下分類填寫，並在 AC 標題後以括號標示**測試層級歸屬**（預設為 Service 單元測試，可省略）：
- `（Service 單元測試）` — 預設，可省略
- `（Controller 整合測試）`
- `（Repository 整合測試）`
- `（Service 單元測試 + 整合測試）`

### 3.1 快樂路徑 (Happy Path) — 正常運作
- [ ] **F1-AC1**（Service 單元測試）：__________（例如：GIVEN 使用者有 3 則未讀公告，WHEN 呼叫 Service，THEN 應成功寫入 3 筆閱讀記錄）

### 3.2 商業規則 / 邊界值 (Business Rules / Boundaries)
- [ ] **F1-AC2**：__________（例如：GIVEN 使用者無未讀公告時，WHEN 呼叫標記全部已讀，THEN 系統不拋錯，且 Repository 未被呼叫）
- [ ] **F1-AC3**：__________（例如：GIVEN 部門 ID 為 null，WHEN 查詢未讀數量，THEN 系統應以 -1 作為預設值，不得拋出 NPE）

### 3.3 安全性與副作用 (Security / Side Effects)
- [ ] **F1-AC4**（Service 單元測試 + 整合測試）：租戶隔離 — 必須使用 `TenantContext.getCurrentTenantId()` 的值傳遞給 Repository，且單元測試須以 `ArgumentCaptor` 驗證此參數。
- [ ] **F1-AC5**（Controller 整合測試）：權限防護 — [若適用] 僅有具備 `ANNOUNCEMENT_MANAGE` 權限的使用者可執行此操作。
- [ ] **F1-AC6**：冪等性 — 重複呼叫（例如連點兩次）不得產生重複資料或錯誤。
- [ ] **F1-AC7**：[其他安全或副作用約束]

**請填寫**：
```
### 3.1 快樂路徑
- [ ] F1-AC1：__________

### 3.2 商業規則 / 邊界值
- [ ] F1-AC2：__________
- [ ] F1-AC3：__________

### 3.3 安全性與副作用
- [ ] F1-AC4：__________
- [ ] F1-AC5：__________
- [ ] F1-AC6：__________
```

---

## 4. 相依元件與對外介面 (Dependencies & Contracts)
> 列出此功能會使用到的外部元件（Repository、Service、Utility），及其預期的行為（可只列出關鍵方法）。

| 元件 | 方法 | 預期行為（正常） | 例外行為（Mock 拋出時） |
| :--- | :--- | :--- | :--- |
| `AnnouncementRepository` | `countUnread(userId, deptId, now)` | 回傳未讀公告總數（Long） | 若拋出 `DataAccessException`，Service 不攔截，由全局例外處理器處理 |
| `AnnouncementReadRepository` | `markAllAsRead(userId, tenantId, deptId)` | void（執行批次插入） | 若拋出 `DataAccessException`，Service 不攔截 |
| `TenantContext` | `getCurrentTenantId()` | 回傳當前租戶 ID（String） | （靜態方法，不回拋例外） |

**請填寫**：
```
| 元件 | 方法 | 預期行為（正常） | 例外行為（Mock 拋出時） |
| :--- | :--- | :--- | :--- |
| __________ | __________ | __________ | __________ |
```

---

## 5. 附加說明（Optional）
> 任何額外需要注意的技術限制、效能要求或非功能性需求。

- [ ] 效能要求：__________
- [ ] 資料庫限制：__________
- [ ] 其他：__________

---

**請填寫**：
```
- [ ] 效能要求：__________
- [ ] 資料庫限制：__________
- [ ] 其他：__________
```
## 6. 整合測試規劃（Integration Test Strategy）

> 涵蓋範圍：僅針對上述「必須由整合測試驗證的 AC」建立 @SpringBootTest 或 @DataJpaTest。

> **何時該用整合測試？**
> 當 AC 涉及以下任一情況時，**必須**使用整合測試（`@SpringBootTest` 或 `@DataJpaTest`），因為單元測試（Mockito）無法模擬：
> 1. **資料庫行為**：SQL 語法、Hibernate `@Filter`、`ON DELETE CASCADE`、`@Version` 樂觀鎖。
> 2. **Native SQL**：`ON CONFLICT`、CTE、窗口函數等 PostgreSQL 專屬語法。
> 3. **檔案 IO**：檔案儲存、刪除、讀取（需 `@TempDir`）。
> 4. **Transaction 行為**：`@Transactional` 滾回、Propagation 等。
> 5. **Spring Security 攔截**：未登入、權限不足等 Controller 層行爲。

 - 命名規範：測試類別名稱以 IT_ 開頭，格式為 IT_[FC編號]_[功能名稱]（例如 IT_F2_AnnouncementCrud）。

 - 內部結構：使用 @Nested 按 AC 分組，在 @DisplayName 中明確標示 AC 編號。

 - 資料庫策略：使用 @Transactional + @Rollback 確保測試資料不汙染實際資料庫；查詢統計類（F4）強烈建議使用 Testcontainers 啟動真實 PostgreSQL，避免 H2 對 Native SQL 語法不支援。

 - 檔案測試（F6）：使用 JUnit 的 @TempDir 注入臨時目錄，隔離檔案系統。

---

## 📌 使用建議（給 AI Agent）

1. **測試命名強制規範**：
   所有 `@Test` 方法名稱**必須**以對應的 AC 編號開頭，格式為：`[AC編號]_[方法名]_[情境]_[預期結果]`
   - ✅ 正確範例：`F3_AC7_markAsRead_crossTenant_throwsNotFound`
   - ✅ 正確範例：`F2_AC6_create_deptAdmin_forcesDeptscopeAndOwnDept`
   - ❌ 錯誤範例：`testMarkAsRead_throwsException`（缺少 AC ID）

2. **可追溯性檢查清單**（Code Review 時人工檢查）：
   - [ ] 每一條標記為 `[x]` 的 AC 在測試程式中是否都有對應的 `@Test` 方法（名稱含 AC 編號）？
   - [ ] 是否有任何 `@Test` 方法沒有對應到任何 AC？（若有，可能為冗餘測試，應刪除或補充對應 AC）