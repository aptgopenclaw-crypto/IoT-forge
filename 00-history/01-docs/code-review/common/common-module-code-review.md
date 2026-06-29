# Common 模組 Code Review & Security Review

> 審查日期：2026-05-20
> 審查範圍：`backend/src/main/java/com/taipei/iot/common/` 全部子套件

---

## 模組結構總覽

```
common/
├── annotation/
│   └── RateLimit.java                  # 速率限制註解（Redis INCR + EXPIRE）
├── dto/
│   ├── PageResponse.java               # 通用分頁回應 DTO
│   └── UserInfo.java                   # SecurityContext 使用者資訊 DTO
├── entity/
│   └── BaseEntity.java                 # 共用基礎實體（createdAt/updatedAt）
├── enums/
│   ├── ErrorCode.java                  # 統一錯誤碼（含 HTTP status 對應）
│   └── SecurityEvent.java              # 安全事件列舉（Security Logger 用）
├── exception/
│   ├── BusinessException.java          # 業務例外（攜帶 ErrorCode + detail）
│   └── GlobalExceptionHandler.java     # 全域例外處理器（@RestControllerAdvice）
├── interceptor/
│   └── RateLimitInterceptor.java       # Redis-based 速率限制攔截器
├── response/
│   └── BaseResponse.java              # 統一 API 回應格式
├── service/
│   ├── VirusScanService.java           # 病毒掃描介面
│   ├── ClamAvVirusScanService.java     # ClamAV 實作（Socket 通訊）
│   ├── NoOpVirusScanService.java       # 開發環境 NoOp 實作
│   ├── FileStorageService.java         # 檔案儲存介面
│   ├── LocalFileStorageService.java    # 本地檔案儲存實作（Path Traversal 防護）
│   ├── FileValidationService.java      # 檔案驗證（副檔名 + Magic Bytes + 大小）
│   └── ImageSanitizer.java             # 圖片消毒（重新渲染去除惡意 payload）
└── util/
    ├── JwtClaimKeys.java               # JWT claim 名稱常數
    ├── SecurityContextUtils.java       # 從 SecurityContext 取得使用者資訊
    ├── SecurityLogger.java             # 安全事件專用 Logger（含 log injection 防護）
    └── TenantAwareQuery.java           # Native query 租戶隔離保護工具
```

**測試覆蓋**：10 個測試檔案，涵蓋所有核心類別。

---

## 總體評價

Common 模組是整個系統的**安全基礎設施層**，設計品質**優異**：

- **Defense in Depth**：檔案上傳防護四層（副檔名白名單 → Magic Bytes 比對 → 病毒掃描 → 圖片消毒）
- **Path Traversal 防護**：`LocalFileStorageService` 所有路徑操作均有 `normalize()` + `startsWith()` 檢查
- **Log Injection 防護**：`SecurityLogger.sanitize()` 清除 `\r\n\t`，防止日誌偽造
- **租戶隔離安全網**：`TenantAwareQuery` 強制 native query 必須含 `tenant_id` 條件，否則拋例外
- **SQL Injection 偵測**：`GlobalExceptionHandler` 在 catch-all 中檢測疑似注入模式
- **速率限制**：Redis-based，Redis 不可用時 fail-open（保可用性），觸發時記錄安全事件
- **資訊洩漏防護**：`BaseResponse.toString()` 排除 body、5xx 不回傳 detail
- **ClamAV 整合**：可選配置，開發環境用 NoOp 替代

---

## 安全審查（Security Review）

### ✅ 防護到位的項目

