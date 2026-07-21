這是一個非常敏銳的觀察！您發現了 `TenantScopedRepository` 裡面**完全沒有任何方法**，它是一個純粹的 **「標記介面 (Marker Interface)」**。

在 Java 設計模式中，標記介面的作用不是為了讓類別實作某些方法，而是為了 **「貼標籤」**（類似 Java 內建的 `Serializable` 或 `Cloneable`）。

在這個多租戶架構中，其他 Repository 是透過 **「是否實作這個介面」** 來決定自己的資料是否需要被隔離。以下為您詳細說明它的應用方式與底層運作原理：

---

### 1. Repository 的兩種分類與應用方式

系統中的 Repository 會被嚴格區分為兩類，透過「有沒有實作 `TenantScopedRepository`」來劃分界線：

#### 🟢 類別 A：需要租戶隔離的 Repository (租戶資料)
這類 Repository 操作的實體（如 `AnnouncementAttachment`、`Order`、`Product`）在資料庫中都有 `tenant_id` 欄位。
*   **應用方式**：在宣告時，**同時繼承** `JpaRepository` 和 `TenantScopedRepository`。
*   **程式碼範例**：
    ```java
    public interface AnnouncementAttachmentRepository
        extends JpaRepository<AnnouncementAttachment, Long>, TenantScopedRepository { // 👈 貼上標籤
        // ...
    }
    ```
*   **語意**：「我操作的資料是屬於特定租戶的，請系統幫我自動過濾，確保 A 租戶看不到 B 租戶的附件。」

#### 🔵 類別 B：不需要隔離的「全域 Repository」 (系統資料)
這類 Repository 操作的實體（如 `User`、`Tenant`、`Role`）是系統層級的資料，沒有 `tenant_id`，或者是跨租戶共用的。
*   **應用方式**：**只繼承** `JpaRepository`，**不實作** `TenantScopedRepository`。
*   **程式碼範例**：
    ```java
    public interface UserRepository extends JpaRepository<User, Long> { 
        // 👈 沒有貼標籤，代表這是全域資料
    }
    ```
*   **語意**：「我是系統全域資料（例如登入時需要查詢所有租戶的使用者），請不要幫我加上 `tenant_id` 的過濾條件。」

---

### 2. 這個「標籤」是如何被系統使用的？ (串聯 TenantFilterAspect)

這個標籤的真正威力，在於提供給 **`TenantFilterAspect` (AOP 保全)** 進行判斷。

回顧一下 `TenantFilterAspect.java` 中的這段核心程式碼：

```java
@Before("execution(* com.taipei.iot..*.repository..*Repository.*(..))")
public void enableTenantFilter(JoinPoint jp) {
    // 👇 關鍵：檢查這個 Repository 有沒有貼上 TenantScopedRepository 的標籤
    if (!(jp.getThis() instanceof TenantScopedRepository)) {
        return; // 如果沒貼標籤 (例如 UserRepository)，直接放行，不啟用 Filter
    }

    // 如果有貼標籤 (例如 AnnouncementAttachmentRepository)，才繼續執行租戶過濾邏輯...
    if (TenantContext.isSystemContext()) { ... }
    
    // 啟用 Hibernate Filter
    Session session = entityManager.unwrap(Session.class);
    session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
}
```

**運作流程：**
1. 當 Service 呼叫 `announcementAttachmentRepository.findAll()` 時，AOP 攔截到這個動作。
2. AOP 檢查：「這個 Repository 是 `TenantScopedRepository` 嗎？」
3. **如果是 (有貼標籤)**：AOP 就會去讀取 `TenantContext`，並啟用 Hibernate 的 `tenantFilter`，在 SQL 加上 `WHERE tenant_id = ?`。
4. **如果不是 (沒貼標籤)**：AOP 直接 `return` 結束，Hibernate 執行原本的 SQL，不加任何租戶條件。

---

### 3. 為什麼要用「標記介面」而不是用「註解 (@Annotation)」？

您可能會想，為什麼不直接在 Repository 上面加一個 `@TenantScoped` 註解就好？使用 Marker Interface 有幾個架構上的優勢：

1. **型別安全 (Type Safety)**：
   在 Java 中，`instanceof` 檢查介面是非常高效且安全的。如果未來有其他底層組件需要區分這兩類 Repository，可以直接透過型別判斷，而不需要用反射 (Reflection) 去讀取註解。
2. **Spring Data JPA 的慣例**：
   Spring Data JPA 本身大量使用這種模式。例如，當您想讓 Repository 支援 QueryDSL 時，也是透過繼承 `QuerydslPredicateExecutor` 這個標記介面來實現的。這符合 Spring 生態系的設計哲學。
3. **語意更強烈且集中**：
   繼承介面是一種「契約 (Contract)」。當開發者看到 `extends JpaRepository, TenantScopedRepository` 時，能立刻在語法層級上意識到：「這個 Repository 的資料是受租戶隔離保護的」，比散落在各處的註解更具警示效果。

### 總結

`TenantScopedRepository` 雖然是一個空介面，但它是整個多租戶架構中 **「區分資料邊界」** 的關鍵開關。
*   **有實作它** = 租戶私有資料 (自動加上 `WHERE tenant_id = ?`)
*   **沒實作它** = 系統全域資料 (不加上過濾條件)

透過這種設計，架構師成功地将「資料隔離的責任」從開發者手動寫 SQL 條件，轉化為「宣告式的介面繼承」，大幅降低了人為疏漏導致資料外洩的風險。