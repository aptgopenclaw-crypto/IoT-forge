這個 `BaseLoggerAspect.java` 是整個審計日誌（Audit Log）系統的**核心攔截器（AOP 切面）**。

它的主要職責是：**自動攔截所有標註了 `@AuditEvent` 註解的方法，在方法執行前後收集上下文資訊、請求參數與執行結果，最後將這些資料交給 `AuditAsyncWriter` 進行非同步寫入。**

以下為該類別的詳細拆解與設計亮點說明：

### 1. 觸發機制 (AOP 攔截)
```java
@Around("@annotation(auditEvent)")
public Object logApiCall(ProceedingJoinPoint pjp, AuditEvent auditEvent) throws Throwable
```
*   **`@Around`**：使用環繞通知（Around Advice），這意味著它可以完全控制目標方法的執行時機（執行前、執行後、甚至攔截不讓它執行）。
*   **`@annotation(auditEvent)`**：切入點（Pointcut）設定為「任何標註了 `@AuditEvent` 的方法」。當 Controller 或 Service 的方法加上 `@AuditEvent(AuditEventType.XXX)` 時，就會觸發此切面。

### 2. 執行流程解析
這個切面的執行邏輯可以分為三個階段：

#### 階段一：執行前（主執行緒上下文捕獲）
因為後續要使用 `@Async` 非同步寫入日誌，而**非同步執行緒無法讀取主執行緒的 `ThreadLocal` 變數**，所以必須在切面剛觸發時（還在主執行緒），把所有上下文資訊「快照」下來：
*   **租戶與使用者資訊**：從 `TenantContext` 抓取 `tenantId`、`impersonatedBy` (代操者)；從 `SecurityContextUtils` 抓取 `userId`、`username`、`deptId`。
*   **HTTP 請求資訊**：透過 `RequestContextHolder` 獲取當前請求的 `URI`、`Client IP` 和 `User-Agent`。
*   **計時**：記錄 `start` 時間，用於後續計算 API 執行耗時。

#### 階段二：執行目標方法與例外處理
```java
try {
    result = pjp.proceed(); // 執行原本的業務邏輯
} catch (BusinessException e) {
    errorCode = e.getErrorCode().getCode(); // 捕獲業務錯誤碼
    throw e; // 重新拋出，不影響原業務
} catch (Exception e) {
    errorCode = "99999"; // 捕獲未知系統錯誤
    throw e;
}
```
*   執行 `pjp.proceed()` 讓原本的業務邏輯繼續跑。
*   **錯誤碼捕捉**：如果發生 `BusinessException`，會提取其專屬的錯誤碼；如果是其他未預期的 `Exception`，則統一標記為系統錯誤碼 `"99999"`。
*   **重新拋出例外**：捕獲例外後**必須 `throw e`**，這樣才不會把原本的錯誤「吃掉」，確保前端或上層呼叫者能收到正確的錯誤回應。

#### 階段三：執行後（非同步寫入日誌）
```java
finally {
    // 1. 計算耗時
    // 2. 參數脫敏 (PayloadSanitizer)
    // 3. 呼叫 auditAsyncWriter.saveAsync(...)
}
```
*   將日誌寫入邏輯放在 **`finally` 區塊**，確保**無論業務邏輯是成功還是拋出例外，審計日誌都一定會被記錄**。
*   **參數脫敏**：使用 `PayloadSanitizer.sanitize(pjp.getArgs())` 處理方法參數，避免將密碼、Token 等敏感資訊寫入日誌。
*   **觸發非同步寫入**：將前面收集到的所有變數，打包傳給 `AuditAsyncWriter.saveAsync()`，由它負責實際的資料庫寫入。

### 3. 關鍵設計亮點與安全考量

1.  **解決 ThreadLocal 傳遞問題**：
    如前所述，在主執行緒提前抓取 `tenantId` 和 `userId`，然後作為參數傳遞給 `AuditAsyncWriter`。這完美解決了 Spring `@Async` 導致上下文丟失的問題。
2.  **安全的 IP 獲取策略**：
    ```java
    // 直接使用 TCP 連線來源 IP，不信任可偽造的 X-Forwarded-For header。
    return req.getRemoteAddr();
    ```
    註解特別說明了不信任 `X-Forwarded-For`。這是因為惡意使用者可以輕易在 HTTP Header 中偽造該欄位。直接讀取底層 TCP 連線的 `RemoteAddr` 雖然在經過多層反向代理（如 Nginx）時可能拿到的是代理 IP，但能確保記錄的 IP 是**絕對真實且無法偽造的**（與系統的 RateLimitInterceptor 保持一致的安全策略）。
3.  **支援「代操」審計 (Impersonation)**：
    透過 `TenantContext.getImpersonator()` 抓取 `impersonatedBy`。這在 B2B 或 SaaS 系統中很常見，例如「超級管理員」代替「一般租戶管理員」進行操作時，日誌會同時記錄實際操作者（管理員）和被代操者，以利後續追溯。

### 總結
`BaseLoggerAspect` 是一個設計嚴謹的 AOP 切面。它將「審計日誌記錄」這個橫切關注點（Cross-cutting concern）從業務程式碼中完全剝離。業務開發者只需要在方法上加上 `@AuditEvent`，切面就會自動處理**上下文抓取、耗時計算、例外錯誤碼記錄、參數脫敏**，並安全地交由非同步執行緒寫入資料庫。