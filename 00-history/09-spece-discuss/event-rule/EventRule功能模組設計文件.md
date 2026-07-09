根據您提供的架構設計文件與 Java 實作程式碼，我為您萃取出 **`event-rule` (事件規則) 功能模組**的完整設計藍圖。

如果說 `telemetry` 是系統的「心臟」，那麼 `event-rule` 就是系統的**「大腦皮層」**。它負責接收核心產生的事件脈搏，進行邏輯判斷，並決定是否要觸發下游的動作（如告警通知）。

---

# 📦 Event Rule 功能模組設計萃取

## 一、 模組定位與核心職責
`event-rule` 是一個**獨立的功能域 (Bounded Context)**。它的核心使命是「訂閱遙測事件、依據結構化規則進行比對、管理觸發狀態，並派發後續動作」。

**核心職責包含：**
1. **事件訂閱**：非同步 (`@Async`) 監聽 `TelemetryReceivedEvent`。
2. **規則求值**：使用安全的「結構化條件樹」對遙測數據進行白名單求值。
3. **狀態管理**：處理去抖動 (`FOR_DURATION`)、邊緣觸發 (`ON_CHANGE`) 與冷卻時間 (`cooldown`)。
4. **動作派發**：命中規則後，透過可插拔的 Action Handler 觸發後續動作（如發送通知）。
5. **規則 CRUD 與驗證**：提供規則的增刪改查，並在建立時透過 Schema 進行欄位白名單驗證。

## 二、 模組邊界與依賴關係
* **依賴方向**：**單向、無環**。
* **依賴誰**：
  * `common` (接收 `TelemetryReceivedEvent`，發布 `RuleTriggeredEvent`)
  * `schema` (透過 `SchemaProviderPort` 讀取欄位定義，用於規則建立時的白名單驗證)
  * `redis` (儲存狀態性規則的即時狀態)
* **被誰依賴**：**無**（它是事件鏈的終點/中繼站，不直接依賴 `telemetry` 或 `notification` 的內部實作）。
* **多租戶隔離**：由於使用 `@Async`，Spring 不會自動繼承 ThreadLocal。模組在 `TelemetryRuleListener` 中**顯式還原 `TenantContext`**，並在 `finally` 中清除，確保多租戶資料絕對隔離。

---

## 三、 核心元件與 Package 設計

### 1. 評估引擎層 (Evaluation)
* **`TelemetryRuleListener`**
  * **職責**：事件入口。使用 `@Async @EventListener` 監聽 `TelemetryReceivedEvent`。
  * **設計亮點**：負責還原 `TenantContext`，並從快取中載入該租戶與設備類型的規則集，逐一交由 `RuleEvaluator` 處理。
* **`RuleEvaluator`**
  * **職責**：單筆規則的評估核心。呼叫 `ConditionEvaluator` 求值，並結合 `RuleStateStore` (Redis) 處理 `FOR_DURATION` / `ON_CHANGE` / `cooldown` 等狀態邏輯。
* **`ConditionEvaluator`**
  * **職責**：條件樹的遞迴求值器。嚴格執行白名單運算子 (`GT`, `LT`, `EQ`) 與邏輯節點 (`AND`, `OR`, `NOT`)。
* **`RuleStateStore`**
  * **職責**：封裝 Redis 操作，管理規則的即時狀態（如：首次滿足時間、上次觸發時間、上次值），並設定 TTL 自動回收。

### 2. 動作派發層 (Action)
* **`RuleActionHandler` (介面)**
  * **職責**：定義動作的擴展點 (`supports`, `execute`)。
* **`NotifyActionHandler`**
  * **職責**：V1 實作。組裝 `RuleTriggeredEvent` 並發布到 `common` 事件匯流排，由 `notification` 模組監聽並發送 Email/站內信。
* **設計亮點**：未來若要新增 Webhook、設備下達指令等，只需新增實作類別，**核心評估引擎完全不需修改** (符合 OCP 原則)。

### 3. 應用服務與快取層 (Service & Cache)
* **`EventRuleService`**
  * **職責**：規則的 CRUD 邏輯。
  * **設計亮點**：在 `create` / `update` 時，會遍歷條件樹的葉節點，呼叫 `SchemaProviderPort` 驗證 `field` 是否真實存在於該 `deviceType` 的 telemetry schema 中，**從源頭阻擋無效規則**。
* **`EventRuleCache`**
  * **職責**：以 `(tenantId, deviceType)` 為 Key 快取啟用的規則集。大幅降低高併發 telemetry 事件到來時的 DB 查詢壓力。規則 CRUD 時主動失效快取。

