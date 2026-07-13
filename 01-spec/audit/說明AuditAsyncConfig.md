這個 `AuditAsyncConfig.java` 是一個 Spring Boot 的配置類，主要作用是**為「審計日誌（Audit Log）」功能配置一個專屬的異步（非同步）線程池**。

透過將審計日誌的記錄工作放到異步線程池中執行，可以避免寫入日誌的 I/O 操作阻塞主業務邏輯，從而提升系統的響應速度與吞吐量。

以下是針對程式碼的詳細解說：

### 1. 類別層級的註解
*   **`@Configuration`**：標示這是一個 Spring 的配置類。Spring 容器在啟動時會掃描並解析這個類別，將其內部的 `@Bean` 方法執行並將返回的物件註冊到 IoC 容器中。
*   **`@EnableAsync`**：開啟 Spring 的異步方法執行支援。加上這個註解後，專案中其他使用 `@Async` 註解的方法才會真正以多線程異步的方式執行。

### 2. 核心方法：定義線程池
```java
@Bean("auditExecutor")
public TaskExecutor auditExecutor()
```
*   **`@Bean("auditExecutor")`**：在 Spring 容器中註冊一個名為 `auditExecutor` 的 Bean。當其他 Service 需要使用這個專屬線程池時，可以透過 `@Async("auditExecutor")` 來指定使用它。
*   **`TaskExecutor`**：Spring 提供的任務執行器接口，底層封裝了 Java 的 `Executor`。

### 3. 線程池參數配置解析
程式碼中使用了 Spring 提供的 `ThreadPoolTaskExecutor` 來配置底層的 Java 線程池，各項參數的意義如下：

*   **`executor.setCorePoolSize(2);`**
    *   **核心線程數 = 2**：線程池初始化時會建立 2 個核心線程。即使這些線程處於空閒狀態，預設也不會被銷毀（除非設定了 `allowCoreThreadTimeOut`）。這保證了隨時有基本的處理能力來應對審計日誌。
*   **`executor.setMaxPoolSize(8);`**
    *   **最大線程數 = 8**：當核心線程都在忙碌，且阻塞隊列也滿載時，線程池會創建新線程來處理任務，但總線程數最多不會超過 8 個。這限制了審計任務對系統 CPU/記憶體資源的過度佔用。
*   **`executor.setQueueCapacity(500);`**
    *   **阻塞隊列容量 = 500**：當核心線程都在忙時，新進來的審計任務會被放入這個等待隊列中。隊列最大長度為 500，能緩衝一定數量的突發日誌請求。
*   **`executor.setThreadNamePrefix("audit-async-");`**
    *   **線程名前綴 = "audit-async-"**：為線程池中的線程命名。當系統出現問題需要查看日誌或進行 Thread Dump（線程轉儲）時，可以一眼看出哪些線程是專門用來處理審計異步任務的，方便排查問題。
*   **`executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());`**
    *   **拒絕策略 = CallerRunsPolicy**：這是**最關鍵的配置之一**。當線程數達到最大（8個）且隊列也滿了（500個）時，新提交的任務會被拒絕。使用 `CallerRunsPolicy` 策略意味著：**誰調用這個異步方法，就由誰（通常是主業務線程）自己來執行這個任務**。
    *   *好處*：審計日誌通常要求**不能丟失**。這個策略不僅保證了日誌任務絕對不會被丟棄，還能產生「背壓（Back-pressure）」效果——讓主業務線程自己去寫日誌，主線程就會變慢，從而自動減緩新任務提交的速度，給線程池時間消化隊列中的任務。

### 總結
這個配置類為系統的審計模組打造了一個**輕量級、高容錯**的異步處理機制。它既保證了寫入審計日誌不會拖慢主业务流程，又透過合理的隊列緩衝與拒絕策略，確保了在極端高併發情況下審計日誌不會丟失，且不會導致系統 OOM（記憶體溢出）。