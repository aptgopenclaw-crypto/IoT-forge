# Audit 功能模組「易擴展」設計指南

> 對象：`com.taipei.iot.audit` 後端模組
> 目的：說明如何設計、以及有哪些具體作法，讓稽核（Audit）模組能「易擴展」——在最少修改、不破壞既有行為的前提下，加入新的稽核事件來源、新的輸出去向（Sink：DB／SIEM／Kafka／Syslog）、新的敏感欄位遮罩規則、新的保存／封存／完整性策略，以及新的查詢／匯出格式。

---

## 1. 什麼叫「易擴展」？（先定義目標）

對 audit 模組而言，「易擴展」要滿足以下可衡量目標：

| 目標 | 白話描述 | 衡量方式 |
|------|----------|----------|
| **開放封閉（OCP）** | 新增事件／輸出去向／遮罩規則時「只新增類別或設定」，不改既有核心 | 加一個 Sink 所需修改的既有檔案數趨近 0（現況需改 `AuditAsyncWriter`） |
| **單一職責（SRP）** | 事件擷取、寫入、輸出、保存策略各自獨立 | 改保存策略不波及寫入路徑 |
| **可設定（Configurable）** | 敏感欄位、保存天數、啟用的 Sink 由設定驅動 | 改設定不需重編譯（現況遮罩規則寫死） |
| **可組合（Composable）** | 一筆稽核事件可同時送多個去向、可掛多個後處理 | 加一個 Sink/後處理不改主流程 |
| **可測試（Testable）** | 每個擴展點可獨立 mock 測試 | Sink、保存策略、遮罩可單測 |
| **多執行緒/多來源安全** | 非請求執行緒（MQ/排程）也能正確稽核 | 不假設 ThreadLocal 一定有值 |
| **不可竄改（Integrity）** | 稽核紀錄可驗證未被篡改 | 具備雜湊鏈/簽章（現況無） |

---

## 2. 現況盤點：已經做對的事

audit 模組在「事件擷取」與「非同步寫入」上已有良好骨架，這些是擴展的基礎：

### 2.1 模組結構

```
audit/
├── annotation/  @AuditEvent（單一屬性 value() = AuditEventType，編譯期型別安全）
├── aspect/      BaseLoggerAspect（@Around 攔截 @AuditEvent，主執行緒擷取 context）
├── async/       AuditAsyncWriter（@Async("auditExecutor")，best-effort 寫入）
├── config/      AuditAsyncConfig（ThreadPool core=2/max=8/queue=500/CallerRunsPolicy）
├── controller/  AuditController（查詢/匯出 /v1/auth/audit/*）
├── entity/      UserEventLogEntity（主稽核表，18 欄、租戶過濾）
│                AuditRevisionEntity + AuditRevisionListener（Envers，已配置但未啟用）
├── enums/       AuditEventType（60+ 事件）、AuditCategory（13 分類）
├── job/         AuditPurgeJob（每日 02:00 依保存天數刪除）
├── listener/    VirusScanAuditListener（@EventListener 解耦範例）
├── repository/  UserEventLogRepository
├── service/     AuditService（Specification 查詢 + DataScope + CSV/XLSX 匯出）
└── util/        PayloadSanitizer（遮罩 password/secret/token，截斷 2000 字）
```

### 2.2 已具備的良好擴展性