### 4. 接入控制層 (Controller)
對應提供的兩個 Controller，負責暴露 REST API：
* **`EventRuleController`**
  * **職責**：規則定義的 CRUD。
  * **端點**：`/v1/auth/event-rules` (支援分頁、篩選 `deviceType`/`enabled`)。
  * **權限**：嚴格區分 `EVENT_RULE_VIEW` 與 `EVENT_RULE_MANAGE`。
* **`EventRuleTriggerLogController`**
  * **職責**：觸發記錄的唯讀查詢。
  * **端點**：`/v1/auth/event-rules/{id}/logs` (指定規則) 與 `/v1/auth/event-rules/logs` (全域)。
  * **設計亮點 (`enrichNames`)**：為了避免前端需要進行多次 N+1 查詢，Controller 在回傳前會**批次收集** `ruleId` 和 `deviceId`，一次性從 DB 查出 `ruleName` 和 `deviceName` 並填入 DTO 中，極大化了查詢效能與前端渲染效率。預設查詢時間窗為最近 7 天。

---

## 四、 核心業務邏輯：規則模型與評估引擎

### 1. 結構化條件樹 (Condition Tree)
**絕對不使用 SpEL / JS / Groovy 等可執行腳本**，徹底杜絕 RCE (遠端程式碼執行) 與注入風險。
* **葉節點**：`{ "field": "temperature", "operator": "GT", "value": 75 }`
* **分支節點**：`{ "op": "AND", "children": [...] }` (支援任意深層巢狀)
* **V1 運算子白名單**：`GT` (大於), `LT` (小於), `EQ` (等於)。

### 2. 觸發語意與狀態管理 (Trigger Semantics)
| 模式 | 行為 | 狀態依賴 (Redis) |
| :--- | :--- | :--- |
| **ON_MATCH** | 每筆 telemetry 滿足條件即觸發（受 cooldown 限制）。 | 僅需記錄「上次觸發時間」(Cooldown) |
| **FOR_DURATION** | 條件必須**持續滿足** N 秒才觸發（去除瞬時抖動）。 | 需記錄「首次滿足時間」 |
| **ON_CHANGE** | 條件結果由 `false → true` 的邊緣觸發。 | 需記錄「上次求值結果」或「上次值」 |

---

## 五、 關鍵設計決策總結

1. **安全第一 (Security First)**
   * 捨棄靈活性極高但危險的腳本引擎，採用**結構化 JSON 白名單求值**。這使得規則可以被前端 UI 安全地渲染、編輯，且後端求值器完全可控。
2. **極致的解耦 (Event-Driven Decoupling)**
   * `telemetry` 發事件 → `event-rule` 收事件並處理 → `event-rule` 發事件 → `notification` 收事件並發送。
   * 整個鏈路中，**沒有任何兩個業務模組在編譯期互相依賴**，全部透過 `common/event` 的 Record 進行通訊。
3. **高併發下的效能保障 (Performance)**
   * **規則快取**：`EventRuleCache` 避免每次事件都查 DB。
   * **例外隔離**：`TelemetryRuleListener` 在逐規則評估時採用 best-effort 策略，單一規則拋出異常會被捕獲並記錄，**絕不中斷**其他規則的評估。
   * **批次名稱關聯**：`EventRuleTriggerLogController.enrichNames()` 解決了分頁查詢中的 N+1 關聯名稱痛點。
4. **多實例支援 (Stateful Rules)**
   * 狀態性規則 (`FOR_DURATION` / `ON_CHANGE`) 的狀態不存放在 JVM 記憶體，而是統一交由 **Redis (`RuleStateStore`)** 管理，確保系統水平擴展 (Scale-out) 時狀態不會丢失或錯亂。

## 六、 歸屬資料表 (Database Schema)
根據架構設計，`event-rule` 模組擁有以下資料表的所有權：

| 資料表名稱 | 歸屬模組 | 用途說明 |
| :--- | :--- | :--- |
| `event_rule` | `event-rule.entity` | 規則定義。使用 JSONB 儲存 `condition` (條件樹)、`trigger_cfg` (觸發配置)、`actions` (動作陣列)。 |
| `event_rule_trigger_log` | `event-rule.entity` | 觸發記錄。**僅在條件成立且通過 cooldown/duration 收斂後實際觸發時才寫入**。與 `telemetry_data` 共用時序儲存策略 (Partition + BRIN)。 |