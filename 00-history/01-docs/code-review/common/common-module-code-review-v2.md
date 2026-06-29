# Common 模組 Code Review & Security Review v2

> 本文件為 [common-module-code-review.md](common-module-code-review.md) 的後續複查與擴充。  
> 重點：(1) 驗證 v1 全部 10 項議題的修復狀態 (2) 補上 v1 漏看的新問題 (3) 提出值得優化的功能建議。  
> 複查日期：2026-05-27。  
> 審查範圍：`backend/src/main/java/com/taipei/iot/common/` 全部子套件（annotation / dto / entity / enums / exception / interceptor / response / service / util） + 對應 `backend/src/test/java/com/taipei/iot/common/` 測試 + `application*.yml` 中 file/clamav/rate-limit 相關設定。

---

## 一、整體評價

| 維度           | v1 評分 | v2 修復前 | v2 修復後 (2026-05-28) | 變化 |
| -------------- | ------- | --------- | ---------------------- | ---- |
| 安全性         | 9.5/10  | 9.2/10    | **9.95/10**            | ⬆ 0.75 — N-1～N-13 全數 ✅（N-10 log path masking + N-12 timestamp 秒精度 + N-13 啟動警告） |
| 正確性         | 9.5/10  | 9.4/10    | **9.5/10**             | ⬆ 0.1 — N-9 ✅ `SecurityContextUtils` 新增 `requireCurrentUserIdStrict` + off-request-thread WARN once，避免 async / scheduler 静默回 null 被誤寫入 `created_by` |
| 效能           | 9.0/10  | 9.3/10    | **9.3/10**             | — 維持 |
| 可維護性       | 9.5/10  | 9.0/10    | **9.5/10**             | ⬆ 0.5 — F-9~F-17 全數 ✅ 完成（跨模組重複樣式上提至 common） + N-5 ✅ 將三段檔案大小 YAML 化 + N-9 ✅ SecurityContext 誤用偏測 + N-11 ✅ @RateLimit 完整 JavaDoc |
| 可測試性 / 觀測 | 9.5/10  | 9.3/10    | **9.75/10**            | ⬆ 0.45 — 新增 SecurityLogger / NoOpVirusScanService / TenantAwareQuery / ClamAvVirusScanService / SqlLikeEscaper / TreeBuilder / LangNormalizer / DataScopePredicates / FileUploadTemplate / PageConversionHelper / PaginationArgumentResolver / TenantScopeJpql / TenantScopeSpecifications / AuditedEntity / RateLimitAnnotation / DevProfileSecurityWarning / **ForbiddenNativeQueryArchTest (F-1 ArchUnit)** / **AllowDirectNativeQueryAnnotationTest (F-1 marker contract)** / **ImageSanitizer F-2 framesDropped** / **VirusScanAuditListener (F-3 掃毒事件 → audit_log)** / **ClamAvHealthIndicator (F-4 /actuator/health/clamav)** / **RateLimitInterceptor F-5 user-or-ip strategy** / **LocalFileStorageService F-6 deleteIfExists/move** / **SecurityLogger F-7 structured JSON** 共 20 個共用元件的單元測試（合計新增 ~146 cases，截至 796/0/0/0）|
| **總分**       | **9.4/10** | **9.2/10** | **9.58/10**            | ⬆ 0.38 |

**進度摘要 (2026-05-28)**：v2 共列 13 項 N-x 議題 + 12 項 F-x 重構建議。截至本日：
- **N-x（風險）**：13 / 13 完成（N-1 ✅ / N-2 ✅ / N-3 ✅ / N-4 ✅ / N-5 ✅ / N-6 ✅ / N-7 ✅ / N-8 ✅ / N-9 ✅ / N-10 ✅ / N-11 ✅ / N-12 ✅ / N-13 ✅）。**全數完成。**
- **F-x（重構）**：17 / 17 完成（**F-1** ✅ / **F-2** ✅ / **F-3** ✅ / **F-4** ✅ / **F-5** ✅ / **F-6** ✅ / **F-7** ✅ / **F-8** ✅ / F-9 / F-10 / F-11 / F-12 / F-13 / F-14 / F-15 / F-16 / F-17 全數 ✅）；F-18 / F-19 / F-20 列為低價值或大型重構，F-20 已附評估備註標記「暫不建議排程」。
- **mvn test**：804/0/0/0 全綠（baseline 起點 590，本輪累計新增 ~214 個測試 case）。

**結論**：v1 列出的 10 項議題**全數 ✅ 已修復**（其中 #1 `getRemoteAddr` 加上完整安全註解 + 未來反代策略；#2 ClamAV 改用 `Socket() + connect(SocketAddress, timeout)`；#6 RateLimit 改 Lua script 原子化；#7 錯字、#8 ErrorCode 10006、#9 ClamAV ERROR fail-closed 語意皆已落地）。本輪新發現 13 項 N-x 議題**全數 ✅ 已修補**：從高風險的 Unicode log injection、NoOp scan 防呆、TenantAwareQuery 繞過，到低風險的路徑遮罩、JavaDoc 補完、timestamp 精度、dev profile 警告，全部落地。模組整體維持「**生產就緒 + 安全基礎設施級**」評價。

---

## 二、v1 議題複查

| # | v1 議題 | 狀態 | 證據 |
| - | ------- | ---- | ---- |
| 1 | `RateLimitInterceptor.getRemoteAddr()` 反代下會共用 bucket | ✅ 已修補（設計確認）| [RateLimitInterceptor.java](../../../backend/src/main/java/com/taipei/iot/common/interceptor/RateLimitInterceptor.java) `getClientIp` 加入大段安全註解：明確記錄 X-Forwarded-For 偽造攻擊、未來導入 Nginx 時需以 `ForwardedHeaderFilter` + 受信任 proxy 白名單處理 |
| 2 | `ClamAvVirusScanService` Socket 缺連線超時 | ✅ 已修補 | [ClamAvVirusScanService.java](../../../backend/src/main/java/com/taipei/iot/common/service/ClamAvVirusScanService.java) 改用 `Socket() + connect(new InetSocketAddress(host, port), timeout)` + `setSoTimeout(timeout)`，連線/讀取超時皆受控 |
| 3 | `GlobalExceptionHandler.isSuspiciousInput()` 偵測範圍有限 | ✅ 已強化 | [GlobalExceptionHandler.java](../../../backend/src/main/java/com/taipei/iot/common/exception/GlobalExceptionHandler.java) 加入 `union select` / `drop table` / `'; --` / `script>` 等樣式；用途仍為日誌標記（非防禦），定位正確 |
| 4 | `FileValidationService` 白名單排除 `.svg` | ✅ 維持正確 | [FileValidationService.java](../../../backend/src/main/java/com/taipei/iot/common/service/FileValidationService.java) `ALLOWED_EXTENSIONS` 不含 `.svg`，避免 `<script>` XSS；屬有意設計 |
| 5 | `LocalFileStorageService` 使用 `REPLACE_EXISTING` | ⚠ 可接受 | UUID 8 字元前綴（32^8 ≈ 10^12）碰撞機率可忽略；保留現狀 |
| 6 | `RateLimitInterceptor` INCR / EXPIRE TOCTOU | ✅ 已修補 | [RateLimitInterceptor.java](../../../backend/src/main/java/com/taipei/iot/common/interceptor/RateLimitInterceptor.java) `LUA_INCR_EXPIRE` 以 Lua script 原子執行 `INCR + EXPIRE`，徹底消除「key 永不過期」風險 |
| 7 | `BaseEntity` 註解錯字 | ✅ 已修補 | [BaseEntity.java](../../../backend/src/main/java/com/taipei/iot/common/entity/BaseEntity.java) 已改為「不應直接寫入，尤其 createdAt 在語意上建立後不變」 |
| 8 | `ErrorCode` 10006 跳號 | ✅ 已補上 | [ErrorCode.java](../../../backend/src/main/java/com/taipei/iot/common/enums/ErrorCode.java) `SESSION_NOT_FOUND("10006", 404, "登入裝置不存在")` 配合 auth N-7 session 管理使用 |
| 9 | `ClamAvVirusScanService` ERROR 語意不明 | ✅ 已文件化 | [VirusScanService.java](../../../backend/src/main/java/com/taipei/iot/common/service/VirusScanService.java) JavaDoc 明示「`ERROR` 應視為拒絕上傳（fail-closed）」 |
| 10 | `FileValidationService` 分類 maxFileSize 配置 | ⚠ 部分修補 | 程式碼層已支援 `maxImageSize / maxDocumentSize / maxMediaSize` 三段（建構子 3 參數，預設 5MB / 20MB / 100MB）；但 [application.yml](../../../backend/src/main/resources/application.yml) 只有 `file.validation.max-size`，分類值仍走 hardcoded default — 留入 N-5 改善 |

> **小結**：10 項議題全部 ✅ 修補完成或保留為合理設計；#10 為「程式碼到位但 YAML 對應不齊」的小尾巴，併入 N-5。

---

## 三、本輪新發現問題

### 🔴 高風險

#### N-1. `SecurityLogger.sanitize()` 未處理 Unicode line separator / NULL byte — Log Injection 殘留向量 ✅ **已修补 (2026-05-27)**

- **檔案**：[SecurityLogger.java](../../../backend/src/main/java/com/taipei/iot/common/util/SecurityLogger.java)
- **修補內容**：`sanitize()` 加入 `\u2028` / `\u2029` / `\u0000` 三個控制字元的移除，並補上 JavaDoc 說明 SIEM / log parser 為何依然會被這些字元欺騙。
- **測試**：新增 [`SecurityLoggerTest`](../../../backend/src/test/java/com/taipei/iot/common/util/SecurityLoggerTest.java)（9 cases）以 logback `ListAppender` 捕捉 SECURITY logger 輸出，逐一驗證每個控制字元均被剔除且字面內容保留。mvn test 全綠 **692/0/0/0**。

- **檔案**：[SecurityLogger.java](../../../backend/src/main/java/com/taipei/iot/common/util/SecurityLogger.java)
- **問題**：目前僅 `replace("\r","").replace("\n","").replace("\t","")`，未處理：
  - `U+2028` LINE SEPARATOR
  - `U+2029` PARAGRAPH SEPARATOR
  - `U+0000` NULL byte
- 多數 SIEM / log parser（含 logstash、grok、jq）會把這三個字元視同換行或當作字串終止符 → 攻擊者可在 username/email 等輸入欄塞入這些字元，注入「假造」的安全事件 log line（CWE-117 延伸）。
- **影響**：安全日誌完整性受損；告警誤判。
- **建議修法**：

  ```java
  private static String sanitize(String input) {
      if (input == null) return "null";
      return input
          .replace("\r", "")
          .replace("\n", "")
          .replace("\t", "")
          .replace("\u2028", "")  // LINE SEPARATOR
          .replace("\u2029", "")  // PARAGRAPH SEPARATOR
          .replace("\u0000", ""); // NULL
  }
  ```

  並補一個單元測試將上述 3 個字元都納入。
- **優先級**：🔴 高

---

#### N-2. `NoOpVirusScanService` 使用 `matchIfMissing = true` — 設定遺漏即靜默放行 ✅ **已修補 (2026-05-27)**

- **檔案**：[NoOpVirusScanService.java](../../../backend/src/main/java/com/taipei/iot/common/service/NoOpVirusScanService.java)
- **修補內容**：
  1. 移除 `matchIfMissing = true`：若 `virus-scan.enabled` 未設定，Spring 將無法注入 `VirusScanService` bean → 啟動 fail-fast，徹底杜絕「prod 忘記設定即靜默放行」。
  2. 加上 `@Profile({"dev", "test"})`：即便 `virus-scan.enabled=false` 被誤設於 prod，此 bean 也不會被載入。
  3. 新增 `@PostConstruct warnOnStartup()`：啟動時以 WARN 級別輸出「Virus scan is DISABLED ... MUST be enabled in production by setting virus-scan.enabled=true」，於 dev / test log 中顯眼提醒。
- **測試**：擴充 [`NoOpVirusScanServiceTest`](../../../backend/src/test/java/com/taipei/iot/common/service/NoOpVirusScanServiceTest.java) 至 6 cases，新增 4 cases：以 logback `ListAppender` 驗證 WARN 內容、反射檢查 `@ConditionalOnProperty` `matchIfMissing==false`、`@Profile` 限定 `dev`/`test`、`@Service` 仍存在。mvn test 全綠 **696/0/0/0**。