| 防護類別 | 實作方式 | 評估 |
|----------|----------|------|
| Path Traversal | `normalize()` + `startsWith(rootLocation)` 四處檢查 | ✅ |
| 檔名消毒 | `sanitizeFileName()` 移除 `/\\\x00` 和 `..` | ✅ |
| 檔案類型偽造 | Apache Tika magic bytes 比對 + 副檔名白名單 | ✅ |
| 圖片惡意 payload | ImageSanitizer 重新渲染（去除 EXIF/嵌入腳本） | ✅ |
| 病毒掃描 | ClamAV INSTREAM 協議（可選配置） | ✅ |
| Log Injection | `SecurityLogger.sanitize()` 移除 CR/LF/TAB | ✅ |
| 資訊洩漏 | 5xx 不回傳 detail、toString 排除 body | ✅ |
| 速率限制 | Redis INCR + EXPIRE、Retry-After header | ✅ |
| 租戶隔離 Native Query | `TenantAwareQuery.create()` 強制檢查 tenant_id | ✅ |
| SQL Injection 偵測 | `isSuspiciousInput()` 在 catch-all 中記錄安全事件 | ✅ |
| ErrorCode 唯一性 | `ErrorCodeTest` 驗證所有 code 不重複 | ✅ |

---

### 需要注意的安全問題

#### 1. [中等] `RateLimitInterceptor` 使用 `request.getRemoteAddr()` 而非真實客戶端 IP

```java
private String getClientIp(HttpServletRequest request) {
    return request.getRemoteAddr();
}
```

**風險**：如果系統部署在反向代理（Nginx / Load Balancer）後方，`getRemoteAddr()` 回傳的是代理 IP 而非真實客戶端 IP。所有使用者共用同一個限流 bucket，導致：
- 正常使用者被連帶限流（false positive）
- 或者攻擊者使用不同代理繞過限流（但此處不適用）

**設計決策說明**：測試中驗證了 `getRemoteAddr()` 而非 `X-Forwarded-For`，這是**刻意設計**——避免攻擊者偽造 `X-Forwarded-For` header 繞過限流。但需確保部署架構正確設定 trusted proxy。

**建議**：如果確定部署在 Nginx 後方，可搭配 Spring 的 `ForwardedHeaderFilter`（已在 Servlet 層剝離非信任 header）後安全使用 `getRemoteAddr()`。目前程式碼是安全的，但建議在架構文件中記錄此假設。

#### 2. [中等] `ClamAvVirusScanService` Socket 連線未設連線超時

```java
try (Socket socket = new Socket(host, port)) {
    socket.setSoTimeout(timeout);
    // ...
}
```

**風險**：`new Socket(host, port)` 使用系統預設連線超時（通常 2 分鐘）。如果 ClamAV 主機無法連線但未回拒絕，執行緒會被阻塞長達 2 分鐘，可能導致：
- 執行緒池耗盡
- 檔案上傳 API 回應緩慢

**建議**：使用 `Socket()` + `connect(SocketAddress, timeout)` 分離連線超時：
```java
Socket socket = new Socket();
socket.connect(new InetSocketAddress(host, port), timeout);
socket.setSoTimeout(timeout);
```

#### 3. [低] `GlobalExceptionHandler` 的 `isSuspiciousInput()` 偵測範圍有限

```java
private static boolean isSuspiciousInput(String message) {
    String lower = message.toLowerCase();
    return lower.contains("sql") && (lower.contains("syntax") || lower.contains("injection"))
            || lower.contains("' or '1'='1")
            // ...
}
```

**評估**：這是一個**啟發式偵測**，用於日誌記錄安全事件，不是防禦機制（防禦由 parameterized query 提供）。誤報和漏報都可以接受，因為它只影響日誌標記。設計意圖正確。

**建議**：可考慮額外比對 `1=1`、`/**/`、`char(` 等常見注入模式，但優先級低。

#### 4. [低] `FileValidationService` 白名單未包含 `.svg`

**評估**：SVG 被排除是**正確的安全決策**——SVG 可包含 `<script>` 標籤，如果未消毒直接渲染會導致 XSS。除非有專門的 SVG sanitizer，否則不應允許上傳。

#### 5. [低] `LocalFileStorageService` 使用 `REPLACE_EXISTING`

