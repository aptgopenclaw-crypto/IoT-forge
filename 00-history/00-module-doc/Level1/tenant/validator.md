這兩個 Class 共同構成了多租戶系統的 **「結構性防護網」**，但它們的防護層級完全不同：一個是**啟動期的架構一致性守門員**，另一個是**執行期的資料邊界防護員**。

---

## 1. `TenantConsistencyValidator`
### 核心定位：啟動期的「架構契約」強制執行器 (Fail-Fast Guard)

#### 功能
在 Spring Boot 應用啟動完成時（`ApplicationReadyEvent`），自動掃描 JPA Metamodel 中所有實作了 `TenantAware` 介面的 Entity，逐一檢查它們對應的 Spring Data Repository **是否都有實作 `TenantScopedRepository` 標記介面**。若有任何一個遺漏，立即拋出 `IllegalStateException` **中止應用啟動**。

#### 解決的痛點 (T-8 / T-1 修復)

| 項目 | 說明 |
|---|---|
| **歷史事故** | `AnnouncementAttachmentRepository` 忘記 `implements TenantScopedRepository` → `TenantFilterAspect` 無法攔截它 → Hibernate `@Filter("tenantFilter")` 從未啟用 → **跨租戶資料外洩** |
| **根因分析** | `TenantScopedRepository` 是純標記介面（Marker Interface），Java 編譯器無法在編譯期檢查「某個 Entity 的 Repository 是否遺漏了這個介面」 |
| **修復策略** | 將檢查制度化為啟動期驗證，把原本只能在**生產環境出事故後才發現**的問題，提前到 **CI/CD 階段或開發者本地啟動時就攔截** |

#### 設計細節
*   **精準掃描**：透過 `EntityManagerFactory.getMetamodel()` 取得所有 JPA Entity，再用 `TenantAware.class.isAssignableFrom()` 過濾出需要租戶隔離的 Entity。
*   **排除範圍明確**：刻意不掛 `@Filter` 的 Entity（如 `UserSessionEntity`）由另一套設計決策測試鎖死；純 JPQL DAO（如 `PasswordPolicyDao`）不在檢查範圍，因其內部已自行帶 `tenantId` 參數。
*   **可測試性**：核心邏輯 `checkConsistency()` 被抽為 `public static` 純函式，不依賴 Spring Context，可直接寫單元測試驗證。

---

## 2. `TenantConfigValidator`
### 核心定位：執行期的「JSON 邊界」防護員 (Input Boundary Guard)

#### 功能
驗證 `TenantEntity.config` 欄位（一個 JSONB 類型的 `Map<String, Object>`）是否符合預設的尺寸與結構限制。違反時拋出 `IllegalArgumentException`，由全局例外處理器轉為 HTTP 400。

#### 解決的痛點 (T-9 修復)

| 項目 | 說明 |
|---|---|
| **潛在風險** | `config` 欄位是開放式的 JSONB，如果未來新增 API 允許寫入此欄位，惡意使用者可能塞入超大 JSON 撐爆 DB / Index，或製造極深巢狀結構導致解析 OOM |
| **修復策略** | 在寫入前進行三重邊界檢查，作為**預防性防禦 (Defense in Depth)** |

#### 三重防護限制

```
┌─────────────────────────────────────────────────────────┐
│                  TenantConfigValidator                   │
├──────────────────┬──────────────────────────────────────┤
│  防護維度         │  限制值與目的                         │
├──────────────────┼──────────────────────────────────────┤
│  序列化大小       │  ≤ 10 KB                             │
│                  │  防止超大 payload 撐爆 DB 儲存空間     │
├──────────────────┼──────────────────────────────────────┤
│  Top-level Keys  │  ≤ 50 個                             │
│                  │  防止扁平結構繞過 size 限制造成         │
│                  │  JSONB Index 膨脹                     │
├──────────────────┼──────────────────────────────────────┤
│  巢狀深度         │  ≤ 5 層                              │
│                  │  防止 deeply-nested JSON 造成解析時     │
│                  │  Stack Overflow 或 OOM                │
└──────────────────┴──────────────────────────────────────┘
```

#### 設計細節
*   **提前佈防**：註解明確指出「目前沒有任何 API 寫入 config」，但 Entity 已暴露 setter。這是**前瞻性防禦**——未來新增 API 時，此驗證會自動把關。
*   **早退優化**：`depthOf()` 方法中，若 `current > MAX_DEPTH + 1` 直接返回，不必繼續遞迴，避免效能浪費。
*   **純靜態工具**：無狀態、無依賴注入，可隨處呼叫，零副作用。

---

## 三、兩者的協同關係：防護層級的互補

這兩個 Validator 在系統防護體系中處於完全不同的時間點與層級：

```
                    系統生命週期
    ─────────────────────────────────────────►

    [ 啟動期 ]                          [ 執行期 ]
        │                                    │
        ▼                                    ▼
┌──────────────────────┐          ┌──────────────────────┐
│ TenantConsistency    │          │ TenantConfig         │
│ Validator            │          │ Validator            │
│                      │          │                      │
│ • 檢查「架構契約」    │          │ • 檢查「資料邊界」    │
│ • 防資料外洩(結構性)  │          │ • 防資源耗盡(輸入性)  │
│ • 違反 → 啟動失敗     │          │ • 違反 → HTTP 400    │
│ • 對象：Repository   │          │ • 對象：JSON Payload │
└──────────────────────┘          └──────────────────────┘
```

### 總結

*   **`TenantConsistencyValidator`** 確保的是：**「所有該被隔離的資料通道，都正確接上了隔離閘門」**。它防止的是開發者的人為疏漏導致的結構性安全漏洞。
*   **`TenantConfigValidator`** 確保的是：**「即使資料通道正確，流入的資料也不能是炸彈」**。它防止的是惡意或異常輸入導致的資源耗盡攻擊。

兩者共同體現了 **「Fail-Fast + Defense in Depth」** 的企業級安全設計哲學。