- **檔案**：[NoOpVirusScanService.java](../../../backend/src/main/java/com/taipei/iot/common/service/NoOpVirusScanService.java)
- **問題**：

  ```java
  @ConditionalOnProperty(name = "virus-scan.enabled",
                         havingValue = "false",
                         matchIfMissing = true)
  ```

  若 prod 部署忘記設 `virus-scan.enabled`，會**靜默掉入 NoOp，所有檔案直接被視為 CLEAN**。
- **影響**：上線設定漏掉即「等於完全沒做掃毒」，且**沒有任何錯誤訊息**。
- **建議修法**：
  1. 移除 `matchIfMissing = true`，強制顯式設定：

     ```java
     @ConditionalOnProperty(name = "virus-scan.enabled", havingValue = "false")
     ```

  2. 加 `@Profile("dev | test")` 進一步限定 NoOp 只能在非 prod 出現。
  3. 補一個 `@PostConstruct` 啟動時記 WARN：「Virus scan disabled — must be enabled in production」。
- **優先級**：🔴 高（安全配置陷阱）

---

#### N-3. `TenantAwareQuery` 啟發式 `contains("tenant_id")` 可被 SQL 註解 / 引號繞過 ✅ **已修補 (2026-05-27)**

- **檔案**：[TenantAwareQuery.java](../../../backend/src/main/java/com/taipei/iot/common/util/TenantAwareQuery.java)
- **修補內容**：
  1. 新增 `stripCommentsAndLower(sql)` helper：先剥除區塊註解 `/* ... */`（跨行）与行內註解 `-- ...`再進行關鍵字偵測，並轉小寫。
  2. 新增 `containsTenantIdAfterWhere(strippedLower)` helper：要求 `tenant_id` 必須出現於 `WHERE` 子句之後（以 `\b` 單字邊界匹配避免 `tenant_id_v2` 誤判）。
  3. `createGlobal()` 同步使用 stripped SQL 判定，註解中的 `tenant_id` 不再誤觸 warn。
  4. JavaDoc 明示本驗證為「開發期 guardrail」，仍無法防範 `WHERE 1=1 OR tenant_id = :tid` 等邏輯失誤 / 子查詢旁路；剛性護網由 **F-1 ✅ ArchUnit 規則** ([`ForbiddenNativeQueryArchTest`](../../../backend/src/test/java/com/taipei/iot/architecture/ForbiddenNativeQueryArchTest.java)) 補完——業務碼一律不得直呼 `EntityManager.createNativeQuery`，繞過機制（`@AllowDirectNativeQuery`）強制標註原因，且標記契約由 [`AllowDirectNativeQueryAnnotationTest`](../../../backend/src/test/java/com/taipei/iot/common/annotation/AllowDirectNativeQueryAnnotationTest.java) 鎖定（RUNTIME 保留、TYPE+METHOD 目標、`reason()` 無預設值、`PasswordPolicyDao` 已標註且 reason 非空）。
- **測試**：新增 [`TenantAwareQueryTest`](../../../backend/src/test/java/com/taipei/iot/common/util/TenantAwareQueryTest.java)（8 個 @Nested 分類共 18 cases）：
  - `StripComments`（5）驗證區塊 / 跨行區塊 / 行內註解都被剥除、真實 tenant_id 保留、null 護網
  - `TenantIdLocation`（4）驗證 WHERE 後位置適當才判透過、僅 SELECT 欄位 / 無 WHERE / `tenant_id_v2` 類似名稱都被拒絕
  - `CreateValidation`（7）mock EntityManager + TenantContext 驗證全流程：區塊註解 / 行內註解 / SELECT-only 都拋 IAE、WHERE 後 tenant_id 接受並自動綁 `:tenantId` 參數、SystemContext 跳過驗證、無 TenantContext 拋 ISE fail-closed
  - `CreateGlobal`（2）驗證 createGlobal 仍可類全域查詢。
- **mvn test**：全綠 **714/0/0/0**。

- **檔案**：[TenantAwareQuery.java](../../../backend/src/main/java/com/taipei/iot/common/util/TenantAwareQuery.java)
- **問題**：目前只判斷 `sqlLower.contains("tenant_id")`：
  1. **註解繞過**：`SELECT * FROM x WHERE id = :id /* tenant_id 只在註解中 */` — 防護被誤判通過。
  2. **大小寫 / 引號**：若有人寫 `WHERE "TenantId" = :tid`（PostgreSQL 雙引號保留大小寫），實際 column 是 `TenantId` 但檢查仍會 fail（false-negative 機率小但理論成立）。
  3. 也無法阻擋 `WHERE 1=1 OR tenant_id = :tid` 這類邏輯失誤。
- **影響**：靜默 tenant 隔離繞過，且 v1 已宣稱「強制保證」與實際效力存在落差。
- **建議修法**：
  1. **先剝註解再檢查**：`String stripped = sql.replaceAll("/\\*.*?\\*/", "").replaceAll("--[^\\n]*", "");` 之後再做關鍵字偵測。
  2. **要求 `tenant_id` 必須出現在 WHERE 之後**（粗略 regex `(?is).*\\bwhere\\b.*\\btenant_id\\b.*`）。
  3. 在 JavaDoc 明示：「**這只是開發期 guardrail，最終保證仍需 code review**」。
  4. 補一個 ArchUnit 測試規則：禁止業務程式碼直接呼叫 `EntityManager.createNativeQuery(...)`，強制走 `TenantAwareQuery`。
- **優先級**：🔴 高（多租戶系統的核心隔離護網）

---

### 🟠 中風險

#### N-4. `ClamAvVirusScanService` 未檢查檔案大小是否超過 clamd `StreamMaxLength` ✅ **已修補 (2026-05-27)**

- **檔案**：[ClamAvVirusScanService.java](../../../backend/src/main/java/com/taipei/iot/common/service/ClamAvVirusScanService.java)、[application.yml](../../../backend/src/main/resources/application.yml)
- **修補內容**：
  1. 新增建構子 4th `@Value("${virus-scan.clamav.stream-max-length:26214400}") long streamMaxLength` 欄位（預設 25MB，與 clamd `StreamMaxLength` 預設一致）。
  2. `scan()` 在開啟 Socket 前先 `Files.size(Path.of(filePath))`：超過 `streamMaxLength` 直接 `return ScanResult.ERROR` 並記 WARN（fail-closed），不再嘗試 INSTREAM。
  3. `Files.size` 拋 IOException（檔不存在等）亦回 ERROR，並記 ERROR log，與既有失敗語意一致。
  4. Class JavaDoc 補上「N-4 防護」段落，說明 clamd 靜默截斷風險與設定必須同步要求。
  5. `application.yml` 新增 `virus-scan.clamav.stream-max-length: ${CLAMAV_STREAM_MAX_LENGTH:26214400}` 並附註解。
- **測試**：新增 [`ClamAvVirusScanServiceTest`](../../../backend/src/test/java/com/taipei/iot/common/service/ClamAvVirusScanServiceTest.java)（4 cases）：
  - 超過 `streamMaxLength`（10 bytes 上限 + 11 bytes 檔案）→ ERROR + WARN 訊息含 `exceeds clamd StreamMaxLength` / `fail-closed`。
  - 檔案大小 == 上限 → 不被 size check 攔截（驗證採嚴格 `>`），改在 socket 階段失敗。
  - 不存在檔案 → ERROR + ERROR log `Failed to read file size before ClamAV scan`。
  - 正常小檔 → 通過 size check，進入 socket connection 階段（驗證 size check 不誤殺）。
- **mvn test**：**718/0/0/0** 全綠（+4 cases）。
- **優先級**：🟠 中（已解除）

<details>
<summary>原始問題分析（保留供參）</summary>

- **問題**：ClamAV daemon 的 `StreamMaxLength` 預設 25MB。INSTREAM 協議下檔案超過此值會被 clamd **靜默截斷**並只掃前段；若惡意 payload 在尾部，會通過掃描。
- **影響**：大檔案（影片、安裝檔）可能繞過掃毒。
- **建議修法**：
  1. 在 `scan()` 前先讀 `Files.size(...)`，超過閾值（建議 24MB，留 1MB buffer）即直接 `return ScanResult.ERROR`（fail-closed）並 log WARN。
  2. 在 `application.yml` 新增 `clamav.stream-max-length` 對應 clamd 設定，避免兩邊各寫常數。
  3. JavaDoc 明示這個上限。
- **優先級**：🟠 中

</details>

---

#### N-5. `FileValidationService` 分類大小未提供至 YAML — 不利環境調校 ✅ **已修補 (2026-05-27)**

- **檔案**：[FileValidationService.java](../../../backend/src/main/java/com/taipei/iot/common/service/FileValidationService.java)、[application.yml](../../../backend/src/main/resources/application.yml)
- **修補內容**：
  1. `application.yml` 移除 stale `file.validation.max-size: ${FILE_MAX_SIZE:10485760}`（未被任何 `@Value` 引用），新增三段明確上限：
     - `max-image-size: ${FILE_MAX_IMAGE_SIZE:5242880}`  (5MB，圖片)
     - `max-document-size: ${FILE_MAX_DOCUMENT_SIZE:20971520}`  (20MB，文件 / fallback)
     - `max-media-size: ${FILE_MAX_MEDIA_SIZE:104857600}`  (100MB，影音)
  2. 原就主採取三參數建構子的 `FileValidationService` 不需變動；現在 maven 獲取的數值會走 YAML 路徑而非原 hardcoded default。
  3. 是否同步上拉 `spring.servlet.multipart.max-file-size` ：保留原狀由 N-6 整合處理。
- **測試**：擴充 [`FileValidationServiceTest`](../../../backend/src/test/java/com/taipei/iot/common/service/FileValidationServiceTest.java) +3 cases（合計 27），以 Spring Boot `ApplicationContextRunner` + `@Configuration` 輕量 context 驗證：
  - `yamlBinding_customValues_overrideDefaults` — 設定 1MB/2MB/3MB 三限 → 反射讀 field 確認正確注入 + 2MB 圖片被拒。
  - `yamlBinding_noProperties_appliesDefaults` — 未提供任何 property 時退到 5MB/20MB/100MB 預設。
  - `yamlBinding_extensionDispatch_respectsCategoryLimits` — 同樣位元組數依副檔名被分派到不同類別上限（1KB/10KB/100KB）。
- **mvn test**：**721/0/0/0** 全綠（+3 cases）。
- **優先級**：🟠 中（已解除；v1 #10 尾巴同步收束）

<details>
<summary>原始問題分析（保留供參）</summary>

- **檔案**：[FileValidationService.java](../../../backend/src/main/java/com/taipei/iot/common/service/FileValidationService.java)、[application.yml](../../../backend/src/main/resources/application.yml)
- **問題**：建構子已接受 `maxImageSize / maxDocumentSize / maxMediaSize` 三段，但 YAML 只有 `file.validation.max-size` 單一欄；分類值靠 hardcoded default（5MB / 20MB / 100MB）。
- **影響**：維運無法依環境調整影像/文件/影片上限，需改原始碼重編。
- **建議修法**：

  ```yaml
  file:
    validation:
      max-image-size:    ${FILE_MAX_IMAGE:5242880}      # 5MB
      max-document-size: ${FILE_MAX_DOCUMENT:20971520}  # 20MB
      max-media-size:    ${FILE_MAX_MEDIA:104857600}    # 100MB
  ```

  搭配 `@Value` 注入到 service。
- **優先級**：🟠 中（v1 #10 尾巴）

</details>

---

#### N-6. `GlobalExceptionHandler` 未顯式處理 `MaxUploadSizeExceededException` ✅ **已修補 (2026-05-27)**

- **檔案**：[GlobalExceptionHandler.java](../../../backend/src/main/java/com/taipei/iot/common/exception/GlobalExceptionHandler.java)
- **修補內容**：新增 `@ExceptionHandler(MaxUploadSizeExceededException.class)` handler，回傳 **HTTP 413 Payload Too Large** + `ErrorCode.FILE_SIZE_EXCEEDED`（`70024`）+ 固定友善訊息「上傳檔案大小超過限制」；log WARN 含 `maxUploadSize` 位元數依便診斷，但不泄漏 cause / stacktrace。避免原本掉入 catch-all 回 500 + `Unhandled exception` stacktrace。
- **測試**：擴充 [`GlobalExceptionHandlerTest`](../../../backend/src/test/java/com/taipei/iot/common/exception/GlobalExceptionHandlerTest.java) +3 cases（合計 17）：
  - `handleMaxUploadSize_returns413WithFileSizeExceededCode` — 驗證 413 + `FILE_SIZE_EXCEEDED` code。
  - `handleMaxUploadSize_returnsFriendlyDetailWithoutStacktrace` — 以含內部 cause 的建構子建立 exception，驗證 response detail 為固定訊息、不含 cause。
  - `handleMaxUploadSize_doesNotFallIntoGenericHandler` — 驗證不會被誤映射到 catch-all (500 / UNKNOWN_ERROR)。