```java
Files.copy(input, targetFile, StandardCopyOption.REPLACE_EXISTING);
```

**風險**：如果 UUID 前綴碰撞（概率極低但非零），會覆蓋已存在的檔案。

**評估**：UUID 8 字元前綴（32^8 = 10^12 組合），加上 subDir 和 fileName 的組合，碰撞概率可忽略。若擔心，可改用完整 UUID 或加入時間戳。優先級極低。

---

## 程式碼品質審查（Code Review）

### ✅ 優點

#### 1. `TenantAwareQuery` — 租戶隔離安全網設計精巧

```java
if (!sqlLower.contains("tenant_id")) {
    throw new IllegalArgumentException(
        "[TenantAwareQuery] Native query 必須包含 tenant_id 條件！\n"
        + "Hibernate @Filter 不會作用於 native query...");
}
```

- 編譯期無法保證 native query 的租戶隔離 → 執行期攔截
- 提供明確的錯誤訊息指引開發者修正
- `createGlobal()` 作為有意識的逃生口（仍有 warning 日誌）

#### 2. `FileValidationService` — 三層防禦架構

1. **副檔名白名單**：阻擋 `.exe`、`.php`、`.jsp` 等危險類型
2. **Magic Bytes 比對**（Apache Tika）：防止 `.jpg` 內其實是 `.exe`
3. **Content-Type 對照表**：`EXTENSION_MIME_MAP` 精確對映

整合 `ImageSanitizer`（第四層：重新渲染圖片去除隱藏 payload）形成完整的檔案上傳安全鏈。

#### 3. `GlobalExceptionHandler` — 分層日誌策略

```java
if (code.getHttpStatus() >= 500) {
    log.error("...", ex);  // 含 stacktrace
} else {
    log.warn("...");       // 不含 stacktrace
}
```

- 4xx = client 錯誤 → warn（不用 stacktrace 汙染日誌）
- 5xx = server 錯誤 → error + stacktrace（方便排錯）
- catch-all 不回傳 detail → 不洩漏內部實作細節

#### 4. `BaseResponse` — 統一且安全的回應格式

- `@JsonInclude(NON_NULL)`：不序列化 null 欄位（減少 payload / 不洩漏結構）
- `@ToString(exclude = "body")`：日誌不會意外印出敏感 body 內容
- `isSuccess()` 方法方便業務判斷

#### 5. `SecurityLogger` — 結構化安全日誌

- 專用 Logger 名稱 `"SECURITY"` → 可獨立配置 appender（如送 SIEM）
- `sanitize()` 防止 Log Injection
- 統一格式 `[SECURITY] EVENT ip=x.x.x.x details` 方便 grep/ELK 過濾

#### 6. 測試覆蓋完整

- `RateLimitInterceptorTest`：11 個場景含 Redis 不可用、邊界值、TTL 為 null
- `FileValidationServiceTest`：副檔名、大小、Magic Bytes 全覆蓋
- `GlobalExceptionHandlerTest`：驗證不洩漏內部 detail
- `ErrorCodeTest`：驗證 code 唯一性、HTTP status 有效性

---

### 需要改進的問題

#### 6. [中等] `RateLimitInterceptor` 存在 TOCTOU 競爭條件

```java
currentCount = redisTemplate.opsForValue().increment(redisKey);
if (currentCount == 1) {
    redisTemplate.expire(redisKey, rateLimit.period(), TimeUnit.SECONDS);
}
```

**問題**：`increment` 和 `expire` 之間非原子操作。在極端併發下：
1. 請求 A increment → 1，準備設 expire
2. 請求 B increment → 2
3. 請求 A 設 expire 60s
4. 如果請求 A 在步驟 3 前崩潰，key 永遠不會過期 → 永久限流

**建議**：使用 Lua script 保證原子性：
```java
String script = """
    local count = redis.call('INCR', KEYS[1])
    if count == 1 then
        redis.call('EXPIRE', KEYS[1], ARGV[1])
    end
    return count
    """;
```

