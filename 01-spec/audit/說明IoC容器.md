要理解 **IoC 容器（IoC Container）**，我們需要先把它拆成兩個部分來看：**IoC（控制反轉）** 是一種「設計思想」，而 **容器（Container）** 是實現這個思想的「具體工具」。

在 Spring 框架中，IoC 容器（通常是 `ApplicationContext`）就是整個系統的心臟。以下為您詳細拆解：

### 1. 什麼是 IoC（控制反轉）？
在傳統的程式設計中，如果你寫了一個類別 A，裡面需要用到了類別 B，你會自己手動去創建它：
```java
// 傳統寫法：控制權在「你自己」手裡
public class A {
    private B b = new B(); // 你自己決定什麼時候 new B()，以及 new 哪一個 B()
}
```
**IoC（Inversion of Control，控制反轉）** 的意思是：**把「創建物件和管理依賴」的控制權，從你的程式碼中剝奪，交給外部的「容器」來管理。**
```java
// IoC 寫法：控制權交給「容器」
public class A {
    private B b; 
    // 我不自己 new 了，我留個洞，等容器把 B 塞給我
}
```

### 2. 什麼是 IoC 容器？
**IoC 容器就是那個負責「幫你創建物件、並把物件塞給你」的超級大管家。** 
在 Spring 中，這個容器在背景默默運行，它的主要職責有三個：

1.  **實例化（Instantiation）**：幫你 `new` 出所有的物件（在 Spring 中稱為 **Bean**）。
2.  **依賴注入（Dependency Injection, DI）**：這是 IoC 的具體實現方式。容器會自動找出物件之間的依賴關係，並把依賴的物件「注入」進去。
3.  **管理生命週期（Lifecycle）**：管理物件從創建、初始化、使用到最終銷毀的整個過程。

### 3. 結合您提供的程式碼來理解
在您上傳的程式碼中，IoC 容器無處不在。我們以 `AuditAsyncWriter` 為例：

```java
@Service
@RequiredArgsConstructor
public class AuditAsyncWriter {
    private final UserEventLogRepository userEventLogRepository;
    private final UserDisplayInfoProvider userDisplayInfoProvider;
    // ...
}
```

**IoC 容器在背後做了什麼？**
1.  **掃描與註冊**：容器啟動時，看到 `@Service` 註解，就知道：「我要在記憶體中創建一個 `AuditAsyncWriter` 的物件（Bean）」。
2.  **發現依賴**：容器看到 `@RequiredArgsConstructor`（Lombok 生成的建構子），發現這個類別需要 `UserEventLogRepository` 和 `UserDisplayInfoProvider`。
3.  **依賴注入（DI）**：容器會去自己的倉庫裡找，看看有沒有這兩個物件。如果有，就透過建構子把它們「塞」給 `AuditAsyncWriter`。如果沒有，容器就會報錯（啟動失敗）。

再看 `AuditAsyncConfig`：
```java
@Configuration
public class AuditAsyncConfig {
    @Bean("auditExecutor")
    public TaskExecutor auditExecutor() { ... }
}
```
這裡是**手動告訴容器**該怎麼做：「容器啊，請你執行這個方法，把回傳的執行緒池物件收好，並且給它取個名字叫 `auditExecutor`，以後別人需要時就從你這裡拿。」

### 4. 通俗的比喻
*   **傳統寫法（沒有容器）**：就像你要吃一頓飯，你必須自己種菜、自己買肉、自己下廚（自己 `new` 所有的依賴物件）。
*   **IoC 容器**：就像是一個**高級餐廳的中央廚房（或大管家）**。你（業務類別）只需要點菜（宣告需要什麼依賴），中央廚房（IoC 容器）就會把洗好、切好、煮好的食材（依賴物件）直接端到你面前（依賴注入）。你完全不需要知道食材是怎麼來的。

### 總結
**IoC 容器**就是一個幫你管理所有物件（Bean）的倉庫與調度中心。它讓你的程式碼變得**低耦合**（類別之間不直接互相 `new`），**易於測試**（測試時可以輕鬆替換成假的依賴物件），並且讓架構變得非常清晰。