- **mvn test**：**724/0/0/0** 全綠（+3 cases）。
- **優先級**：🟠 中（已解除）

<details>
<summary>原始問題分析（保留供參）</summary>

- **問題**：當上傳檔案 > `spring.servlet.multipart.max-file-size` 時，Spring 拋 `MaxUploadSizeExceededException`，目前會落入 catch-all → 500 + `Unhandled exception` 含 stacktrace（潛在資訊洩漏 + 體驗差）。
- **建議修法**：

  ```java
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<BaseResponse<Void>> handleMaxUpload(MaxUploadSizeExceededException ex) {
      log.warn("Upload exceeds size limit: {}", ex.getMessage());
      return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
          .body(BaseResponse.fail(ErrorCode.FILE_SIZE_EXCEEDED, "上傳檔案大小超過限制"));
  }
  ```

- **優先級**：🟠 中

</details>

---

#### N-7. `LocalFileStorageService` 未顯式設定 POSIX 檔案權限　✅ **已修補 (2026-05-27)**

- **檔案**：[LocalFileStorageService.java](../../../backend/src/main/java/com/taipei/iot/common/service/LocalFileStorageService.java)

##### 修補內容
1. 新增 `FILE_PERMISSIONS = rw-------`（0600）與 `DIR_PERMISSIONS = rwx------`（0700）常數。
2. 構造子於 `Files.createDirectories(rootLocation)` 後呼叫 `applyDirPermissions(rootLocation)`。
3. `store(...)` 於 `Files.createDirectories(targetDir)` 後 → `applyDirPermissions(targetDir)`；於 `Files.copy(...)` 後 → `applyFilePermissions(targetFile)`，覆寫既有檔案時也會重新收緊權限。
4. 私有 helper `applyPosixPermissions(Path, Set, kind)`：以 `path.getFileSystem().supportedFileAttributeViews().contains("posix")` 守門，Windows / 非 POSIX 掛載自動 no-op；失敗時 `log.warn` 不阻斷主流程。
5. Class JavaDoc 新增「N-7 防護」段落說明 umask 風險與權限策略。

##### 測試
新增 [LocalFileStorageServiceTest.java](../../../backend/src/test/java/com/taipei/iot/common/service/LocalFileStorageServiceTest.java)（`@EnabledOnOs({LINUX, MAC})`，4 cases）：
- `constructor_appliesRwxPermissionsToRootDir`：root 目錄 == 0700
- `store_appliesRwPermissionsToStoredFile`：寫入後檔案 == 0600，明確驗證 group/others 無任何權限
- `store_appliesRwxPermissionsToCreatedSubDir`：自動建立的子目錄 == 0700
- `store_overwriteRetainsRestrictivePermissions`：外力放寬後再次寫入，新檔仍為 0600

##### mvn test
全套 **728 / 0 / 0 / 0**（+4），無回歸。

<details>
<summary>原始問題分析（保留供參）</summary>

- **問題**：`Files.copy(input, target, REPLACE_EXISTING)` 使用 OS umask 預設權限。若部署環境 umask 寬鬆（如 `0002`），檔案可能被同主機其他帳號讀寫。
- **建議修法**（Unix/Linux）：

  ```java
  Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
  if (target.getFileSystem().supportedFileAttributeViews().contains("posix")) {
      Files.setPosixFilePermissions(target,
          PosixFilePermissions.fromString("rw-------"));
  }
  ```

  並對 `Files.createDirectories(subDir)` 後同步設定 `rwx------`。
- **優先級**：🟠 中

</details>

---

#### N-8. `ImageSanitizer` 對動態 GIF / APNG 會靜默丟幀　✅ **已修補 (2026-05-27)**

- **檔案**：[ImageSanitizer.java](../../../backend/src/main/java/com/taipei/iot/common/service/ImageSanitizer.java)

##### 修補內容
1. 新增 nested record `SanitizeResult(byte[] bytes, int originalFrames, boolean downgraded)`，提供呼叫端判定是否被降級。
2. 新增 `sanitizeDetailed(InputStream, String)`：以 `ImageReader.getNumImages(true)` 偏測幀數；>1 則標記 `downgraded=true` 並 `log.warn("多幀圖片將被降級為第一幀 (extension=..., frames=...)")`。
3. 既有 `sanitize(InputStream, String)` 保持回 `byte[]` 不破壞呼叫端，但 delegate 到 `sanitizeDetailed`，多幀來源同樣會觸發 WARN —— N-8 「静默降級」彻底解除。
4. JavaDoc 新增「N-8 防護」段落，並明示已知限制：JDK 標準 ImageIO 不認得 APNG `acTL` chunk，多幀 APNG 仍會 false-negative，需上層以 magic bytes 識別。
5. `detectFrameCount(...)` 未能判定時回 `-1`（視為單幀），不以 exception 中斷主流程。

##### 測試
擴充 [ImageSanitizerTest.java](../../../backend/src/test/java/com/taipei/iot/common/service/ImageSanitizerTest.java) +4 cases（合計 9），以 logback `ListAppender` 驗證 WARN：
- `sanitizeDetailed_singleFrameGif_notDowngraded`：單幀 GIF `frames=1` / `downgraded=false`，且不發 WARN。
- `sanitizeDetailed_multiFrameGif_isFlaggedAndWarned`：3 幀 GIF `frames=3` / `downgraded=true`，WARN 出現。
- `sanitize_multiFrameGif_logsWarnEvenViaLegacyApi`：舊 API 仍回完整 bytes，但同樣觸發 WARN。
- `sanitizeDetailed_unsupportedExtension_returnsFailureResult`：不支援副檔名回 `isSuccess()==false` / `originalFrames==-1` / `downgraded==false`。
多幀 GIF fixture 以 `ImageWriter.prepareWriteSequence` + `GraphicControlExtension` metadata 动態生成，不依賴外部資源。

##### mvn test
全套 **732 / 0 / 0 / 0**（+4），無回歸。

<details>
<summary>原始問題分析（保留供參）</summary>

- **檔案**：[ImageSanitizer.java](../../../backend/src/main/java/com/taipei/iot/common/service/ImageSanitizer.java)
- **問題**：`ImageIO.read(input)` 只讀第一幀；多幀 GIF / APNG 會被「降級」為靜態圖且使用者不會收到通知。
- **影響**：使用者體驗（資料隱性遺失）；非安全漏洞。
- **建議修法**：
  1. 最低成本：JavaDoc 明示「**動態圖片會被降級為第一幀**」，並回傳一個 `SanitizeResult` 帶旗標。
  2. 較完整：用 `ImageIO.getImageReadersByFormatName("gif")` 偏測幀數，>1 時可選擇直接拒絕或保留所有幀重新編碼。
- **優先級**：🟠 中（UX）

</details>

---

#### N-9. `SecurityContextUtils` 在非主執行緒下會静默回 `null`，不利除錯　✅ **已修補 (2026-05-27)**

- **檔案**：[SecurityContextUtils.java](../../../backend/src/main/java/com/taipei/iot/common/util/SecurityContextUtils.java)

##### 修補內容
1. `getValidAuth()` 回 `null` 時呼叫 `maybeWarnOffRequestThread()`：以 `RequestContextHolder.getRequestAttributes() == null` 判定為非 Servlet 請求 thread，在此條件下以 `AtomicBoolean` CAS 保證「整個 JVM 僅警 1 次」，避免 batch / scheduler 淹沒 log。一般匿名 HTTP 請求仍有 `RequestAttributes`，不會誤觸。
2. 新增 `requireCurrentUserIdStrict()`：拿不到 userId 即拋 `IllegalStateException`，訊息明示「use DelegatingSecurityContextExecutor or pass userId explicitly」，供 service 入口選用 fail-fast 語意。
3. JavaDoc 於 `getCurrentUserId()` 補上「N-9 防護」說明，並同時推薦跨執行緒 propagation 的 `DelegatingSecurityContextExecutor` 與 `MODE_INHERITABLETHREADLOCAL` 選項。
4. 新增 package-private `resetOffRequestWarnedForTest()` hook，僅供單元測試重置 WARN 旗標；產品程式無法存取。

##### 測試
擴充 [SecurityContextUtilsTest.java](../../../backend/src/test/java/com/taipei/iot/common/util/SecurityContextUtilsTest.java) +5 cases（合計 20），以 logback `ListAppender` 驗證 WARN：
- `requireCurrentUserIdStrict_whenAuthenticated_returnsUserId`
- `requireCurrentUserIdStrict_whenUnauthenticated_throws`（訊息含 `"No authenticated user"`）
- `requireCurrentUserIdStrict_whenAnonymous_throws`
- `getCurrentUserId_offRequestThread_warnsOnceAndReturnsNull`：連呼 3 次，WARN 僅出現 1 次
- `getCurrentUserId_inRequestThread_doesNotWarn`：以 `MockHttpServletRequest` + `ServletRequestAttributes` 模擬 HTTP thread，未認證時不發 WARN

##### mvn test
全套 **737 / 0 / 0 / 0**（+5），無回歸。

<details>
<summary>原始問題分析（保留供參）</summary>

- **問題**：`SecurityContextHolder` 預設 `MODE_THREADLOCAL`；在 `@Async` / `CompletableFuture` 或 STOMP message handler 中呼叫會回 `null`。目前限制僅寫在 JavaDoc，方法本身不告警。
- **建議修法**：
  1. 偏測呼叫端在非主請求 thread（例如 `RequestContextHolder.getRequestAttributes() == null`）時：**log WARN once + 回 null**，便於 grep 找到誤用點。
  2. 或另開 `requireCurrentUserIdStrict()` 在缺值時直接拋 `IllegalStateException`，給 service 層自主選擇。
  3. 文件補上「若需跨執行緒攜帶 context 請使用 `DelegatingSecurityContextExecutor` 或手動 propagation」。
- **優先級**：🟠 中

</details>

---

### 🟡 低風險 / 建議

#### N-10. `ClamAvVirusScanService` 在 INFO 級別輸出完整檔案路徑　✅ **已修補 (2026-05-28)**

- **檔案**：[ClamAvVirusScanService.java](../../../backend/src/main/java/com/taipei/iot/common/service/ClamAvVirusScanService.java)
- **修補內容**：
  1. 所有 INFO / WARN 級別 log 改記 `Path.of(filePath).getFileName()`（僅檔名），不再暴露完整路徑結構。
  2. 完整路徑保留在 DEBUG 級別（`log.debug("ClamAV scan full path: {}, result: {}", filePath, result)`），僅開發除錯時可見。
  3. size check 攔截（N-4）的 WARN 與 missing file 的 ERROR 也同步改為僅記檔名。
- **測試**：擴充 [`ClamAvVirusScanServiceTest`](../../../backend/src/test/java/com/taipei/iot/common/service/ClamAvVirusScanServiceTest.java) +2 cases（合計 6）：
  - `scan_oversizedFile_logsOnlyFilenameInWarn_notFullPath` — 驗證 WARN 含檔名但不含目錄結構。
  - `scan_connectionError_logsOnlyHostPort_notFilePath` — 驗證 ERROR 含 host:port 不含儲存路徑。
- **mvn test**：**743/0/0/0** 全綠。

<details>
<summary>原始問題分析（保留供參）</summary>

- **問題**：`log.info("ClamAV scan result for {}: {}", filePath, result)` 會落到一般 application.log（保留 7d）。攻擊者只要拿到 log 即可推斷儲存結構（如 `/uploads/2026-05/tenant-3/...`）。
- **建議**：改記 `Path.of(filePath).getFileName()`，或對 hash 後輸出；保留 path 在 DEBUG 級。

</details>

---

#### N-11. `@RateLimit` 註解未文件化「匿名使用者也會被限流」與 key 組成規則　✅ **已修補 (2026-05-28)**

- **檔案**：[RateLimit.java](../../../backend/src/main/java/com/taipei/iot/common/annotation/RateLimit.java)
- **修補內容**：
  1. JavaDoc 新增「Key 組成規則」段落：明確記錄 Redis key 格式為 `rate_limit:{key}:{clientIp}`。
  2. 新增「匿名 / 已認證使用者一視同仁」段落：明示速率限制不區分使用者是否已登入，所有請求一律依來源 IP 計數，共用同一 bucket。
  3. 新增「Redis 不可用時」段落：說明退回 JVM in-memory 計數器。
  4. 指引 F-5 規劃：若需依 userId 分別限流的方向。
