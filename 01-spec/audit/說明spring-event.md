**Spring Event（Spring 事件機制）** 是 Spring 框架內建的一套**發布-訂閱（Publish-Subscribe）模式**的實現。

如果用一句話來概括：**它允許系統中的某個組件在發生某件事時「廣播」一個訊息，而其他關心這件事的組件可以「監聽」並自動做出反應，雙方完全不需要知道彼此的存在。**

這正是我們前面提到「中觀層次：應用內部模組解耦」的終極武器。

---

### 1. 核心三要素

要理解 Spring Event，只需要記住三個角色：

1.  **Event（事件）**：
    *   封裝了具體業務資料的物件。它代表「發生了什麼事」。
    *   *在您的程式碼中*：`LoginAuditEvent` 就是一個事件，裡面攜帶了 `tenantId`, `userId`, `ipAddress` 等登入相關的資料。
2.  **Publisher（發布者）**：
    *   觸發事件的來源。當業務邏輯執行到某個節點時，發布者會把 Event 丟給 Spring 容器。
    *   *實作方式*：透過注入 `ApplicationEventPublisher` 介面，呼叫 `publishEvent(new LoginAuditEvent(...))`。
3.  **Listener（監聽者）**：
    *   接收並處理事件的組件。
    *   *實作方式*：在方法上加上 `@EventListener` 註解，並把 Event 物件作為參數傳入。
    *   *在您的程式碼中*：`LoginAuditListener` 就是監聽者，它的 `onLoginAudit` 方法專門監聽 `LoginAuditEvent`。

---

### 2. 通俗的比喻：微信群組 vs 直接私聊

*   **沒有 Spring Event（直接呼叫 / 強耦合）**：
    就像「私聊」。登入模組（AuthService）在登入成功後，必須親自去通知審計模組（`auditService.log()`），接著又要通知簡訊模組（`smsService.sendWelcome()`），還要通知積分模組（`pointService.add()`）。
    **痛點**：登入模組變成了「大管家」，它必須認識並依賴所有下游模組。如果哪天新增了一個「發送 Email」的功能，登入模組的程式碼就要改一次。
*   **使用 Spring Event（發布訂閱 / 解耦）**：
    就像「微信群組廣播」。登入模組只在群組裡發了一條訊息：「使用者 A 登入成功了！（`publishEvent`）」。
    審計模組、簡訊模組、積分模組都在群組裡（各自 `@EventListener`）。它們聽到訊息後，各自去執行自己的邏輯。
    **優勢**：登入模組**完全不知道**群組裡有哪些人，它只管發訊息。未來要加 Email 功能，只需要新拉一個人進群組（新增一個 Listener），登入模組的程式碼**一行都不用改**。

---

### 3. 結合您的程式碼深度解析

我們來看看 `LoginAuditListener.java` 是如何完美運用 Spring Event 的：

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class LoginAuditListener {
    private final UserEventLogRepository userEventLogRepository;

    // 1. @EventListener：宣告這個方法是一個監聽器，專門接收 LoginAuditEvent
    // 2. @Async("auditExecutor")：收到事件後，丟給專屬執行緒池非同步處理，不阻塞登入主流程
    @Async("auditExecutor")
    @EventListener
    public void onLoginAudit(LoginAuditEvent event) {
        // ... 組裝 Entity ...
        
        // 3. 切換到 SYSTEM 上下文寫入資料庫
        TenantContext.runInSystemContext(() -> userEventLogRepository.save(entity));
    }
}
```

#### 💡 關鍵細節：為什麼用 `@EventListener` 而不是 `@TransactionalEventListener`？
在 `LoginAuditListener` 的 Javadoc 中特別提到了這一點，這是 Spring Event 的高階考點：
*   **`@EventListener`**：只要事件被 `publish`，**立刻**觸發監聽器。
*   **`@TransactionalEventListener`**：必須等到發布事件的**資料庫事務（Transaction）成功 Commit 之後**，才會觸發監聽器。

**為什麼這裡選前者？**
因為「登入/驗證」這個動作，有時候是純記憶體操作（例如驗證 JWT Token、比對 Redis 快取），**它本身根本沒有包在資料庫的 `@Transactional` 事務中**。如果用了 `@TransactionalEventListener`，因為沒有事務提交這個動作，監聽器就**永遠不會被觸發**，導致審計日誌漏寫。因此，使用 `@EventListener` 是最安全、最穩妥的選擇。

---

### 4. Spring Event 的執行流程（底層原理）

當您在程式碼中呼叫 `publisher.publishEvent(event)` 時，底層發生了什麼？

1.  **觸發發布**：`ApplicationEventPublisher` 將事件交給 Spring 內部的 `ApplicationEventMulticaster`（事件廣播器）。
2.  **尋找監聽者**：廣播器會去 IoC 容器中掃描，找出所有帶有 `@EventListener` 且參數型別匹配（如 `LoginAuditEvent`）的方法。
3.  **分發執行**：
    *   如果監聽方法**沒有** `@Async`：廣播器會**同步**呼叫該方法（在同一個執行緒中執行，會阻塞發布者）。
    *   如果監聽方法**有** `@Async`（如您的程式碼）：廣播器會將這個呼叫任務丟給 Spring 的 `TaskExecutor`（執行緒池），**非同步**執行，發布者立刻返回。

---

### 5. 總結：Spring Event 在知識圖譜中的定位

回到我們之前建立的 Spring Boot 知識圖譜：
*   **IoC 容器** 負責把物件「組裝」起來。
*   **AOP 切面** 負責在方法執行時「攔截」並增強。
*   **Spring Event** 則負責在物件與物件之間建立一條 **「非同步、解耦的通訊總線」**。

它讓系統從「緊密的齒輪咬合（方法互相呼叫）」變成了「鬆散的廣播電台（事件發布與監聽）」，是構建高內聚、低耦合的大型企業級應用不可或缺的基礎設施。