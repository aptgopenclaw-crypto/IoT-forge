這兩個檔案在多租戶架構中扮演著非常特殊但至關重要的角色：一個是**架構重構期的「向後相容橋樑」**，另一個是**Hibernate 底層隔離機制的「全域宣告中心」**。

以下為這兩個檔案的詳細功能與設計目的解析：

---

### 1. `TenantContext.java` (位於 `com.taipei.iot.tenant` 套件)
**核心定位：架構重構期的「向後相容委派外觀 (Backward-Compatible Delegate Facade)」**

#### 功能與目的
這是一個被標記為 `@Deprecated` 的過渡性類別。它內部**沒有任何實質邏輯**，所有的靜態方法（如 `getCurrentTenantId`, `setCurrentTenantId`, `setSystemContext` 等）全部直接委派（Delegate）給位於 `common` 模組的新類別：`com.taipei.iot.common.context.TenantContext`。

#### 為什麼需要這個設計？（架構演進的痛點與解法）
1.  **解決模組依賴倒置問題**：
    *   **過去**：`TenantContext` 放在 `tenant` 模組。這會導致一個問題：如果 `common` 模組（例如全域的 `JwtAuthenticationFilter` 或 `SecurityLogger`）需要讀取或設定當前租戶 ID，`common` 就必須依賴 `tenant` 模組，這違反了「底層不依賴上層」的原則。
    *   **現在**：將真正的 `TenantContext` 實作搬遷到最底層的 `common` 模組，讓所有上層模組（包含 `tenant` 本身）都能依賴它。
2.  **避免「大爆炸」重構 (Big Bang Refactoring)**：
    *   如果直接刪除舊的 `tenant.TenantContext`，專案中幾百個舊有程式碼的 `import` 會瞬間全部報錯，導致編譯失敗。
    *   保留這個 `@Deprecated` 的委派類別，可以讓舊程式碼**繼續編譯通過並正常運行**（因為底層行為已經指向新的 `common.TenantContext`）。
3.  **平滑過渡 (Transient Period)**：
    *   開發團隊可以透過 IDE 的警告（Deprecated）或靜態程式碼分析工具（如 SonarQube），在後續的迭代中**分批、漸進地**將舊的 `import` 替換為新的 `com.taipei.iot.common.context.TenantContext`，最終再將此委派類別安全移除。

---

### 2. `package-info.java` (位於 `com.taipei.iot.tenant` 套件)
**核心定位：Hibernate 多租戶過濾器的「全域宣告中心 (Global Filter Definition)」**

#### 功能與目的
`package-info.java` 是 Java 中用來為整個套件（Package）提供註解或文件說明的特殊檔案。在這裡，它宣告了 Hibernate 的多租戶核心機制：

```java
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
package com.taipei.iot.tenant;
```

這行程式碼的作用是：**在 Hibernate 的全域層級，定義了一個名為 `tenantFilter` 的過濾器，並宣告它需要一個名為 `tenantId` 的 String 參數。**

#### 為什麼要放在 `package-info.java` 而不是 Entity 上？
在 Hibernate 中，`@FilterDef`（定義過濾器長什麼樣）和 `@Filter`（啟用過濾器）是分開的。
1.  **避免重複宣告 (DRY 原則)**：
    *   系統中可能有幾十個 Entity 需要進行租戶隔離（如 `OrderEntity`, `DeviceEntity`, `UserEntity`）。
    *   如果把 `@FilterDef` 寫在每一個 Entity 上，會產生大量的重複程式碼。
    *   將 `@FilterDef` 放在 `package-info.java`，意味著**這個過濾器定義對該套件下的所有 Entity 都是可見且全域有效的**。
2.  **Entity 只需「啟用」即可**：
    *   因為全域已經定義好了 `tenantFilter`，底層的 `TenantAware` Entity 只需要加上 `@Filter(name = "tenantFilter")` 即可啟用隔離，無需重複定義參數。
3.  **與 AOP 機制的完美串聯**：
    *   這個全域定義，正是為了配合前面提到的 **`TenantFilterAspect`**。
    *   當 `TenantFilterAspect` 執行 `session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);` 時，Hibernate 底層就是去尋找這個在 `package-info.java` 中定義好的 `tenantFilter`，然後將參數注入，最終在生成的 SQL 語句末尾自動加上 `WHERE tenant_id = ?`。

---

### 總結：兩者在架構中的協同意義

*   **`TenantContext.java` (過渡期)** 展現了團隊在進行**底層基礎設施重構**時的嚴謹度。透過委派模式，他們成功將 `TenantContext` 下沉到 `common` 模組，解決了模組循環依賴或反向依賴的架構腐化問題，同時保證了線上系統的穩定過渡。
*   **`package-info.java` (底層機制)** 展現了對 **Hibernate 進階特性**的熟練運用。透過套件層級的 `@FilterDef`，優雅地實現了「定義一次，全域 Entity 共用」的資料隔離基礎設施，為 `TenantFilterAspect` 的自動 SQL 注入提供了底層契約。

這兩個檔案雖然不是直接的業務邏輯，但卻是支撐整個多租戶系統**架構演進**與**底層隔離機制**的關鍵基石。