| 設計 | 說明 | 對擴展的意義 |
|------|------|-------------|
| **列舉驅動事件** | `AuditEventType`：`value()` + `category()` + `success()`（驅動 errorCode） | 加事件只需加列舉值 + 在方法掛 `@AuditEvent`，其餘零修改 |
| **宣告式 AOP 擷取** | `@AuditEvent` + `BaseLoggerAspect` 自動擷取 actor/tenant/IP/UA/耗時/payload | 新端點只要加註解即被稽核 |
| **事件解耦範例** | `VirusScanAuditEvent`（common 發佈）→ `VirusScanAuditListener`（audit 訂閱） | common 不依賴 audit；跨模組稽核走事件 |
| **非同步寫入** | `AuditAsyncWriter` + 專屬執行緒池，best-effort 不影響業務 | 稽核失敗不拖垮業務交易 |
| **可組合查詢** | `AuditService` 用 JPA `Specification` + DataScope 過濾 | 查詢條件可組合 |
| **匯出安全** | CSV 防注入（`=+-@` 前綴加引號）、XLSX 串流（SXSSF） | 匯出已具防護 |
| **遮罩 + 截斷** | `PayloadSanitizer` 遮蔽敏感欄位、截斷長度 | 基本資安到位 |
| **保存清理** | `AuditPurgeJob` 依租戶 `AUDIT_RETENTION_DAYS` 清理 | 已有保存機制 |

> **結論：事件擷取（in）已相當成熟，但「輸出（out）、保存策略、遮罩規則、來源抽象」偏硬編碼、單一化。本文件聚焦補上這些擴展點。**

---

## 3. 擴展維度：會被擴展的是哪些東西？

| 維度 | 範例需求 | 現況痛點 |
|------|----------|----------|
| **A. 輸出去向（Sink）** | 除 DB 外，再送 SIEM／Kafka／Syslog／檔案／S3 | 全部直寫 `user_event_log`，無 Sink 抽象 → 要改 `AuditAsyncWriter` |
| **B. 事件來源（Source）** | 排程任務、MQ 監聽、Webhook、登入流程 | 來源散落（AOP 註解 + `AuthServiceImpl` 直接呼叫 + listener），無統一入口 |
| **C. 敏感欄位遮罩** | 新增 apiKey／credential／idNumber 等需遮罩欄位 | `PayloadSanitizer` 規則寫死在程式 |
| **D. 保存／封存策略** | 「違規事件永久保存」「一年後封存 S3」「半年後加密」 | `AuditPurgeJob` 一律 `createTime < cutoff` 刪除，無差異化 |
| **E. 完整性／防竄改** | 雜湊鏈、簽章、WORM、外部不可變儲存 | 完全沒有；紀錄可被直接改 DB |
| **F. 查詢／匯出格式** | JSON、PDF、串接外部報表 | 匯出格式寫死 CSV/XLSX |
| **G. Envers 版本稽核** | 「某欄位被誰改成什麼」的欄位級歷史 | rev_info 表已建但無 `@Audited`，閒置 |

---

## 4. 設計手法（Patterns & Practices）

### 手法 1：輸出去向以「Sink 策略集合」抽象 — 維度 A（**最高優先**）

**問題**：所有稽核都直寫 DB（`AuditAsyncWriter` → `UserEventLogRepository`）。要再送 SIEM/Kafka，得改寫 writer，違反 OCP。

**做法**：抽 `AuditLogSink` 介面，寫入時廣播給所有啟用的 Sink。

```java
public interface AuditLogSink {
    String name();
    boolean enabled(String tenantId);          // 逐租戶/逐環境開關
    void write(AuditRecord record);             // 失敗各自處理，不影響其他 Sink
}

@Component class DatabaseAuditLogSink implements AuditLogSink { /* 包現有 repository 寫入 */ }

// 未來新增——不動既有檔：
@Component @ConditionalOnProperty("audit.sink.kafka.enabled")
class KafkaAuditLogSink implements AuditLogSink { ... }
@Component @ConditionalOnProperty("audit.sink.siem.enabled")
class SyslogAuditLogSink implements AuditLogSink { ... }
```

`AuditAsyncWriter` 改成注入 `List<AuditLogSink>`，在 async 執行緒內逐一寫入（單一 Sink 失敗不影響其他）：

```java
sinks.stream().filter(s -> s.enabled(record.tenantId()))
     .forEach(s -> safely(() -> s.write(record)));  // best-effort per sink
```

