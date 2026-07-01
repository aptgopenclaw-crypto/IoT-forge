在這段程式碼的脈絡中，**Port / Interface（埠 / 介面）** 具體指的是 `AuditAsyncWriter.java` 裡引入的這兩個類別：

```java
import com.taipei.iot.common.audit.port.UserDisplayInfo;
import com.taipei.iot.common.audit.port.UserDisplayInfoProvider;
```

這裡的 `UserDisplayInfoProvider` 就是一個 **Interface（介面）**，在架構設計上被稱為 **Port（埠）**。

以下為您詳細拆解這個設計背後的意義與「依賴反轉原則 (DIP)」的運作方式：

### 1. 什麼是依賴反轉原則 (DIP)？
DIP 的核心思想是：**「高層模組不應該依賴低層模組，兩者都應該依賴抽象（Abstract/Interface）。」**
*   **高層模組**：核心業務邏輯（在這裡是 `audit` 審計模組）。
*   **低層模組**：基礎設施或具體實作（在這裡是 `auth` 或 `user` 模組，負責實際查詢資料庫取得使用者資訊）。
*   **抽象**：定義好的介面（在這裡是 `UserDisplayInfoProvider`）。

### 2. 傳統做法 vs. 當前做法（為什麼要這樣做？）

#### ❌ 傳統做法（違反 DIP，高度耦合）
如果 `AuditAsyncWriter` 直接注入 `UserService` 或 `UserRepository`：
```java
// 假設的錯誤寫法
private final UserService userService; 
```
**缺點**：
1.  **模組綁死**：`audit` 模組強行依賴了 `user` 模組。如果 `user` 模組重構、改名或替換實作，`audit` 模組也必須跟著改。
2.  **循環依賴風險**：如果 `user` 模組在登入時也需要呼叫 `audit` 模組寫日誌，就會形成 `audit` 依賴 `user`，`user` 又依賴 `audit` 的死結，Spring 啟動時會報錯。

#### ✅ 當前做法（符合 DIP，依賴反轉）
程式碼中，`audit` 模組自己定義了一個介面 `UserDisplayInfoProvider`（放在 `common.audit.port` 套件下），然後**只依賴這個介面**：
```java
// 實際的正確寫法
private final UserDisplayInfoProvider userDisplayInfoProvider;
```
**運作方式**：
1.  `audit` 模組宣告：「我需要一個能根據 userId 查詢 displayName 和 email 的能力（定義 Port）」。
2.  `user` 或 `auth` 模組去**實作**這個介面（這稱為 Adapter / 適配器），並透過 Spring 的 `@Component` 注入到容器中。
3.  Spring 在執行時，會把具體的實作類別注入給 `AuditAsyncWriter`。

### 3. 為什麼套件名稱叫 `port`？
這個命名通常來自於 **六邊形架構 (Hexagonal Architecture)** 或 **整潔架構 (Clean Architecture)**。
*   在這些架構中，核心領域（Core/Domain）被視為一個港口。
*   **Port（埠）**：定義在核心模組內的**介面**，代表核心模組與外部世界（如資料庫、其他微服務、UI）溝通的契約。
*   **Adapter（適配器）**：在外部模組中**實作**這些介面的具體類別（例如 `UserDisplayInfoProviderImpl`）。

### 4. 這樣設計帶來的具體好處

1.  **極致的解耦**：`audit` 模組完全不知道底層是怎麼查使用者的（可能是查 MySQL、可能是查 Redis、甚至是呼叫外部 API）。它只關心「有沒有這個介面」。
2.  **打破循環依賴**：依賴方向變成了單向的。`user` 模組依賴 `common.audit.port`，`audit` 模組也依賴 `common.audit.port`。兩者都依賴抽象，完美解決了互相依賴的問題。
3.  **極易於單元測試**：當你要測試 `AuditAsyncWriter` 時，不需要啟動真實的資料庫或 `UserService`。你只需要用 Mockito 寫一個假物件（Mock）實作 `UserDisplayInfoProvider`，就能快速且穩定地測試審計寫入邏輯。

**總結來說：**
在這裡，**Port/Interface 就是指 `UserDisplayInfoProvider` 這個介面**。它是 `audit` 模組向外伸出的一隻「手」，用來抓取它需要的使用者資訊，但不需要知道這隻手最後連著的是哪個具體的模組或資料庫，從而實現了優雅的模組解耦。