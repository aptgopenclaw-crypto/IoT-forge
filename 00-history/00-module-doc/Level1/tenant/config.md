這個 `TenantProperties` 類別是一個標準的 **Spring Boot 設定屬性綁定類別 (Configuration Properties)**。

它的核心目的是：**提供一個集中且類型安全的機制，讓系統能夠透過外部設定檔（如 `application.yml`）來動態決定當前部署環境的「多租戶運行模式」。**

以下為其詳細功能與設計目的解析：

### 一、 核心功能解析

1.  **設定檔綁定 (`@ConfigurationProperties`)**：
    透過 `@ConfigurationProperties(prefix = "tenant")` 註解，Spring Boot 會自動將 `application.yml` 中所有以 `tenant.` 開頭的設定值，映射到這個類別的欄位上。
    *例如：`tenant.mode=multi` 會自動注入到 `mode` 欄位。*

2.  **定義兩種運行模式 (`mode`)**：
    *   **`single` (預設值)**：固定單一租戶模式。
    *   **`multi`**：多租戶 SaaS 模式。

3.  **定義預設租戶 ID (`defaultId`)**：
    *   預設值為 `"DEFAULT"`。
    *   專門用於 `single` 模式下，作為系統中唯一且固定的租戶識別碼。

---

### 二、 設計目的與應用場景

這個類別的存在，解決了系統在**不同商業部署場景下的彈性適配問題**，避免了一套程式碼只能應對一種架構的僵化設計。

#### 場景 1：單一租戶 / 私有化部署 (Single Mode)
*   **情境**：系統部署給單一客戶使用（例如某市政府的私有雲），或者作為內部系統使用，根本不需要「租戶隔離」的功能。
*   **設定**：`tenant.mode=single`，`tenant.default-id=CITY-GOV-01`。
*   **系統行為**：底層的攔截器（如 `JwtAuthenticationFilter`）在解析 Token 時，**不需要**去尋找 Token 裡的 `tenantId`，而是直接從 `TenantProperties` 讀取 `defaultId` 並注入到 `TenantContext` 中。
*   **好處**：大幅簡化了認證流程，且依然可以複用底層依賴 `TenantContext` 的業務邏輯，無需為了單一租戶寫另一套程式碼。

#### 場景 2：多租戶 SaaS 部署 (Multi Mode)
*   **情境**：系統作為 SaaS 平台運營，同時服務數百個不同的企業/場域，需要嚴格的資料隔離。
*   **設定**：`tenant.mode=multi`。
*   **系統行為**：系統會啟用完整的多租戶機制。攔截器必須從 JWT Token、URL 或 Header 中嚴格解析出當前請求的 `tenantId`，並交由前面提到的 `TenantFilterAspect` 進行 Hibernate Filter 的資料隔離，同時透過 `TenantEnabledCache` 檢查該租戶是否被停用。

---

### 三、 在架構中的協同運作

您可以將 `TenantProperties` 視為整個多租戶機制的 **「總開關」**。

雖然您提供的程式碼片段中沒有直接看到它被注入的地方，但在實際架構中，通常會有一個 `TenantContextInterceptor` 或 `TenantConfig` 依賴它：

```java
// 偽代碼示意：TenantContext 的初始化邏輯
if ("single".equals(tenantProperties.getMode())) {
    // 單一模式：直接寫死使用設定檔裡的 defaultId
    TenantContext.setCurrentTenant(tenantProperties.getDefaultId());
} else {
    // 多租戶模式：從 HTTP Request (JWT/Header) 中動態解析
    String tenantId = extractFromRequest(request);
    TenantContext.setCurrentTenant(tenantId);
}
```

### 總結
`TenantProperties` 是一個**架構適配器**。它讓這套具備嚴謹多租戶隔離能力（如 `TenantFilterAspect`、`TenantEnabledCache`）的程式碼，能夠「向下相容」到最簡單的單一租戶部署場景，實現了 **「一套程式碼，支援多種部署形態」** 的優雅設計。