或使用 `SET NX EX` + `GET` 的替代方案。實際影響低（Redis 很少在兩行之間崩潰），但最佳實踐建議使用 Lua。

#### 7. [低] `BaseEntity` 註解中有錯字

```java
// 不加 @Setter：兩個欄位均由 AuditingEntityListener 管理，
// 應用層不懅直接寫入，尤其 createdAt 在語意上建立後險變。
```

「不懅」→「不應」、「險變」→「不變」（明顯的輸入法殘留）。

#### 8. [低] `ErrorCode` 編號間有跳號（10006 缺失）

```java
ACCESS_TOKEN_GET_INFO_FAILED("10005", ...),
// 10006 缺失
CAPTCHA_INVALID("10007", ...),
```

**評估**：不影響功能，但可能是歷史刪除的殘留。建議在 enum 加入註解標記保留區間，避免未來新增時誤用已廢棄的 code。

#### 9. [低] `ClamAvVirusScanService` scan 失敗返回 `ERROR` 但呼叫端行為不明

```java
return ScanResult.ERROR;
```

**問題**：介面定義了 `ERROR` 結果，但呼叫端（上傳邏輯）對 `ERROR` 的處理策略未在此模組定義。如果呼叫端將 `ERROR` 視為通過，則惡意檔案可能繞過掃描。

**建議**：在介面文件或 JavaDoc 中明確建議：`ERROR` 應視為**拒絕上傳**（fail-closed），除非有明確的 fallback 策略。

#### 10. [建議] `FileValidationService` 的 `maxFileSize` 可改為可配置的副檔名組合

目前所有檔案共用同一個 `maxFileSize`，但影片（mp4）和文件（docx）的合理大小差異很大。

**建議**：可按類別配置不同限制：
- 圖片：5MB
- 文件：20MB
- 影片：100MB

這不是安全問題，是 UX 改善建議。

---

## 架構總評

| 維度 | 評分 | 說明 |
|------|------|------|
| 安全性 | **9.5/10** | Path Traversal 四處防護、Magic Bytes 驗證、圖片消毒、ClamAV、Log Injection 防護、TenantAwareQuery 安全網。僅 ClamAV Socket 超時和 Rate Limit 原子性可改進。 |
| 正確性 | **9.5/10** | ErrorCode 唯一性有測試保證、exception handler 分層正確、Redis fail-open 設計合理。 |
| 效能 | **9/10** | Rate Limit 用 Redis O(1) 操作、ImageSanitizer 使用 BufferedImage 避免大量記憶體。ClamAV 連線超時是唯一風險點。 |
| 可維護性 | **9.5/10** | 介面設計乾淨（VirusScanService/FileStorageService）、@ConditionalOnProperty 切換實作、ErrorCode 集中管理。測試覆蓋完整。 |
| 可測試性 | **9.5/10** | 10 個測試檔案覆蓋所有核心邏輯、mock 設計良好、邊界測試完整。 |

---

## 建議優先級摘要

| 優先級 | 項目 | 類型 | 狀態 |
|--------|------|------|------|
| **P3** | `ClamAvVirusScanService` 加入連線超時 | Security / Availability | ✅ 已修正 |
| **P3** | `RateLimitInterceptor` 改用 Lua script 保證原子性 | Correctness | ✅ 已修正 |
| **P4** | 記錄 `getRemoteAddr()` 部署假設到架構文件 | Documentation | N/A（無 Nginx） |
| **P4** | `BaseEntity` 修正錯字 | Maintainability | ✅ 已修正 |
| **P4** | `ClamAV` ERROR 結果處理策略文件化 | Documentation | ✅ 已修正（JavaDoc） |
| **P5** | `FileValidationService` 按類別配置不同大小限制 | UX | ✅ 已修正 |
| **P5** | `ErrorCode` 跳號加註解 | Maintainability | ✅ 已存在 |
