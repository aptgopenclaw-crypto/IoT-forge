

---

# 📑 Audit 模組技術設計文件 (Audit Module Technical Specification)

## 一、 模組概述 (Overview)
`audit` 模組是系統架構中的 **L1 平台核心層** 元件。其核心職責是提供**全方位、不可竄改、高可用**的操作與資料變更追蹤機制。
本模組不僅記錄「使用者調用了哪些 API」，更透過 Hibernate Envers 追蹤「使用者修改了哪些資料庫欄位」，並提供多租戶環境下的安全查詢與匯出功能，以滿足企業級的安全合規（Compliance）、問題排查（Troubleshooting）與 SIEM 系統整合需求。

## 二、 系統架構與依賴設計 (Architecture & Dependencies)

### 2.1 分層與依賴反轉 (DIP)
為了避免 L1 (`audit`) 與 L2 (`auth`/`user`) 產生循環依賴，本模組嚴格遵循**依賴反轉原則 (DIP)**：
*   **Port (介面)**：定義於 L0 (`common`) 的 `UserDisplayInfoProvider`。
*   **Adapter (實作)**：由 L2 (`auth`) 實作並注入 Spring 容器。
*   **結果**：`audit` 模組在編譯期完全不依賴 `auth` 模組，僅在執行期透過 Spring IoC 獲取使用者顯示資訊（displayName, email）。

### 2.2 審計視角的雙軌設計
本模組提供兩種互補的審計視角：
1.  **事件/操作級別審計 (Event-Level)**：記錄 API 呼叫、登入、掃毒結果（寫入 `user_event_log`）。
2.  **資料/實體級別審計 (Data-Level)**：記錄 JPA Entity 的 CRUD 變更歷史（透過 Hibernate Envers 寫入 `rev_info` 與對應的 `_AUD` 歷史表）。

---

## 三、 核心功能與資料流 (Core Features & Data Flow)

### 3.1 API 操作審計 (AOP 自動攔截)
*   **觸發機制**：透過 `BaseLoggerAspect` 攔截所有標註 `@AuditEvent` 的 Controller 方法。
*   **上下文採集**：在主執行緒（Request Scope）提前抓取 `TenantContext` 與 `SecurityContext`，解決 `@Async` 切換執行緒後 ThreadLocal 丟失的問題。
*   **安全防護**：
    *   **IP 防偽造**：直接讀取 TCP 連線的 `RemoteAddr`，不信任 `X-Forwarded-For`。
    *   **Payload 脫敏**：透過 `PayloadSanitizer` 自動將 JSON 中的 `password`, `token`, `secret` 替換為 `***`，並限制最大長度為 2000 字元，防止 DB 撐爆。
*   **非同步寫入**：交由 `AuditAsyncWriter` 透過專屬執行緒池 `auditExecutor` 非同步落庫。

### 3.2 系統事件審計 (Event-Driven 解耦)
針對非標準 API 呼叫的系統級事件，採用 Spring Event 機制解耦：
*   **登入/登出 (`LoginAuditListener`)**：監聽 `LoginAuditEvent`。因登入時可能尚無完整的 Tenant Context，直接使用 `TenantContext.runInSystemContext()` 繞過 Hibernate Filter 寫入。
*   **檔案掃毒 (`VirusScanAuditListener`)**：監聽 `VirusScanAuditEvent`。將掃毒結果（INFECTED/ERROR）轉譯為審計事件，委託 `AuditAsyncWriter` 寫入。

### 3.3 資料變更審計 (Hibernate Envers)
*   **機制**：透過 `AuditRevisionEntity` 與 `AuditRevisionListener` 實作。
*   **功能**：每當受 Envers 監管的 Entity 發生變更時，自動記錄**版本號 (Revision)** 與**操作者 ID (`actionUserId`)**，實現資料列級別的歷史回溯（Who changed What and When）。

### 3.4 日誌查詢、權限控制與匯出
由 `AuditService` 與 `AuditController` 提供：
*   **多維度權限過濾 (DataScope)**：
    *   **一般使用者**：僅能查詢自己的日誌。
    *   **部門管理員**：依據 `VisibleDeptScopeProvider` 查詢所轄部門的日誌。
    *   **超級管理員**：可查詢全租戶日誌，但**系統底層會自動排除 `SUPER_ADMIN` 的操作紀錄**，避免高權限帳號的敏感操作污染一般場域的稽核畫面。