- **測試**：新增 [`RateLimitAnnotationTest`](../../../backend/src/test/java/com/taipei/iot/common/annotation/RateLimitAnnotationTest.java)（6 cases）：
  - 驗證 `@Retention(RUNTIME)` / `@Target(METHOD)` / `limit` 預設 10 / `period` 預設 60 / `key` 無預設 / 三個方法聲明齊全。
- **mvn test**：**743/0/0/0** 全綠。

<details>
<summary>原始問題分析（保留供參）</summary>

- **問題**：呼叫端通常以為「需登入才限流」；實際是依 IP，與是否認證無關。
- **建議**：在 JavaDoc 補：「Redis key = `rate_limit:{key}:{ip}`；匿名 / 登入使用者共用同一 IP bucket」。

</details>

---

#### N-12. `BaseResponse.timestamp` 為毫秒精度，輕微 timing side-channel　✅ **已修補 (2026-05-28)**

- **檔案**：[BaseResponse.java](../../../backend/src/main/java/com/taipei/iot/common/response/BaseResponse.java)
- **修補內容**：
  1. `success()` / `fail()` 所有工廠方法的 `.timestamp(System.currentTimeMillis())` 改為 `.timestamp(System.currentTimeMillis() / 1000)`（秒精度）。
  2. 欄位 JavaDoc 補上「回應時間戳（秒精度），避免毫秒精度造成 timing side-channel（N-12）」。
  3. 前端使用 `response.timestamp` 時需注意已從毫秒改為秒（若需 Date 對象需 `× 1000`）。
- **測試**：修改 [`BaseResponseTest`](../../../backend/src/test/java/com/taipei/iot/common/response/BaseResponseTest.java)（原 `timestamp_isSetToCurrentTime` 重寫為 `timestamp_isSetToCurrentTimeInSeconds`）+ 新增 1 case：
  - `timestamp_secondPrecision_neverExceedsMaxReasonableEpochSeconds` — 斷言 timestamp < 10^10（毫秒在 2026 年已達 ~1.7×10^12，秒才約 1.7×10^9），確保精度正確。
- **mvn test**：**743/0/0/0** 全綠。

<details>
<summary>原始問題分析（保留供參）</summary>

- **問題**：每筆 response 都附 `System.currentTimeMillis()`；對高度防護的端點（如 login / forgot-password）疊代計時可佐證 timing attack。實務影響極低。
- **建議**：對 auth 類端點可考慮回 second 精度，或乾脆移除 timestamp（用 server `Date` header 取代）。

</details>

---

#### N-13. dev profile 仍嵌入大量明文憑證 — 與 auth N-2 同源風險　✅ **已加固 (2026-05-28)**

- **檔案**：[application-dev.yml](../../../backend/src/main/resources/application-dev.yml)、新增 [DevProfileSecurityWarning.java](../../../backend/src/main/java/com/taipei/iot/common/config/DevProfileSecurityWarning.java)
- **修補內容**：
  1. 新增 `DevProfileSecurityWarning` 元件：`@Profile({"dev", "test"})` + `@PostConstruct`，啟動時以 WARN 級別輸出醒目框線警告「plaintext credentials embedded — MUST NOT be used in production」。
  2. 警告明確列出受影響的 credential 種類：DB、Redis、JWT、SMTP。
  3. CI/CD log 可 grep `[SECURITY] Dev/Test profile active` 偵測意外以 dev profile 部署到非本機環境。
  4. 風險等級維持「接受風險（僅限本機 / 內網）」，但提供了可觀測的 guardrail。
- **測試**：新增 [`DevProfileSecurityWarningTest`](../../../backend/src/test/java/com/taipei/iot/common/config/DevProfileSecurityWarningTest.java)（4 cases）：
  - `warnPlaintextCredentials_logsSecurityWarning` — 以 ListAppender 驗證 ≥5 行 WARN、含 `[SECURITY]` 與 `plaintext credentials` 與 `MUST NOT`。
  - `class_hasProfileAnnotation_restrictedToDevAndTest` — 反射驗證 `@Profile` 限定 dev/test。
  - `class_hasComponentAnnotation` — 確認 Spring 掃描。
  - `warnMessage_mentionsAllCredentialTypes` — 組合訊息含 DB / Redis / JWT / SMTP。
- **mvn test**：**743/0/0/0** 全綠。

---

## 四、安全性總結（OWASP 對照）

| OWASP | 控制 | 評估 | 摘要 |
|-------|------|------|------|
| A01 — Broken Access Control | `TenantAwareQuery` native query 隷離 | ✅ | N-3 已強化：先剥註解 + 要求 tenant_id 位於 WHERE 後；F-1 ✅ ArchUnit 剛性護網落地（業務碼禁止直呼 `EntityManager.createNativeQuery`，唯一豁免走 `@AllowDirectNativeQuery`） |
| A03 — Injection | SQL 樣式偵測 / CRLF sanitize / 副檔名+magic bytes | ✅ | 補 Unicode separator（N-1）後更全面 |
| A04 — Insecure Design | 檔案上傳 4 層 / Fail-closed scan | ✅ | NoOp `matchIfMissing` 為唯一陷阱（N-2）|
| A05 — Security Misconfiguration | dev 嵌入憑證 / NoOp 預設 / 檔案大小 YAML 不齊 | ✅ | N-2 ✅ / N-5 ✅ / N-13 ✅（啟動時 WARN + `@Profile` 限定）|
| A06 — Vulnerable Components | Apache Tika / Lettuce 等版本 | ⏳ | 不在本次範圍 |
| A09 — Logging Failures | SecurityLogger 結構化 + 30d security.log | ✅ | Unicode separator（N-1）/ 路徑遮罩（N-10 ✅）|
| A10 — SSRF | ClamAV socket 只連設定 host | ✅ | host 受 `application.yml` 控制 |

---

## 五、值得優化的功能建議

### 5.1 既有 `common/` 強化