> **效益**：新增輸出去向 = 新增一個 `AuditLogSink` 類別 + 設定開關，寫入主流程零修改。DB 不再是唯一去向。

---

### 手法 2：統一的事件來源入口（Source 抽象）— 維度 B

**問題**：稽核來源有三種寫法——`@AuditEvent` AOP、`AuthServiceImpl` 直接呼叫 `logLoginEvent()`、listener 轉譯外部事件。新增來源（MQ/排程）時無一致入口，容易各寫各的、欄位不齊。

**做法**：所有來源都收斂到單一「攝取入口」`AuditIngestor`，並用統一的 `AuditRecord` 信封（envelope）。

```java
public record AuditRecord(            // 統一信封，解耦 schema 變更
    String schemaVersion,             // 信封版本，未來加欄位向後相容
    String tenantId, String userId, String username, String userLabel,
    AuditEventType eventType, String apiEndpoint, String payload,
    String errorCode, String message, String ipAddress, String userAgent,
    Long executionTime, Long deptId, String impersonatedBy, Instant occurredAt) {}

public interface AuditIngestor {
    void ingest(AuditRecord record);  // 唯一入口 → 套遮罩 → 廣播 Sink
}
```

- `BaseLoggerAspect`、登入流程、`VirusScanAuditListener`、未來的 MQ/排程來源，**全部呼叫 `AuditIngestor.ingest()`**。
- **關鍵：context 解析不再硬綁 ThreadLocal**。`AuditRecord` 自帶 tenant/actor，呼叫端（在主執行緒/發佈端）負責填好；非請求執行緒（MQ）也能正確稽核，解決現況「ThreadLocal 為 null」的耦合。

> **效益**：來源與寫入解耦；信封版本化讓未來加欄位不破壞既有 Sink；新增來源只需「組好 `AuditRecord` → 呼叫 ingest」。

---

### 手法 3：遮罩規則設定化 — 維度 C

**問題**：`PayloadSanitizer.isSensitive()` 把 `password/secret/token` 寫死在程式，新增 `apiKey/credential/idNumber` 要改碼。

**做法**：規則改由設定驅動，並抽成可擴展的 `FieldMasker` 集合。

```yaml
audit:
  sanitize:
    patterns: [password, secret, token, apiKey, credential]
    max-length: 2000
```

```java
public interface FieldMasker {                 // 進階：不同型別不同遮罩
    boolean supports(String fieldName);
    String mask(String value);                 // 全遮 / 部分遮（保留末4碼）
}
@Component class DefaultPatternMasker implements FieldMasker { /* 讀設定 patterns */ }
@Component class PartialIdMasker implements FieldMasker { /* 身分證遮中間 */ }
```

> **效益**：新增敏感欄位改設定即可；需要「部分遮罩」時加一個 `FieldMasker`，不動主流程。

---

### 手法 4：保存／封存以「策略提供者」抽象 — 維度 D（**建議新增**）

**問題**：`AuditPurgeJob` 一律 `createTime < cutoff` 刪除，無法表達「違規事件永久保存」「一年後封存到 S3」「半年後加密」等差異化需求。

**做法**：抽 `AuditRetentionPolicy`，依事件類別/類型決定處置動作。

```java
public enum RetentionAction { DELETE, ARCHIVE, ANONYMIZE, KEEP_FOREVER }

public interface AuditRetentionPolicy {
    boolean applies(UserEventLogEntity record, String tenantId);
    RetentionAction decide(UserEventLogEntity record);
}
@Component class DefaultRetentionPolicy implements AuditRetentionPolicy { /* 依天數 DELETE */ }
@Component class SecurityEventRetentionPolicy implements AuditRetentionPolicy {
    // 違規/安全事件 → KEEP_FOREVER 或 ARCHIVE
}
```