*   **高效能匯出**：
    *   支援 CSV 與 XLSX 格式。
    *   **XLSX 串流寫入**：使用 `SXSSFWorkbook` (保留 100 行在記憶體)，防止大數據量匯出導致 OOM。
    *   **CSV 注入防護**：`csvEscape` 方法會自動在開頭為 `=, +, -, @` 等危險字元前加上單引號，防止 Excel 公式注入攻擊。
    *   **限制上限**：單次匯出上限 5000 筆，並搭配 `@RateLimit` 防止惡意大量下載。

### 3.5 生命週期管理 (Purge Job)
*   **機制**：`AuditPurgeJob` 每日凌晨 2 點執行。
*   **多租戶個性化保留**：讀取各租戶在 `SystemSetting` 中設定的 `AUDIT_RETENTION_DAYS`，精準刪除過期資料。
*   **髒資料清理**：自動清理 `tenantId` 為 null 的孤立紀錄。

---

## 四、 關鍵非同步與穩定性設計 (Async & Stability)

為了確保審計日誌寫入**絕對不影響主業務 API 的響應時間**，系統設計了嚴密的非同步保護網：

| 設計機制 | 實作細節 | 目的 |
| :--- | :--- | :--- |
| **專屬執行緒池** | `AuditAsyncConfig` 配置 `auditExecutor` (Core:2, Max:8, Queue:500) | 資源隔離，防止日誌寫入耗盡 Tomcat 執行緒。 |
| **防丟失拒絕策略** | `CallerRunsPolicy` | 當佇列滿載時，由**主業務執行緒**親自執行寫入。犧牲該次 API 效能，確保日誌**零丟失**。 |
| **Best-Effort 容錯** | `AuditAsyncWriter` 內部的 `try-catch` 吞掉例外並記錄 Error Log | 確保即使資料庫暫時鎖死或離線，也不會導致主業務 API 拋出 500 錯誤。 |
| **上下文顯式傳遞** | AOP/Listener 在主執行緒抓取 ThreadLocal 後，透過**方法參數**傳遞給 Async 方法 | 解決 Spring `@Async` 新執行緒無法繼承 ThreadLocal 的經典痛點。 |

---

## 五、 資料模型 (Data Model)

### 5.1 `user_event_log` (API 與事件操作日誌)
記錄系統級別的互動行為。
*   **核心欄位**：`tenant_id` (租戶), `user_id`, `event_type` (事件類型), `api_endpoint`, `payload` (脫敏後的入參), `ip_address`, `execution_time` (API 耗時)。
*   **特色欄位**：`impersonated_by` (記錄 SUPER_ADMIN 代操場景下的真實操作者)。
*   **多租戶隔離**：透過 Hibernate `@Filter(name = "tenantFilter")` 實現底層資料隔離。

### 5.2 `rev_info` (Envers 資料變更版本資訊)
配合 Hibernate Envers 使用，記錄資料列變更的元數據。
*   **核心欄位**：`id` (版本號), `timestamp` (變更時間), `action_user_id` (由 `AuditRevisionListener` 自動從 SecurityContext 抓取的變更者 ID)。

---

## 六、 API 介面清單 (API Endpoints)

所有介面皆位於 `/v1/auth/audit` 路徑下，需具備 `AUDIT_LIST` 權限（查詢我的登入紀錄除外）。

| Method | Endpoint | 功能描述 | 備註 |
| :--- | :--- | :--- | :--- |
| `GET` | `/categories` | 取得稽核分類列表 | 回傳 `AuditCategory` 枚舉值 |
| `GET` | `/user/usage/history` | 分頁查詢使用者操作歷程 | 支援多條件過濾、DataScope 權限控制 |
| `GET` | `/user/usage/history/export` | 匯出操作歷程 (CSV/XLSX) | 限流 5次/60秒，上限 5000 筆 |
| `GET` | `/user/login/my` | 查詢「我的」登入紀錄 | 一般使用者可查看自己的登入/登出歷史 |

---

## 七、 總結 (Summary)
`audit` 模組展現了高度成熟的企業級架構設計。它透過 **AOP + Event-Driven** 實現了無侵入的日誌採集；透過 **DIP (依賴反轉)** 保持了模組邊界的純潔；透過 **專屬 Async Pool + CallerRunsPolicy** 兼顧了效能與資料完整性；更在細節處（如 IP 防偽造、CSV 注入防護、Payload 脫敏）築起了堅實的資安防線。是一個兼具**可觀測性、合規性與高可用性**的基礎設施模組。