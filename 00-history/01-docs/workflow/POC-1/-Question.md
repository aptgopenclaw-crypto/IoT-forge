

以下是執行 POC 前需要確認的問題：

---

**1. 實作語言/位置**
設計文件用 TypeScript，但現有後端是 Java/Spring Boot。POC 要：
- 在現有 Spring Boot 專案內新增 `workflow` 模組？
- 還是另起一個獨立專案？

**2. 資料庫**
- 新建 `workflow_definitions`、`workflow_instances`、`workflow_step_logs` 三張表，直接建在現有 PostgreSQL？
- 還是 POC 階段用 H2 記憶體資料庫隔離？

**3. MockAssigneeResolver 的角色對應**
設計中有 `MANAGER`、`PROPERTY_MANAGER`、`DIRECTOR` 等 role_code，現有系統的 `roles` 表有這些角色嗎？還是 POC 階段用完全獨立的假資料？

**4. 第一個接入的業務類型**
POC 要跑通哪個完整案例？（例如：資產轉移 / 路燈報修 / 其他）這決定流程定義的步驟數與角色。

**5. 整合範圍**
POC 的驗收標準是：
- A：只要單元測試 + 整合測試跑通（不接前端）
- B：有一支 REST API 可以手動呼叫（Postman/curl）
- C：要接現有前端操作

**6. 代理人 `delegate_settings` 表**
現有系統有這張表嗎？還是 POC 要一起建？