`AuditPurgeJob` 改成：對到期紀錄詢問策略鏈 → 依 `RetentionAction` 分流（刪除／封存到 Sink／匿名化）。封存可復用「手法 1」的 Sink（如 `S3ArchiveSink`）。

> **效益**：新增保存規則 = 新增一個 policy 類別；封存/匿名化與刪除統一在策略下表達。

---

### 手法 5：完整性／防竄改 — 維度 E（**安全建議**）

**問題**：稽核紀錄可直接改 DB，無任何防竄改機制（雜湊/簽章皆無）。

**做法**（漸進式）：
1. **雜湊鏈（Hash chaining）**：寫入時計算 `hash = H(prev_hash + record_payload)`，存入新欄位 `record_hash / prev_hash`，可離線驗證鏈未被斷裂或竄改。
2. **唯讀/外部 Sink**：透過「手法 1」把稽核同步寫到 append-only 的外部系統（SIEM/WORM 儲存），即使 DB 被改也有對照。
3. **欄位簽章（選配）**：高敏感事件以服務私鑰簽章。

> 由 `AuditIngestor`（手法 2）統一計算雜湊，集中一處，不散落各來源。

---

### 手法 6：查詢/匯出格式以「Exporter 策略」抽象 — 維度 F

**問題**：匯出寫死 CSV/XLSX。加 JSON/PDF 需改 `AuditService`。

**做法**：抽 `AuditExporter`，依 `format` 參數選用。

```java
public interface AuditExporter {
    String format();                                   // csv, xlsx, json, pdf
    void export(List<UserEventLogEntity> rows, OutputStream out);
}
@Component class CsvAuditExporter ... @Component class XlsxAuditExporter ...
// 未來：JsonAuditExporter / PdfAuditExporter
```

> Controller 依 `format` 從集合取對應 exporter，新增格式不改既有。

---

### 手法 7：Envers 決策（維度 G，**澄清/收斂**）

rev_info 表與 `AuditRevisionEntity/Listener` 已配置，但**無任何 `@Audited` 實體**，形同閒置。建議二擇一並記錄決策：
- **啟用**：在關鍵實體（角色、權限、設定、使用者）加 `@Audited`，提供「欄位級變更歷史」，與 `UserEventLogEntity`（操作事件）互補。
- **下架**：若短期不需欄位級歷史，移除 Envers 依賴與空表，降低維運誤解。

> 兩種稽核定位不同：Envers＝「資料變成什麼」；`UserEventLogEntity`＝「誰做了什麼操作」。文件中應明確區分，避免擴展時混淆。

---

## 5. 共通的工程實踐（讓擴展不出錯）

| 實踐 | 作法 | 為何重要 |
|------|------|----------|
| **設定外部化** | 遮罩 patterns、保存天數、啟用的 Sink/Exporter 走設定 | 不改碼即可調整 |
| **特性開關** | Sink/Source/Policy 加 `@ConditionalOnProperty` | 逐租戶/逐環境灰度 |
| **統一信封 + 版本** | `AuditRecord(schemaVersion, ...)` | 加欄位向後相容，不破壞既有 Sink |
| **best-effort 隔離** | 每個 Sink 寫入獨立 try/catch，失敗記 log 不互相影響 | 稽核不拖垮業務、單一去向故障不影響其他 |
| **不假設 ThreadLocal** | context 在主執行緒/發佈端擷取並放進 `AuditRecord` | 支援 MQ/排程等非請求來源 |
| **稽核「稽核行為」** | 查詢/匯出本身掛 `@AuditEvent(EXPORT_AUDIT)`（現已如此） | 防內部濫用 |
| **匯出防注入** | 任何新 Exporter 沿用 CSV 注入防護與串流寫法 | 防公式注入/OOM |
| **完整性集中計算** | 雜湊/簽章在 `AuditIngestor` 一處 | 不散落、可驗證 |
| **可測試** | 每個 Sink/Policy/Masker/Exporter 單測 | 擴展有回歸保護 |

