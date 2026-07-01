這兩個類別都是 **Spring 事件監聽器（Event Listener）**，負責將特定業務事件轉換為審計日誌並非同步寫入資料庫。它們的核心目的相同，但在**觸發場景、實作策略**上有顯著差異。

---

## 一、`LoginAuditListener` — 登入/登出審計監聽器

### 功能
- 訂閱 `LoginAuditEvent`（登入成功、登入失敗、登出等事件）。
- 將事件資料直接映射為 `UserEventLogEntity`，透過 `UserEventLogRepository` 寫入資料庫。

### 設計亮點

| 設計決策 | 原因 |
|---|---|
| **直接操作 Repository**（不經過 `AuditAsyncWriter`） | 登入時使用者尚未完全認證，`UserDisplayInfoProvider` 可能查不到完整的 displayName/email，但事件本身已攜帶這些資訊，因此直接組裝 Entity 更合理。 |
| `@Async("auditExecutor")` + `@EventListener` | 登入響應不应被日誌 I/O 阻塞。使用專屬執行緒池隔離資源。 |
| `TenantContext.runInSystemContext(...)` | 登入時可能**尚未建立 Tenant Context**（例如登入失敗時），直接使用 SYSTEM context 繞過 Hibernate 的租戶過濾器（`@Filter`），確保寫入不會因缺少 tenantId 而被攔截。 |
| 使用 `@EventListener` 而非 `@TransactionalEventListener` | 註解明確說明：「登入流程本身不一定在 transaction 中執行」，直接監聽可確保無論有無外層交易都能寫入。 |

---

## 二、`VirusScanAuditListener` — 檔案掃毒審計監聽器

### 功能
- 訂閱 `VirusScanAuditEvent`（檔案上傳後的病毒掃描結果事件）。
- 將掃描結果（`INFECTED`、`ERROR`）翻譯為對應的 `AuditEventType`，再委託 `AuditAsyncWriter` 非同步寫入。

### 設計亮點

| 設計決策 | 原因 |
|---|---|
| **委託 `AuditAsyncWriter`** 寫入 | 掃毒發生在使用者已認證的請求中，`AuditAsyncWriter` 能自動補充 displayName/email，並統一處理異常與租戶上下文。 |
| **在 Listener 內手動抓取 ThreadLocal** | 註解明確說明：「在事件發布的呼叫端執行緒（仍在 request scope 內）擷取 ThreadLocal 值」。因為 `AuditAsyncWriter.saveAsync()` 本身是 `@Async`，會切換到新執行緒，如果等到 `saveAsync` 內部才讀取 `TenantContext`，會讀到 `null`。所以在 Listener（主執行緒）先抓出來，再當作參數傳入。 |
| **不使用 `@Async` 在 Listener 上** | 因為最終寫入已由 `AuditAsyncWriter.saveAsync()` 的 `@Async` 處理，Listener 本身只需同步抓取上下文後立即委派即可，避免雙重非同步的複雜性。 |
| **跨模組解耦（Event-Driven）** | 註解說明：「common 模組以 `ApplicationEventPublisher` 發送事件，由本 listener 在 audit 模組內訂閱」。這意味著 **common 模組不需要依賴 audit 模組**，就能實現掃毒結果的可觀測性，完美符合您之前討論的分層架構（L0 不依賴 L1）。 |

---

## 三、兩者對比總結

```
┌──────────────────────────┬───────────────────────────┬───────────────────────────────┐
│          維度             │    LoginAuditListener     │    VirusScanAuditListener     │
├──────────────────────────┼───────────────────────────┼───────────────────────────────┤
│ 訂閱事件                  │ LoginAuditEvent           │ VirusScanAuditEvent           │
│ 觸發時機                  │ 登入/登出流程              │ 檔案上傳後掃毒完成             │
│ 寫入方式                  │ 直接 Repository.save()    │ 委託 AuditAsyncWriter         │
│ 非同步機制                │ 自身 @Async               │ AuditAsyncWriter 的 @Async    │
│ ThreadLocal 抓取位置      │ 事件本身已攜帶，不需抓取   │ Listener 內手動抓取後傳參      │
│ 租戶上下文處理            │ runInSystemContext()      │ 由 AuditAsyncWriter 的註解處理  │
│ 設計動機                  │ 登入時 Context 不完整      │ 跨模組解耦 (common → audit)   │
└──────────────────────────┴───────────────────────────┴───────────────────────────────┘
```

### 核心設計哲學

這兩個 Listener 共同展現了一個重要的架構原則：**「審計日誌的收集不應侵入業務程式碼」**。

- 登入邏輯不需要知道審計系統的存在，只需發布 `LoginAuditEvent`。
- 掃毒邏輯不需要知道審計系統的存在，只需發布 `VirusScanAuditEvent`。
- **Listener 作為橋樑**，將業務事件翻譯為審計紀錄，實現了業務模組與審計模組的**完全解耦**。