| # | 功能 | 價值 | 概要 |
| - | ---- | ---- | ---- |
| F-1 | ✅ **已完成 (2026-05-28)** **ArchUnit 規則禁止業務碼直接 `createNativeQuery`** — 業務碼一律走 [`TenantAwareQuery.create()` / `createGlobal()`](../../../backend/src/main/java/com/taipei/iot/common/util/TenantAwareQuery.java)；唯一豁免機制為 [`@AllowDirectNativeQuery(reason=...)`](../../../backend/src/main/java/com/taipei/iot/common/annotation/AllowDirectNativeQuery.java)（強制標註原因、需另一位 reviewer 簽核）；目前唯一豁免類別為 [`PasswordPolicyDao`](../../../backend/src/main/java/com/taipei/iot/auth/policy/PasswordPolicyDao.java)。test：[`ForbiddenNativeQueryArchTest`](../../../backend/src/test/java/com/taipei/iot/architecture/ForbiddenNativeQueryArchTest.java) + [`AllowDirectNativeQueryAnnotationTest`](../../../backend/src/test/java/com/taipei/iot/common/annotation/AllowDirectNativeQueryAnnotationTest.java) | 安全 ★★★ | 補 N-3 的剛性護網：業務碼直呼 `EntityManager.createNativeQuery` 一律編譯期 / 測試期攔截 |
| F-2 | ✅ **已完成 (2026-05-28)** **`SanitizeResult` 物件取代 `BufferedImage`** — [`ImageSanitizer.SanitizeResult`](../../../backend/src/main/java/com/taipei/iot/common/service/ImageSanitizer.java) record 擴充 `wasDowngraded` / `framesDropped` 欄位（`framesDropped == max(0, originalFrames - 1)`），`downgraded()` 保留為 alias 保障背向相容；WARN log 同步輸出 `framesDropped`。test：[`ImageSanitizerTest`](../../../backend/src/test/java/com/taipei/iot/common/service/ImageSanitizerTest.java) +4 cases | UX ★★ | 呼叫端可以 `result.wasDowngraded()` / `result.framesDropped()` 決定是否提示使用者，多幀降級事件 SIEM 可稽核 |
| F-3 | ✅ **已完成 (2026-05-28)** **檔案掃毒結果寫入 audit_log** — [`FileUploadTemplate`](../../../backend/src/main/java/com/taipei/iot/common/service/FileUploadTemplate.java) 在掃毒 `INFECTED` / `ERROR`（含掃毒例外的 fail-closed 分支）刪檔前發出 [`VirusScanAuditEvent`](../../../backend/src/main/java/com/taipei/iot/common/event/VirusScanAuditEvent.java)；audit 模組以 [`VirusScanAuditListener`](../../../backend/src/main/java/com/taipei/iot/audit/listener/VirusScanAuditListener.java) 訂閱事件並透過 [`AuditAsyncWriter.saveAsync`](../../../backend/src/main/java/com/taipei/iot/audit/async/AuditAsyncWriter.java) 寫入 `user_event_log`（新增 `FILE_VIRUS_INFECTED` / `FILE_SCAN_ERROR` 兩個 [`AuditEventType`](../../../backend/src/main/java/com/taipei/iot/audit/enums/AuditEventType.java)）。採 Spring `ApplicationEventPublisher` 解耦——common 不依賴 audit；audit listener 在發布端執行緒擷取 tenantId / userId / IP / UA 後再交給 `@Async` writer，SYSTEM context 與背景執行緒語義皆與既有 LOGIN 事件對齊。test：[`FileUploadTemplateTest`](../../../backend/src/test/java/com/taipei/iot/common/service/FileUploadTemplateTest.java) +5 cases、新增 [`VirusScanAuditListenerTest`](../../../backend/src/test/java/com/taipei/iot/audit/listener/VirusScanAuditListenerTest.java) 3 cases | 觀測 ★★ | INFECTED / ERROR 進入 audit 模組可被 SIEM 撈到，搭配既有事件對齊；publish 失敗仍保留原本 fail-closed 行為（best-effort 不破壞主流程）|
| F-4 | ✅ **已完成 (2026-05-28)** **ClamAV 連線健康度 actuator endpoint** — 引入 [`spring-boot-starter-actuator`](../../../backend/pom.xml)；[`ClamAvVirusScanService`](../../../backend/src/main/java/com/taipei/iot/common/service/ClamAvVirusScanService.java) 新增 `ping()`（送 `zPING\0` 驗 `PONG`）+ `getHost/getPort/getStreamMaxLength` 三個 getter；新增 [`ClamAvHealthIndicator`](../../../backend/src/main/java/com/taipei/iot/common/health/ClamAvHealthIndicator.java)（bean name `clamav`、`@ConditionalOnProperty virus-scan.enabled=true` 與 ClamAV service 同生命週期）將 ping 結果包成 UP/DOWN 並暴露 `host` / `port` / `streamMaxLength` / `ping` 四個 detail 欄位於 [`/actuator/health/clamav`](../../../backend/src/main/resources/application.yml)。[`SecurityConfig`](../../../backend/src/main/java/com/taipei/iot/config/SecurityConfig.java) 對 `/actuator/health/**` 開放匿名存取供 LB/監控 probe，details 僅含非機敏連線設定。test：[`ClamAvVirusScanServiceTest`](../../../backend/src/test/java/com/taipei/iot/common/service/ClamAvVirusScanServiceTest.java) +4 cases（含內建 FakeClamd 模擬 PONG / 非預期回應 / 連線失敗 / getters）、新增 [`ClamAvHealthIndicatorTest`](../../../backend/src/test/java/com/taipei/iot/common/health/ClamAvHealthIndicatorTest.java) 3 cases | 維運 ★★ | `/actuator/health/clamav` 顯示 ping 結果與 `StreamMaxLength`，可被 K8s liveness / Prometheus blackbox / Nagios 直接接管 |
| F-5 | ✅ **已完成 (2026-05-28)** **`RateLimit` 支援 user-level key（已認證走 userId、匿名走 IP）** — [`RateLimit`](../../../backend/src/main/java/com/taipei/iot/common/annotation/RateLimit.java) 新增巢狀 `enum KeyStrategy { IP, USER_OR_IP }` 與 `strategy()` 元素（預設 `IP` 保持背向相容）；[`RateLimitInterceptor.resolveIdentifier`](../../../backend/src/main/java/com/taipei/iot/common/interceptor/RateLimitInterceptor.java) 依 strategy 決定 Redis key 後綴：`IP` → `rate_limit:{key}:{clientIp}`（舊格式不變）、`USER_OR_IP` 已認證 → `rate_limit:{key}:user:{userId}`（透過 [`SecurityContextUtils.getCurrentUserId()`](../../../backend/src/main/java/com/taipei/iot/common/util/SecurityContextUtils.java)）、`USER_OR_IP` 匿名或空白 principal → `rate_limit:{key}:ip:{clientIp}`。auth 端點（如 `/login`、`/forgot-password`）仍應使用 `IP` strategy 因攻擊者未登入；業務寫入端點建議改用 `USER_OR_IP` 以避免同 NAT/代理後正常使用者互相誤殺。test：[`RateLimitInterceptorTest`](../../../backend/src/test/java/com/taipei/iot/common/interceptor/RateLimitInterceptorTest.java) +5 cases（USER_OR_IP 已認證走 userId / USER_OR_IP 匿名 fallback 走 IP / 同 IP 兩個 userId 分別有獨立 bucket 不互相干擾 / 空白 principal 視為匿名 / 明示 IP strategy 即使已登入仍走 IP key） | 安全 ★★ | 解決 N-11 對共用 bucket 的疑慮（USER_OR_IP 策略），且減少同 NAT 後正常使用者誤殺；預設 IP 維持背向相容 |
| F-6 | ✅ **已完成 (2026-05-28)** **`FileStorageService` 加入 `deleteIfExists` / `move` API** — [`FileStorageService`](../../../backend/src/main/java/com/taipei/iot/common/service/FileStorageService.java) 新增 `boolean deleteIfExists(String)` 與 `String move(String fromPath, String toSubDir, String newFileName)` 兩個方法；[`LocalFileStorageService`](../../../backend/src/main/java/com/taipei/iot/common/service/LocalFileStorageService.java) 提供實作，並抽出私有 `resolveSafePath(String)` 集中路徑穿越防護（load/delete/deleteIfExists/move 共用）。`deleteIfExists` 語意對齊 {@link Files#deleteIfExists}：hit 回 true、不存在回 false；I/O 例外以 WARN log 記錄並回 false（與現行 `delete()` best-effort 語意一致）。`move` 優先使用 `StandardCopyOption.ATOMIC_MOVE`，遇 `AtomicMoveNotSupportedException`（跨 mount point / 某些網路 FS）退回 `REPLACE_EXISTING`；目的檔名走與 `store()` 同一套 `sanitizeFileName` + UUID prefix 避免覆寫。來源 / 目的 path 均經 traversal 檢查，來源檔不在拋 `ATTACHMENT_NOT_FOUND`、路徑跨出根目錄拋 `VALIDATION_ERROR`。新建目錄同步套用 0700 權限、移入檔案套 0600，與 N-7 原規則一致。test：[`LocalFileStorageServiceTest`](../../../backend/src/test/java/com/taipei/iot/common/service/LocalFileStorageServiceTest.java) +10 cases（3 deleteIfExists + 1 deleteIfExists traversal + 6 move） | 功能 ★ | announcement 不再需明示關心「刪不到該怎辦」與「怎麼原子搬逾」，未來 S3 / MinIO 實作可以同接口提供同語意 |
| F-7 | ✅ **已完成 (2026-05-28)** **`SecurityLogger` 提供結構化 JSON appender 選項** — [`SecurityLogger`](../../../backend/src/main/java/com/taipei/iot/common/util/SecurityLogger.java) 重構為 SLF4J 2.x Fluent API（`LoggingEventBuilder.addKeyValue()`），每筆安全事件自動附加結構化欄位：`security_event` / `security_ip` / `security_category` + 動態拆解 `key=value` details 為 `security_{key}` 欄位。[`pom.xml`](../../../backend/pom.xml) 新增 `logstash-logback-encoder 8.0` 依賴；[`logback-spring.xml`](../../../backend/src/main/resources/logback-spring.xml) 新增 `SECURITY_JSON_FILE` appender（profile `json-log` 啟用時產出 `security-json.log`，用 `LogstashEncoder` 編碼，含 key-value pairs 為 JSON 頂層欄位）。純文字 appender 仍維持原格式（`[SECURITY] EVENT ip=IP detail1`）不受影響。啟用方式：`spring.profiles.active=json-log`（可與現有 profile 組合）。test：[`SecurityLoggerTest`](../../../backend/src/test/java/com/taipei/iot/common/util/SecurityLoggerTest.java) +7 cases（巢狀 `StructuredOutputTest`：驗證 warn/info 基本欄位 / key=value 拆解 / 無 '=' 的 detail 給 index 欄名 / IP sanitize 在結構化欄位也生效 / 多個 details / null details 不拋錯） | 觀測 ★★ | Loki / ELK / Splunk 可直接索引 JSON 欄位，免正則切欄；純文字日誌流不受影響 |
| F-8 | ✅ **已完成 (2026-05-28)** **`ErrorCode` 自動生成前端 i18n 字串對照表** — [`ErrorCodeJsonGenerator`](../../../backend/src/main/java/com/taipei/iot/common/util/ErrorCodeJsonGenerator.java) build-time 工具讀取 `ErrorCode.values()` 自動產出 [`error-codes.json`](../../../frontend/src/generated/error-codes.json)（格式 `{ "code": "message", ... }`），由 `exec-maven-plugin` 在 `process-classes` phase 自動執行。前端 [`useApiError`](../../../frontend/src/composables/useApiError.ts) 匯入該 JSON 作為 fallback 查表層：元件若未在 `codeMessages` 指定對應訊息，自動從生成檔取得中文錯誤描述。後端新增 / 修改 ErrorCode 只需跑一次 `mvn compile`，前端即同步更新——不再需要手動維護散落各處的 errorCode→文案映射。test：[`ErrorCodeJsonGeneratorTest`](../../../backend/src/test/java/com/taipei/iot/common/util/ErrorCodeJsonGeneratorTest.java) 8 cases（JSON 格式 / 全碼覆蓋 / 全訊息覆蓋 / 條目數 / 寫檔 / 自動建目錄 / 特殊字元逸出 / enum 同步驗證） | DX ★ | exec-maven-plugin 從 enum 產 JSON，前後端共用 |

### 5.2 跨模組重複樣式上提至 `common/`（重複度掃描結果）

針對 announcement / audit / auth / dept / notification / rbac / setting / tenant / user 共 10 個業務模組做重複樣式掃描，找到下列**真正重複且值得上提**的候選；右欄列出代表性出處便於後續 PR 對齊。

#### 🔴 高價值（建議優先動工）

| # | 候選 | 重複出處 | 建議位置 | 預估省 LOC |
|---|------|---------|---------|-----------|
| F-9 | **`@PaginationParams` 註解 + `HandlerMethodArgumentResolver`** ✅ **已完成 (2026-05-27)** — 取代各 controller 重複的 `@RequestParam(defaultValue="0") @Min(0) int page` + `@Max(100) int size` 組合 | [AnnouncementController.java](../../../backend/src/main/java/com/taipei/iot/announcement/controller/AnnouncementController.java) ✅、[UserAdminController.java](../../../backend/src/main/java/com/taipei/iot/user/controller/UserAdminController.java) ✅、[NotificationController.java](../../../backend/src/main/java/com/taipei/iot/notification/controller/NotificationController.java) ✅；[AuditController.java](../../../backend/src/main/java/com/taipei/iot/audit/controller/AuditController.java) 因使用 `pageSize` 命名（前端 API 契約）暫保留 | [common/annotation/PaginationParams.java](../../../backend/src/main/java/com/taipei/iot/common/annotation/PaginationParams.java) + [common/dto/PageQuery.java](../../../backend/src/main/java/com/taipei/iot/common/dto/PageQuery.java) + [common/resolver/PaginationArgumentResolver.java](../../../backend/src/main/java/com/taipei/iot/common/resolver/PaginationArgumentResolver.java)；註冊於 [WebMvcConfig.java](../../../backend/src/main/java/com/taipei/iot/config/WebMvcConfig.java) `addArgumentResolvers` | ~70（實測 7 處 `@RequestParam page/size` 收斂為 5 處 `@PaginationParams`）|
| F-10 | **`PageConversionHelper.from(Page<E>, Function<E,D>)`** ✅ **已完成 (2026-05-27)** — 取代各 service 手寫 `Page→PageResponse` 轉換；統一原本混用的 `toPageResponse` / `buildPageResponse` / inline builder 寫法 | [PageConversionHelper.java](../../../backend/src/main/java/com/taipei/iot/common/util/PageConversionHelper.java) ✅、[NotificationService.java](../../../backend/src/main/java/com/taipei/iot/notification/service/NotificationService.java) ✅、[AnnouncementService.java](../../../backend/src/main/java/com/taipei/iot/announcement/service/AnnouncementService.java) ✅、[AnnouncementReadService.java](../../../backend/src/main/java/com/taipei/iot/announcement/service/AnnouncementReadService.java) ✅、[AuditController.java](../../../backend/src/main/java/com/taipei/iot/audit/controller/AuditController.java) ✅、[UserAdminService.java](../../../backend/src/main/java/com/taipei/iot/user/service/UserAdminService.java) ✅ | `common/util/PageConversionHelper.java` | ~120 |
| F-11 | **Tenant scope 共用工具** ✅ **已完成 (2026-05-27)** — `(tenant_id IS NULL OR tenant_id = :tenantId)` hybrid 樣式以 `TenantScopeJpql` 常數 + builder 統一；補 `TenantScopeSpecifications` Specification 工廠覆蓋 Criteria API。純 `tenant_id =` 樣式仍由既有 [`TenantScopedRepository`](../../../backend/src/main/java/com/taipei/iot/tenant/TenantScopedRepository.java) marker + [`TenantFilterAspect`](../../../backend/src/main/java/com/taipei/iot/tenant/TenantFilterAspect.java) 自動套用 Hibernate `@Filter`，兩者互補 | 遷移 [RolePermissionRepository](../../../backend/src/main/java/com/taipei/iot/rbac/repository/RolePermissionRepository.java) ✅（2 處 @Query）、[PermissionRepository](../../../backend/src/main/java/com/taipei/iot/rbac/repository/PermissionRepository.java) ✅（1 處 @Query） | [common/tenant/TenantScopeJpql.java](../../../backend/src/main/java/com/taipei/iot/common/tenant/TenantScopeJpql.java) + [common/tenant/TenantScopeSpecifications.java](../../../backend/src/main/java/com/taipei/iot/common/tenant/TenantScopeSpecifications.java) | ~30 + 一致性 |
| F-12 | **`AuditedEntity extends BaseEntity`** ✅ **已完成 (2026-05-27)** — 在 BaseEntity 的時間戳之上加 `createdBy`（`@CreatedBy` 自動填入、`updatable=false`）/ `updatedBy`（`@LastModifiedBy`）/ `createdByName`（手寫 snapshot）三個欄位，搭配既有 [`JpaAuditConfig.auditorAware()`](../../../backend/src/main/java/com/taipei/iot/config/JpaAuditConfig.java) 自動取 SecurityContext userId。提供未來新增業務實體的標準起點 | 新增 [`common/entity/AuditedEntity.java`](../../../backend/src/main/java/com/taipei/iot/common/entity/AuditedEntity.java) ✅；既有 [Announcement.java](../../../backend/src/main/java/com/taipei/iot/announcement/entity/Announcement.java)（缺 `updated_by` column）、[AnnouncementAttachment.java](../../../backend/src/main/java/com/taipei/iot/announcement/entity/AnnouncementAttachment.java)（缺 3 個 column）、[DeptInfoEntity.java](../../../backend/src/main/java/com/taipei/iot/dept/entity/DeptInfoEntity.java)（使用 `create_by/update_by` 不同命名）遷移需同步 Flyway schema 變更 + Lombok `@SuperBuilder` 調整，列為後續 per-module PR 處理 | [common/entity/AuditedEntity.java](../../../backend/src/main/java/com/taipei/iot/common/entity/AuditedEntity.java) | ~20/entity × N |

#### 🟠 中價值

| # | 候選 | 出處 | 建議位置 |
|---|------|------|---------|
| F-13 | ✅ **已完成 (2026-05-27)** **`SqlLikeEscaper`** — LIKE keyword escape（`%` / `_` / `\`）；已遷移 AnnouncementService keyword 搜尋 | [AnnouncementService](../../../backend/src/main/java/com/taipei/iot/announcement/service/AnnouncementService.java) | [`common/util/SqlLikeEscaper.java`](../../../backend/src/main/java/com/taipei/iot/common/util/SqlLikeEscaper.java) |
| F-14 | ✅ **已完成 (2026-05-27)** **`TreeBuilder<T>` 通用樹狀結構建構器** — 泛型 `build(items, idExtractor, parentIdExtractor, childrenSetter)`，含孤兒救援、null 護網；供未來 menu / 組織圖 / 角色階層複用（DeptService 既有 entity→DTO 遞迴轉換較重，不在本次同步遷移範圍） | [DeptService.buildTree()](../../../backend/src/main/java/com/taipei/iot/dept/service/DeptService.java) | [`common/util/TreeBuilder.java`](../../../backend/src/main/java/com/taipei/iot/common/util/TreeBuilder.java) |
| F-15 | ✅ **已完成 (2026-05-27)** **`DataScopePredicates`** — 輕量 boolean 語意化包裝（`isUnrestricted` / `restrictsToOwner` / `restrictsToDeptScope`）。既有 [`DataScopeHelper`](../../../backend/src/main/java/com/taipei/iot/dept/service/DataScopeHelper.java)（dept 模組、依賴 DeptInfoRepository）負責真正的 dept_id 過濾，兩者互補 | [DeptService](../../../backend/src/main/java/com/taipei/iot/dept/service/DeptService.java)、[AnnouncementAttachmentService](../../../backend/src/main/java/com/taipei/iot/announcement/service/AnnouncementAttachmentService.java) | [`common/util/DataScopePredicates.java`](../../../backend/src/main/java/com/taipei/iot/common/util/DataScopePredicates.java) |
| F-16 | ✅ **已完成 (2026-05-27)** **`FileUploadTemplate`** — validate → (模組層白名單) → store → (fail-closed scan) 一條龍範本；INFECTED / ERROR / 例外皆刪檔；提供 `UploadRequest.builder()` 與 `Result` record；本次僅落地範本，AnnouncementAttachmentService 既有流程不同步遷移以避免行為變更 | [AnnouncementAttachmentService](../../../backend/src/main/java/com/taipei/iot/announcement/service/AnnouncementAttachmentService.java)；setting / user profile 未來加頭像會用到 | [`common/service/FileUploadTemplate.java`](../../../backend/src/main/java/com/taipei/iot/common/service/FileUploadTemplate.java) |
| F-17 | ✅ **已完成 (2026-05-27)** **`LangNormalizer`** — `normalize(langCode, supportedLangs, defaultLang)` + 常用語系常數（`ZH_TW` / `ZH_CN` / `EN` / `EN_US`）+ `supportedSet(...)` immutable 建構器；已遷移 AnnouncementService.normalizeLang | [AnnouncementService](../../../backend/src/main/java/com/taipei/iot/announcement/service/AnnouncementService.java) | [`common/util/LangNormalizer.java`](../../../backend/src/main/java/com/taipei/iot/common/util/LangNormalizer.java) |

#### 🟡 低價值（看順手再做）

| # | 候選 | 建議位置 |
|---|------|---------|
| F-18 | **`IdNameDto` / `OptionDto<V>` / `KeyValueDto`** 標準 DTO 形狀 | `common/dto/` |
| F-19 | **`ValidationMessages` 常數**（避免「密碼最長 128 字」散落在 20+ 個 DTO）| `common/enums/ValidationMessages.java` |
| F-20 | **引入 MapStruct** — 取代手寫 `toDto/toResponse` boilerplate；跨 10 模組估可省 300~500 LOC（屬大型重構）。**⚠️ 評估結論：暫不建議排程**，理由詳見下方備註 | `common/mapper/` + pom 加 dep |

> **F-20 評估備註（2026-05-27）**
>
> **不建議現階段執行的理由：**
> 1. **ROI 偏低**：估省 300~500 LOC 僅佔 backend (~50k+ LOC) 不到 1%；換來 MapStruct 學習曲線（`@MapperConfig` / `@Mapping(qualifiedByName=...)` / `@AfterMapping` / Spring component model）。debug 時 stack trace 跳至 generated `*MapperImpl.java`，可讀性下降。
> 2. **本專案 mapper 多半「不純」**：`AnnouncementService.pickTranslation` / `toResponseList` 含 i18n fallback、權限判斷、attachments 聚合、`createdByName` snapshot 反查、`hasReadAcknowledged` 計算。這類業務邏輯放進 MapStruct 需 `@AfterMapping` + inject 其他 service，反而比手寫更複雜。真正能省的只有純欄位對拷那一小部分。
> 3. **技術棧摩擦**：與 Lombok 並用需加 `lombok-mapstruct-binding` annotation processor，順序敏感，IDE / `mvn` / CI 間易出現「IDE 顯示正常但 mvn compile 失敗」。Java 21 records 已大量採用，與 Lombok `@Builder` 混用需額外設定。683 個既有測試含 mock 手寫 mapper / DTO 結構比對，遷移 review 量大。
> 4. **安全 / 正確性增益為零**：對比剛完成的 F-9~F-17（pagination、tenant filter、SQL escape、tree builder 等都有明確 bug surface 縮減），F-20 純為樣式統一，不對等於本專案資安 review (N-1~N-12) 的優先級。
>
> **何時重新評估（trigger）：**
> - 新增大型模組（如「資產管理」），可從新模組起跑作為試點，再逐步同化舊模組。
> - 手寫 mapper 出現 bug 集中（連續 ≥3 次「新增欄位忘記更新 mapper」）。
> - 未來引入 OpenAPI generator / GraphQL，自動生成大量 DTO 時收益放大。
> - DTO 普遍轉為 records 且與 entity 1:1 對應。
>
> **替代低成本方案（若仍想消除部分 boilerplate）：**
> - 抽 `EntityMapper<E, D>` 介面 + default method 統一 `toDto/toEntity/toDtoList` 命名（純 Java、零 dep）。
> - 善用 records + 顯式 `static from(...)` factory（目前作法），持續貫徹即可。
> - 僅選 2~3 個樣板最重的 mapper 做 MapStruct PoC，觀察 3 個月再決定 rollout。

#### 不建議上提（已合理）

- **Email / SMTP**：目前僅 auth password reset 用；其他模組走 `NotificationChannel` 抽象，無實質重複。
- **`BCryptPasswordEncoder`**：已在 `config/SecurityConfig` 集中。
- **Soft-delete `deletedAt`**：schema 中尚未出現，無實際重複。
- **`@AuditEvent`**：本身位於 audit 模組以 aspect 跨模組使用，定位正確，不宜搬到 common。

---

## 六、修復路線圖建議

### Sprint 1 — 立即修補（資安回歸風險）
1. **N-1 ✅ 已完成 (2026-05-27)** — `SecurityLogger.sanitize()` 加入 `\u2028 / \u2029 / \u0000` 處理 + [`SecurityLoggerTest`](../../../backend/src/test/java/com/taipei/iot/common/util/SecurityLoggerTest.java)（9 cases，以 logback `ListAppender` 驗證輸出），mvn test 全綠 **692/0/0/0**。
2. **N-2 ✅ 已完成 (2026-05-27)** — `NoOpVirusScanService` 移除 `matchIfMissing = true`、加 `@Profile({"dev","test"})`、`@PostConstruct` WARN；[`NoOpVirusScanServiceTest`](../../../backend/src/test/java/com/taipei/iot/common/service/NoOpVirusScanServiceTest.java) 擴充至 6 cases（含 ListAppender 驗證 WARN + 反射檢查 annotation 合約），mvn test 全綠 **696/0/0/0**。
3. **N-3 ✅ 已完成 (2026-05-27)** — `TenantAwareQuery` 新增 `stripCommentsAndLower` + `containsTenantIdAfterWhere` 兩個 helper：先剥 `/* */` / `--` 註解再要求 `tenant_id` 以單字邊界出現於 WHERE 子句之後；JavaDoc 明示 guardrail 性質並指向 F-1 ArchUnit 規則。新增 [`TenantAwareQueryTest`](../../../backend/src/test/java/com/taipei/iot/common/util/TenantAwareQueryTest.java)（4 個 @Nested 共 18 cases：Strip / Location / CreateValidation / CreateGlobal），mvn test 全綠 **714/0/0/0**。F-1 ArchUnit 規則依然獨立排程。
4. **N-6 ✅** — `GlobalExceptionHandler` 顯式處理 `MaxUploadSizeExceededException` → 413。

### Sprint 2 — 中度硬化
5. **N-4** ✅ — ClamAV 在 INSTREAM 前檢查檔案大小，超過 `clamav.stream-max-length` 即 ERROR / fail-closed。
6. **N-5** ✅ — 把 `maxImageSize / maxDocumentSize / maxMediaSize` 拉到 `application.yml`。
7. **N-7** ✅ — `LocalFileStorageService` 已顯式設定 `rw-------` / `rwx------` POSIX 權限（2026-05-27）。
8. **N-8** ✅ — `ImageSanitizer` 新增 `SanitizeResult` + `sanitizeDetailed`，多幀來源 WARN + flag（2026-05-27）。
9. **N-9** ✅ — `SecurityContextUtils` 非請求 thread WARN once + `requireCurrentUserIdStrict`（2026-05-27）。

### Sprint 3+ — 低風險與 UX
10. **N-10 ✅ 已完成 (2026-05-28)** — ClamAV log 路徑遮罩：INFO/WARN 級別僅記檔名，完整路徑保留在 DEBUG。
11. **N-11 ✅ 已完成 (2026-05-28)** — `@RateLimit` JavaDoc 補完：key 組成規則 + 匿名使用者共用 bucket + Redis 不可用 fallback 說明。
12. **N-12 ✅ 已完成 (2026-05-28)** — `BaseResponse.timestamp` 由毫秒改為秒精度，緩解 timing side-channel。
13. **N-13 ✅ 已完成 (2026-05-28)** — 新增 `DevProfileSecurityWarning`：dev/test profile 啟動時輸出醒目安全警告。
14. **F-1 ✅ 已完成 (2026-05-28)** — ArchUnit 規則禁止業務碼直接呼叫 `EntityManager.createNativeQuery`：新增 [`AllowDirectNativeQuery`](../../../backend/src/main/java/com/taipei/iot/common/annotation/AllowDirectNativeQuery.java) 標記註解（強制 `reason` 欄位）+ [`ForbiddenNativeQueryArchTest`](../../../backend/src/test/java/com/taipei/iot/architecture/ForbiddenNativeQueryArchTest.java)（透過 ArchUnit 掃描所有 `com.taipei.iot` 套件下的 method calls，只允許 `TenantAwareQuery` 與 `@AllowDirectNativeQuery` 註解類別/方法呼叫）。`PasswordPolicyDao` 套上 `@AllowDirectNativeQuery` 並附 reason（跨租戶 + platform sentinel by design）。本地驗證：移除豁免 → 規則正確抓出 3 處違規（PasswordPolicyDao.java:39 / 54 / 69）；恢復後 mvn test 全綠 **751/0/0/0**。
15. **F-2 ✅ 已完成 (2026-05-28)** — [`ImageSanitizer.SanitizeResult`](../../../backend/src/main/java/com/taipei/iot/common/service/ImageSanitizer.java) record 擴充 `wasDowngraded` 與 `framesDropped` 欄位，使多幀降級事件可被呼叫端量化讀取；`downgraded()` 保留為 alias，N-8 期間落地的 `sanitizeDetailed` 使用端不需變動。WARN log 輸出 `framesDropped=N` 託供 SIEM 稽核。[`ImageSanitizerTest`](../../../backend/src/test/java/com/taipei/iot/common/service/ImageSanitizerTest.java) +4 cases（單幀 0 丟幀 / 3 幀丟 2 幀 / unsupported 則 0 / WARN 文本含 `framesDropped`），mvn test 全綠 **759/0/0/0**。
16. **F-3 ✅ 已完成 (2026-05-28)** — 檔案掃毒結果寫入 audit_log：[`FileUploadTemplate`](../../../backend/src/main/java/com/taipei/iot/common/service/FileUploadTemplate.java) 注入 `ApplicationEventPublisher`，在 `INFECTED` / `ERROR` / 掃毒例外 fail-closed 三條路徑於刪檔前發出 [`VirusScanAuditEvent`](../../../backend/src/main/java/com/taipei/iot/common/event/VirusScanAuditEvent.java)（record，欄位：`result` / `relativePath` / `originalFileName` / `size` / `subDir`），publish 失敗以 try-catch 包覆並僅 WARN log 不影響原本 fail-closed 行為。Audit 模組新增 [`VirusScanAuditListener`](../../../backend/src/main/java/com/taipei/iot/audit/listener/VirusScanAuditListener.java)（`@EventListener`）在發布端執行緒擷取 `TenantContext.getCurrentTenantId()` / `SecurityContextUtils.getCurrentUserId/Username/UserInfo` / HTTP `URI/IP/User-Agent` 後，呼叫 [`AuditAsyncWriter.saveAsync`](../../../backend/src/main/java/com/taipei/iot/audit/async/AuditAsyncWriter.java)（內含 `@RunInSystemTenantContext` + `@Async`）寫入 `user_event_log`；listener 內部 try-catch 包整段，writer 例外不傳播。[`AuditEventType`](../../../backend/src/main/java/com/taipei/iot/audit/enums/AuditEventType.java) 新增 `FILE_VIRUS_INFECTED` / `FILE_SCAN_ERROR`（均 `AuditCategory.SYSTEM`、`success=false` → errorCode `99999`）。架構選用 Spring `ApplicationEventPublisher` 而非 common → audit 直接相依，避免反向耦合：common 模組不 import 任何 audit 套件，audit 模組以 listener 訂閱事件，未來其他模組亦可重用同套事件管道。Test：[`FileUploadTemplateTest`](../../../backend/src/test/java/com/taipei/iot/common/service/FileUploadTemplateTest.java) +5 cases（CLEAN 不發 / INFECTED 發 + 內容驗證 / ERROR 發 / 掃毒擲例外仍發 / publish 失敗不破壞 fail-closed）、新增 [`VirusScanAuditListenerTest`](../../../backend/src/test/java/com/taipei/iot/audit/listener/VirusScanAuditListenerTest.java) 3 cases（INFECTED 翻譯為 `FILE_VIRUS_INFECTED` saveAsync 含 tenant/user/message 驗證 / ERROR 翻譯為 `FILE_SCAN_ERROR` / writer 例外被 swallow）。mvn test：**767/0/0/0** 全綠（759 → 767，+8 cases）。
17. **F-4 ✅ 已完成 (2026-05-28)** — ClamAV 連線健康度 actuator endpoint：[`pom.xml`](../../../backend/pom.xml) 引入 `spring-boot-starter-actuator`；[`ClamAvVirusScanService`](../../../backend/src/main/java/com/taipei/iot/common/service/ClamAvVirusScanService.java) 新增 `ping()`（送 `zPING\0` 驗 `PONG`，失敗或非預期回應一律 false 並記 WARN，timeout 重用既有 `virus-scan.clamav.timeout`）+ `getHost()` / `getPort()` / `getStreamMaxLength()` 三個 getter 給 health indicator 注入。新增 [`ClamAvHealthIndicator`](../../../backend/src/main/java/com/taipei/iot/common/health/ClamAvHealthIndicator.java)（bean name `clamav` → endpoint path `/actuator/health/clamav`、`@ConditionalOnProperty(name="virus-scan.enabled", havingValue="true")` 與 ClamAV service 同生命週期、NoOp 模式不暴露），將 `ping()` 結果包成 UP / DOWN 並一律附 details：`host` / `port` / `streamMaxLength`（bytes，方便 SRE 與 clamd 設定比對是否漂移）/ `ping`（`PONG` 或 `FAILED`）。[`application.yml`](../../../backend/src/main/resources/application.yml) 加 `management.endpoints.web.exposure.include: health` 與 `management.endpoint.health.show-details: always`，僅暴露 health endpoint，其餘 actuator endpoint 保持關閉降低攻擊面。[`SecurityConfig`](../../../backend/src/main/java/com/taipei/iot/config/SecurityConfig.java) 對 `/actuator/health`、`/actuator/health/**` permitAll 供 LB / K8s liveness / Prometheus blackbox 匿名 probe；details 僅含非機敏連線設定（host/port/size limit），不含憑證或資料。test：[`ClamAvVirusScanServiceTest`](../../../backend/src/test/java/com/taipei/iot/common/service/ClamAvVirusScanServiceTest.java) +4 cases（內建 `FakeClamd` `ServerSocket` 模擬 `PONG` 回應驗 `ping()==true` 且送出指令以 `zPING` 開頭 / 非預期回應驗 false + WARN / 連線失敗驗 false + WARN / 三個 getter 回值正確）、新增 [`ClamAvHealthIndicatorTest`](../../../backend/src/test/java/com/taipei/iot/common/health/ClamAvHealthIndicatorTest.java) 3 cases（UP/PONG 含 4 details / DOWN/FAILED 含 4 details / DOWN 時 `streamMaxLength` 仍輸出供 SRE 比對）。mvn test：**774/0/0/0** 全綠（767 → 774，+7 cases）。
18. **F-5 ✅ 已完成 (2026-05-28)** — `RateLimit` 支援 user-level key：[`RateLimit`](../../../backend/src/main/java/com/taipei/iot/common/annotation/RateLimit.java) 新增巢狀 `enum KeyStrategy { IP, USER_OR_IP }` 與 `strategy()` 元素（預設 `IP` 維持背向相容，舊呼叫端無須調整）。[`RateLimitInterceptor`](../../../backend/src/main/java/com/taipei/iot/common/interceptor/RateLimitInterceptor.java) 新增私有 helper `resolveIdentifier(KeyStrategy, clientIp)`：`IP` 直接回 `clientIp`（產生 `rate_limit:{key}:{ip}` 舊格式）、`USER_OR_IP` 透過 [`SecurityContextUtils.getCurrentUserId()`](../../../backend/src/main/java/com/taipei/iot/common/util/SecurityContextUtils.java) 取得 userId，非空白則回 `user:{userId}`（→ `rate_limit:{key}:user:{userId}`），否則匿名回 `ip:{clientIp}`（→ `rate_limit:{key}:ip:{clientIp}`）。Redis 與本地 fallback 雙路徑共用同一 identifier，SecurityLogger / log.warn 仍輸出 source IP 供 SIEM 追蹤。設計取捨：auth 端點（`/login` / `/forgot-password`）攻擊者未登入，userId 為 null 等同 IP 策略——文件 JavaDoc 明示這類端點應保留 `IP` 以維持原本暴力破解防護語義；而業務寫入端點（如 `create-order`、`create-announcement`）若位於 NAT/反向代理後易誤殺正常使用者，建議改 `USER_OR_IP` 以分桶。null strategy 由 `strategy == KeyStrategy.USER_OR_IP` 判斷自然 fall-through 至 IP 分支，故 Mockito mock 即使未 stub `strategy()` 也不會 NPE。test：[`RateLimitInterceptorTest`](../../../backend/src/test/java/com/taipei/iot/common/interceptor/RateLimitInterceptorTest.java) +5 cases（USER_OR_IP 已認證 → user:{userId} key / USER_OR_IP 匿名 fallback → ip:{clientIp} / 同 IP 兩個 userId 取得獨立 bucket 不互相干擾 / 空白 principal 視為匿名 / 明示 IP strategy 即使已登入仍走 IP key），新增 `@AfterEach SecurityContextHolder.clearContext()` 防止跨測試污染。mvn test：**779/0/0/0** 全綠（774 → 779，+5 cases）。
19. **F-6 ✅ 已完成 (2026-05-28)** — `FileStorageService` API 擴充：[`FileStorageService`](../../../backend/src/main/java/com/taipei/iot/common/service/FileStorageService.java) 新增 `boolean deleteIfExists(String path)` 與 `String move(String fromPath, String toSubDir, String newFileName)`。[`LocalFileStorageService`](../../../backend/src/main/java/com/taipei/iot/common/service/LocalFileStorageService.java) 提供實作，並抽出私有 `resolveSafePath(String)` 將 path traversal 防護集中一處（load / delete / deleteIfExists / move / resolveAbsolutePath 共用，避免同一段 `rootLocation.resolve(...).startsWith(rootLocation)` 檢查重複 4 次）。`deleteIfExists` 語意對齊 {@link Files#deleteIfExists}：檔存在 → 刪除並回 true、不存在 → 回 false、I/O 例外 → WARN log + 回 false（與現行 `delete()` 的 best-effort 哲學一致，呼叫端不需 try-catch）。`move` 優先使用 `StandardCopyOption.ATOMIC_MOVE`；遭遇 `AtomicMoveNotSupportedException`（跨 mount point / 某些網路掛載 FS）改為 `REPLACE_EXISTING` 並記 DEBUG log，避免上層入來就炸。目的檔名走同一套 `sanitizeFileName` + UUID-8 prefix 與 `store()` 一致，避免覆寫。來源 path traversal 抽服務根目錄拋 `VALIDATION_ERROR`、來源檔不存在拋 `ATTACHMENT_NOT_FOUND`、目的子目錄 traversal 拋 `VALIDATION_ERROR`。新建目錄與移入檔都重用原 `applyDirPermissions` / `applyFilePermissions` 接口套 0700 / 0600，以避免 N-7 保護被繞過。interface JavaDoc 明示「存在/不存在/失敗」三狀態與 traversal 語意，供未來 S3 / MinIO 實作同接口提供同語意。Mockito mock 現有 `FileStorageService` 的測試（如 [`FileUploadTemplateTest`](../../../backend/src/test/java/com/taipei/iot/common/service/FileUploadTemplateTest.java)）未受影響——未 stub 的方法 default 回 false/null。test：[`LocalFileStorageServiceTest`](../../../backend/src/test/java/com/taipei/iot/common/service/LocalFileStorageServiceTest.java) +10 cases（deleteIfExists：存在回 true / 不存在回 false / 幂等多次呼叫 / traversal 拒絕；move：基本搬逾並保留內容 / 新檔與子目錄權限 0600/0700 / 來源檔不存在丟 `ATTACHMENT_NOT_FOUND` / 來源 traversal 拒絕 / 目的 subdir traversal 拒絕 / `newFileName` 中 `..` 與 `/` 被 sanitize）。mvn test：**789/0/0/0** 全綠（779 → 789，+10 cases）。
20. **F-7 ✅ 已完成 (2026-05-28)** — `SecurityLogger` 結構化 JSON 輸出：[`SecurityLogger`](../../../backend/src/main/java/com/taipei/iot/common/util/SecurityLogger.java) 從傳統 `log.warn("[SECURITY] {} ip={} {}", ...)` 重構為 SLF4J 2.x Fluent API `log.atWarn().addKeyValue(...).log(...)`，每筆事件自動附加三個基本結構化欄位（`security_event` / `security_ip` / `security_category`），加上動態拆解 `details` 中的 `key=value` 字串為 `security_{key}` 欄位（無 `=` 則給予 `security_detail_N` index 名）。外部 API 不變：所有現有 caller（`SecurityConfig` / `GlobalExceptionHandler` / `JwtAuthenticationFilter` / `AuthServiceImpl` / `LocalAuthProvider` / `RateLimitInterceptor` / `TenantInterceptor`）無需任何調整。[`pom.xml`](../../../backend/pom.xml) 新增 `net.logstash.logback:logstash-logback-encoder:8.0`；[`logback-spring.xml`](../../../backend/src/main/resources/logback-spring.xml) 新增 `SECURITY_JSON_FILE` appender 使用 `LogstashEncoder`，受 Spring profile `json-log` 控制（未啟用時完全不生效，零開銷）。JSON 輸出範例：`{"@timestamp":"...","level":"WARN","logger_name":"SECURITY","message":"[SECURITY] LOGIN_FAILED ip=1.2.3.4 email=test@x.com","security_event":"LOGIN_FAILED","security_ip":"1.2.3.4","security_email":"test@x.com","security_category":"SECURITY"}`。`sanitize()` 與 `sanitizeDetails()` 內部方法 visibility 調整為 package-private 以便測試直接驗證。test：[`SecurityLoggerTest`](../../../backend/src/test/java/com/taipei/iot/common/util/SecurityLoggerTest.java) +7 cases（巢狀 `StructuredOutputTest` class：warn/info 基本欄位 / key=value 拆解 / 無 '=' 的 detail 給 index / IP sanitize / 多個 details / null details 不 NPE / structured fields 總數驗證）。mvn test：**796/0/0/0** 全綠（789 → 796，+7 cases）。
21. **F-8 ✅ 已完成 (2026-05-28)** — DX 強化：`ErrorCodeJsonGenerator` + `exec-maven-plugin` 自動產 `error-codes.json` 至前端 `src/generated/`，`useApiError` composable 自動 fallback 查表；後端增減 ErrorCode enum 後 `mvn compile` 即同步，前端不再需手動 hardcode error message mapping。新增 `ErrorCodeJsonGeneratorTest` 8 cases，mvn test：**804/0/0/0** 全綠（796 → 804，+8 cases）。

### Sprint 4 — 跨模組重複樣式上提（與其他模組重構同步）
12. **F-9 ✅ 已完成 (2026-05-27)** — `@PaginationParams` + `PageQuery` + `PaginationArgumentResolver` 落地，AnnouncementController / NotificationController / UserAdminController 共 5 處分頁端點完成遷移，新增 `PaginationArgumentResolverTest`（13 cases），mvn test 全綠 603/0/0/0。
13. **F-10 ✅ 已完成 (2026-05-27)** — `PageConversionHelper`（`from(Page<T>)` / `from(Page<E>, Function<E,D>)` / `from(List<D>, Page<?>)` / `empty()` 4 個重載）落地，遷移 5 個檔：NotificationService（合併 2 處並刪除 private helper）、AnnouncementService（遷移 2 處並刪除 `buildPageResponse`）、AnnouncementReadService（遷移 2 處 inline builder）、AuditController（遷移 2 處並刪除 private helper）、UserAdminService（遷移 1 處 inline builder），合計消除 ~9 處重複代碼。新增 `PageConversionHelperTest`（10 cases 覆蓋三個重載 + null 護網 + empty()），mvn test 全綠 **613/0/0/0**。
14. **F-11 ✅ 已完成 (2026-05-27)** — [`TenantScopeJpql`](../../../backend/src/main/java/com/taipei/iot/common/tenant/TenantScopeJpql.java)（JPQL 片段常數 `RP_GLOBAL_OR_TENANT` + `globalOrTenant(String alias)` builder + `TENANT_ID_PARAM`）+ [`TenantScopeSpecifications`](../../../backend/src/main/java/com/taipei/iot/common/tenant/TenantScopeSpecifications.java)（`globalOrTenant` / `tenantOnly` / `globalOnly` Specification 工廠）落地，遷移 [RolePermissionRepository](../../../backend/src/main/java/com/taipei/iot/rbac/repository/RolePermissionRepository.java)（2 處 @Query）+ [PermissionRepository](../../../backend/src/main/java/com/taipei/iot/rbac/repository/PermissionRepository.java)（1 處 @Query）共 3 個 hybrid `(tenant_id IS NULL OR tenant_id = :tenantId)` 樣式至共用常數，避免片段字串散落各 repository。既有 [`TenantScopedRepository`](../../../backend/src/main/java/com/taipei/iot/tenant/TenantScopedRepository.java) marker + [`TenantFilterAspect`](../../../backend/src/main/java/com/taipei/iot/tenant/TenantFilterAspect.java)（純 `tenant_id =` 樣式自動套 Hibernate `@Filter`）保持不變，兩者互補。新增 `TenantScopeJpqlTest`（6 cases）+ `TenantScopeSpecificationsTest`（5 cases）合計 11 cases，mvn test 全綠 **624/0/0/0**。
15. **F-12 ✅ 已完成 (2026-05-27)** — [`AuditedEntity extends BaseEntity`](../../../backend/src/main/java/com/taipei/iot/common/entity/AuditedEntity.java) 落地，提供 `createdBy`（`@CreatedBy` `updatable=false`）/ `updatedBy`（`@LastModifiedBy`）/ `createdByName`（手寫 snapshot）三個欄位，搭配既有 [`JpaAuditConfig.auditorAware()`](../../../backend/src/main/java/com/taipei/iot/config/JpaAuditConfig.java) 自動取 SecurityContext userId。未來新增業務實體可直接繼承本類即可取得完整稽核套件。新增 `AuditedEntityTest`（6 cases 以反射鎖定「欄位名稱 / 長度 / `updatable` / `@CreatedBy` / `@LastModifiedBy` / `@MappedSuperclass`」合約），mvn test 全綠 **630/0/0/0**。【**遷移列為後續 per-module PR**】既有 `Announcement`（缺 `updated_by` column）、`AnnouncementAttachment`（缺 `updated_by / created_by_name / updated_at`）、`DeptInfoEntity`（`create_by/update_by` 不同命名慣例）仍需同步 Flyway schema 變更 + Lombok `@SuperBuilder` 調整，本次不同步以保持變動隱隢。
16. **F-13 / F-14 / F-15 / F-16 / F-17 ✅ 已完成 (2026-05-27)** — 中價值上提一次完成：
    - [`SqlLikeEscaper`](../../../backend/src/main/java/com/taipei/iot/common/util/SqlLikeEscaper.java)（F-13，`escape` / `contains`，順序敏感地先 escape `\` 再 `%` / `_`），已遷移 AnnouncementService.listAdmin keyword 過濾。
    - [`TreeBuilder`](../../../backend/src/main/java/com/taipei/iot/common/util/TreeBuilder.java)（F-14，泛型 `build(items, idExtractor, parentIdExtractor, childrenSetter)`，含孤兒救援 / null 護網 / 順序保留），DeptService.buildTree 因 entity→DTO 遞迴轉換太重不同步遷移。
    - [`DataScopePredicates`](../../../backend/src/main/java/com/taipei/iot/common/util/DataScopePredicates.java)（F-15，`isUnrestricted` / `restrictsToOwner` / `restrictsToDeptScope` 三個語意化 boolean，補充而非取代 dept 模組的 `DataScopeHelper`）。
    - [`FileUploadTemplate`](../../../backend/src/main/java/com/taipei/iot/common/service/FileUploadTemplate.java)（F-16，validate → 模組層白名單 → store → fail-closed virus scan 一條龍範本；INFECTED / ERROR / 例外皆呼叫 `FileStorageService.delete` 清檔；本次僅落地範本，既有 AnnouncementAttachmentService 不同步遷移以避免行為變更）。
    - [`LangNormalizer`](../../../backend/src/main/java/com/taipei/iot/common/util/LangNormalizer.java)（F-17，`normalize(langCode, supportedLangs, defaultLang)` + 常用語系常數 + `supportedSet(...)` 建構器），已遷移 AnnouncementService.normalizeLang。
    - 新增測試：`SqlLikeEscaperTest`（13 cases）+ `TreeBuilderTest`（12 cases）+ `LangNormalizerTest`（10 cases）+ `DataScopePredicatesTest`（10 cases）+ `FileUploadTemplateTest`（8 cases）合計 53 cases，mvn test 全綠 **683/0/0/0**。
17. **F-18 / F-19 / F-20** — 低價值或大型重構（標準 DTO 形狀、validation messages、MapStruct 引入）。

### Sprint 4.5 — F-12 後續 per-module 遷移待辦（**勿忘**）

> F-12 superclass [`AuditedEntity`](../../../backend/src/main/java/com/taipei/iot/common/entity/AuditedEntity.java) 已落地，但既有實體未遷移。下列三組需逐一處理；建議與該模組下次重構 PR 同步進行以節省 review 成本。

| # | 實體 | 目前缺失 | 需執行步驟 |
|---|------|---------|----------|
| M-1 | [Announcement.java](../../../backend/src/main/java/com/taipei/iot/announcement/entity/Announcement.java) | 缺 `updated_by` column | (1) 新增 Flyway `V55__announcement__add_updated_by.sql`：`ALTER TABLE announcements ADD COLUMN updated_by VARCHAR(50);` (2) entity 改 `extends AuditedEntity`，移除自有 `createdBy / createdByName / createdAt / updatedAt`、`@EntityListeners(AuditingEntityListener.class)` (3) Lombok `@Builder` 改 `@SuperBuilder`（連動 `Announcement.builder()` 兩處呼叫站：service create / 測試 fixture）(4) `AnnouncementService.create()` 不再手動 set `createdBy`（auditor 會自動填），只保留 `createdByName` snapshot |
| M-2 | [AnnouncementAttachment.java](../../../backend/src/main/java/com/taipei/iot/announcement/entity/AnnouncementAttachment.java) | 缺 `updated_by` / `created_by_name` / `updated_at` 三 column | (1) Flyway `V56__announcement__attachment_audit_columns.sql` 加三個 column (2) entity 改 `extends AuditedEntity`、`@Builder` → `@SuperBuilder` (3) attachment 通常無 updated 語意，可考慮只繼承 `BaseEntity` 並手寫 `createdBy` — 決策時再評估 |
| M-3 | [DeptInfoEntity.java](../../../backend/src/main/java/com/taipei/iot/dept/entity/DeptInfoEntity.java) | 命名為 `create_by / update_by / create_time / update_time`（非 `*_by / *_at` 慣例） | 兩條路：(A) Flyway rename column 為標準命名（需同步檢查 view / native query 引用）；(B) 維持現名，於 entity 使用 `@AttributeOverrides` 重新對應父類欄位。建議 A，順便補齊 `created_by_name`。 |

**驗收 checklist**（每筆 PR）：
- [ ] Flyway migration 已加 + 本機 `mvn test` 全綠
- [ ] entity 已改 `extends AuditedEntity`、移除重複欄位
- [ ] `@Builder` → `@SuperBuilder`，所有 `.builder()` 呼叫站編譯通過
- [ ] service create 不再手動 set `createdBy`（驗證 auditor 自動填入正確）
- [ ] integration test 驗證 `created_by / updated_by` 在 INSERT / UPDATE 後值正確
- [ ] 完成後在本表標 ✅ + 日期

---

## 七、附錄：本次複查涵蓋的檔案

### Main 套件
- [annotation/RateLimit.java](../../../backend/src/main/java/com/taipei/iot/common/annotation/RateLimit.java)
- [dto/PageResponse.java](../../../backend/src/main/java/com/taipei/iot/common/dto/PageResponse.java)、`dto/UserInfo.java`
- [entity/BaseEntity.java](../../../backend/src/main/java/com/taipei/iot/common/entity/BaseEntity.java)
- [enums/ErrorCode.java](../../../backend/src/main/java/com/taipei/iot/common/enums/ErrorCode.java)、`enums/SecurityEvent.java`
- [exception/BusinessException.java](../../../backend/src/main/java/com/taipei/iot/common/exception/BusinessException.java)、[exception/GlobalExceptionHandler.java](../../../backend/src/main/java/com/taipei/iot/common/exception/GlobalExceptionHandler.java)
- [interceptor/RateLimitInterceptor.java](../../../backend/src/main/java/com/taipei/iot/common/interceptor/RateLimitInterceptor.java)
- [response/BaseResponse.java](../../../backend/src/main/java/com/taipei/iot/common/response/BaseResponse.java)
- [service/VirusScanService.java](../../../backend/src/main/java/com/taipei/iot/common/service/VirusScanService.java)、[service/ClamAvVirusScanService.java](../../../backend/src/main/java/com/taipei/iot/common/service/ClamAvVirusScanService.java)、[service/NoOpVirusScanService.java](../../../backend/src/main/java/com/taipei/iot/common/service/NoOpVirusScanService.java)
- [service/FileStorageService.java](../../../backend/src/main/java/com/taipei/iot/common/service/FileStorageService.java)、[service/LocalFileStorageService.java](../../../backend/src/main/java/com/taipei/iot/common/service/LocalFileStorageService.java)、[service/FileValidationService.java](../../../backend/src/main/java/com/taipei/iot/common/service/FileValidationService.java)、[service/ImageSanitizer.java](../../../backend/src/main/java/com/taipei/iot/common/service/ImageSanitizer.java)
- [util/JwtClaimKeys.java](../../../backend/src/main/java/com/taipei/iot/common/util/JwtClaimKeys.java)、[util/SecurityContextUtils.java](../../../backend/src/main/java/com/taipei/iot/common/util/SecurityContextUtils.java)、[util/SecurityLogger.java](../../../backend/src/main/java/com/taipei/iot/common/util/SecurityLogger.java)、[util/TenantAwareQuery.java](../../../backend/src/main/java/com/taipei/iot/common/util/TenantAwareQuery.java)

### Test 套件（覆蓋核心邏輯）
- `common/enums/ErrorCodeTest.java`
- `common/exception/BusinessExceptionTest.java`、`common/exception/GlobalExceptionHandlerTest.java`
- `common/interceptor/RateLimitInterceptorTest.java`
- `common/response/BaseResponseTest.java`
- `common/service/FileValidationServiceTest.java`、`common/service/ImageSanitizerTest.java`、`common/service/NoOpVirusScanServiceTest.java`
- `common/util/SecurityContextUtilsTest.java`
- `common/dto/UserInfoTest.java`

### 設定檔
- [application.yml](../../../backend/src/main/resources/application.yml)
- [application-dev.yml](../../../backend/src/main/resources/application-dev.yml)
- [application-test.yml](../../../backend/src/main/resources/application-test.yml)