---

## 6. 目前缺口與優先順序

| 缺口 | 現況 | 風險 | 建議優先級 |
|------|------|------|-----------|
| 單一輸出去向（只有 DB） | 直寫 `user_event_log` | 加 SIEM/Kafka 要改 writer | ★★★（先做） |
| 來源分散、ThreadLocal 硬依賴 | AOP/直呼/listener 三套寫法 | MQ/排程來源 ThreadLocal 為 null | ★★★ |
| 遮罩規則寫死 | `password/secret/token` 寫在程式 | 新敏感欄位需改碼、漏遮風險 | ★★☆ |
| 保存策略一刀切 | 一律到期刪除 | 無法永久保存/封存違規事件 | ★★☆ |
| 無防竄改機制 | 紀錄可改 DB | 稽核可信度不足、不符合規 | ★★☆ |
| 匯出格式寫死 | 僅 CSV/XLSX | 加 JSON/PDF 改 service | ★☆☆ |
| Envers 閒置 | rev_info 空表 | 維運誤解、未提供欄位級歷史 | ★☆☆ |

---

## 7. 落地檢查清單

### 7.1 新增一種「輸出去向（Sink）」（例如 Kafka）
- [ ] 新增 `KafkaAuditLogSink implements AuditLogSink`（`@ConditionalOnProperty`）
- [ ] 設定開關 + 連線參數（secret 加密儲存）
- [ ] `enabled(tenantId)` 逐租戶判斷
- [ ] 單測：寫入成功、失敗不影響其他 Sink、停用時不寫
- [ ] 確認走 best-effort（不拋出影響業務）

### 7.2 新增一種「事件來源」（例如 MQ 監聽）
- [ ] 在來源處組好 `AuditRecord`（自帶 tenant/actor，不依賴 ThreadLocal）
- [ ] 呼叫 `AuditIngestor.ingest(record)`
- [ ] 若為新事件，於 `AuditEventType` 加列舉值（含 category/success）
- [ ] 單測：欄位齊全、非請求執行緒可正確稽核

### 7.3 新增「敏感欄位遮罩」
- [ ] 在 `audit.sanitize.patterns` 加欄位名（純設定）
- [ ] 若需部分遮罩，新增 `FieldMasker` 實作
- [ ] 單測：該欄位於 payload 被遮蔽

### 7.4 新增「保存/封存策略」
- [ ] 新增 `AuditRetentionPolicy` 實作（如違規事件 KEEP_FOREVER）
- [ ] 若封存，復用 Sink（如 `S3ArchiveSink`）
- [ ] 單測：到期紀錄被正確分流（刪/封存/匿名）

> 若上述任一新增**被迫修改 `AuditAsyncWriter`/`AuditPurgeJob`/`PayloadSanitizer` 的核心分支**，代表該擴展點尚未抽出，應回頭補上手法 1~4 的抽象。

---

## 8. 一句話總結

> audit 模組的**事件擷取（in）已成熟**——列舉驅動事件、`@AuditEvent` 宣告式 AOP、事件解耦範例、非同步 best-effort 寫入、Specification 查詢與安全匯出。
> 但**輸出與治理（out/governance）偏單一、硬編碼**：只有 DB 單一去向、來源分散且硬綁 ThreadLocal、遮罩規則寫死、保存策略一刀切、無防竄改。
> 建議優先補上 **Sink 策略集合（AuditLogSink）**、**統一攝取入口 + 信封版本（AuditIngestor / AuditRecord，解除 ThreadLocal 耦合）**、**設定化遮罩**、**保存策略提供者（AuditRetentionPolicy）**，並評估**雜湊鏈防竄改**與 **Envers 去留**，
> 全程貫徹「設定外部化、特性開關、信封版本化、best-effort 隔離、不假設 ThreadLocal」，即可在不動核心寫入流程的前提下持續演進。
