基於現有設計與討論結論，提出以下實作階段計畫：

---

## Phase 1：核心實體（Entity Only）— 1-2 天

將現有 device 的程式碼轉換為通用 IoT 設備管理，**只動 Entity 與 Migration**。

### 1.1 保留不變的 Entity

| Entity | 理由 |
|--------|------|
| `Device` | 坐標、父子階層、JSONB attributes — 已夠通用 |
| `DeviceTemplate` | JSON Schema 驅動動態欄位 — 核心設計 |
| `DeviceEvent` | 事件記錄 — 只需將 event_type 改為 String |
| `DeviceManager` | 責任人指派 — 完全通用 |

### 1.2 微調的 Entity

```diff
// Device.java
- @Enumerated(EnumType.STRING)
- private DeviceType deviceType;
+ private String deviceType;   // 從 Enum 改為 String

- private Long contractId;     // 過渡保留
+ // 保留 circuitId, contractId 作為軟關聯（nullable）
```

```diff
// DeviceEvent.java
- @Enumerated(EnumType.STRING)
- private DeviceEventType eventType;
+ private String eventType;    // 由 device_type 自行定義
```

### 1.3 新增的 Entity

```
WorkOrder.java          # 工單（不含 workflow 整合，第一版純狀態機）
WorkOrderLog.java       # 工單操作日誌（GPS 打卡、狀態變更記錄）
```

### 1.4 刪除的 Enum

```
DeviceType.java         # 改用 String + Template schema
DeviceEventType.java    # 改用 String
```

### 1.5 Migration SQL

```sql
-- Phase 1
ALTER TABLE devices ALTER COLUMN device_type TYPE VARCHAR(30);
ALTER TABLE device_events ALTER COLUMN event_type TYPE VARCHAR(30);

CREATE TABLE work_orders ( ... );     -- 如前所述
CREATE TABLE work_order_logs ( ... );  -- 操作軌跡
```

### 驗收標準
- [ ] `mvn compile` 通過
- [ ] Flyway migration 執行成功
- [ ] 舊資料的 `device_type` 值（POLE, LUMINAIRE…）相容

---

## Phase 2：Repository + Enum 簡化 — 1 天

### 2.1 調整 Repository

```java
// DeviceRepository.java
// 將 DeviceType 參數改為 String
Page<Device> findByFilters(String deviceType, DeviceStatus status,
                           String keyword, List<Long> deptIds, Pageable pageable);
```

### 2.2 保留的 Enum，調整內容

```java
// DeviceStatus.java — 保留，內容保持不變
ACTIVE, INACTIVE, DECOMMISSIONED

// ConnectivityType.java — 保留，擴充通用選項
NONE, DIRECT, GATEWAY, WIFI, LORAWAN, NB_IOT, LTE

// ContractStatus.java — 保留

// WorkOrderStatus.java — 新增
PENDING, ASSIGNED, IN_PROGRESS, REVIEWING, COMPLETED, REJECTED, CLOSED

// WorkOrderSourceType.java — 新增
CITIZEN, AUTO, SYSTEM, PATROL, INSPECTION
```

### 驗收標準
- [ ] 所有 Repository query 改用 String 正常運作
- [ ] 單元測試通過（現有的 DeviceRepositoryTest）

---

## Phase 3：Service 層 — 2-3 天

這是核心邏輯所在，需要移植 service 的既有程式碼。

### 3.1 DeviceService（主要改動）

```java
// 保留：listDevices(), getById(), create(), update(), delete(), decommission()
// 新增：validateAttributes() — 整合 DeviceTemplate JSON Schema 驗證
// 新增：validateHierarchyDepth() — 最多 4 層
// 新增：getDeviceTree() — 回傳設備組合樹
// 保留：CoordinateService 坐標轉換邏輯
```

**關鍵改動邏輯（`create` 與 `update`）：**

```java
@Transactional
public DeviceResponse create(DeviceRequest request) {
    // 1. 檢查 device_code 唯一性（tenant 內）
    // 2. 載入 DeviceTemplate，驗證 attributes 符合 JSON Schema
    TemplateValidationResult result = templateService.validate(
        request.getDeviceType(), request.getAttributes());
    if (!result.isValid()) {
        throw new BusinessException(ErrorCode.INVALID_DEVICE_ATTRIBUTES,
            result.getErrors());
    }
    // 3. 驗證設備階層深度
    validateHierarchyDepth(request.getParentDeviceId(), 1);
    // 4. 坐標自動填補（若有 CoordinateService）
    // 5. 建立 Device
}
```

### 3.2 WorkOrderService（全新）

```java
// create()         — 從通報建立工單（含 location_snapshot 凍結）
// assign()         — 指派技師
// startWork()      — 技師打卡到場
// complete()       — 技師完成維修（含更換零件記錄）
// submitForReview()— 提交覆核（金額 > 門檻 → 觸發 workflow）
// reject()         — 駁回
// close()          — 結案
// getTimeline()    — 回傳工單 Timeline（for 前端時間軸）
```

### 3.3 DeviceExportService（保留改寫）

```java
// 保留原有 CSV/XLSX 匯出邏輯
// 新增：attributes 欄位自動扁平化為匯出欄位
// 新增：根據 device_type 動態決定匯出欄位
```

### 驗收標準
- [ ] Device CRUD + DeviceTemplate 驗證完整測試通過
- [ ] WorkOrder 狀態機轉換正確（不可跳過狀態）
- [ ] 匯出功能包含 attributes 欄位

---

## Phase 4：Controller + API — 1-2 天

### 4.1 移植 Controller

```java
DeviceController.java           // 保留原有 CRUD，加上 deviceType=String
DeviceTemplateController.java   // 保留不變
WorkOrderController.java        // 新增
```
### 4.2 API 路徑

```
GET    /v1/auth/devices                    # 分頁查詢（deviceType 為 String 參數）
GET    /v1/auth/devices/{id}               # 明細（含 children 組合元件）
POST   /v1/auth/devices                    # 新增（含 attributes JSON Schema 驗證）
PUT    /v1/auth/devices/{id}               # 編輯
DELETE /v1/auth/devices/{id}               # 刪除
POST   /v1/auth/devices/{id}/decommission  # 報廢

GET    /v1/auth/devices/tree               # 設備組合樹

POST   /v1/auth/work-orders                           # 建立工單
GET    /v1/auth/work-orders                           # 分頁查詢
GET    /v1/auth/work-orders/{id}                      # 明細
POST   /v1/auth/work-orders/{id}/assign               # 指派
POST   /v1/auth/work-orders/{id}/start                # 到場打卡
POST   /v1/auth/work-orders/{id}/complete             # 完成維修
POST   /v1/auth/work-orders/{id}/submit-review        # 提交覆核
POST   /v1/auth/work-orders/{id}/approve              # 核准
POST   /v1/auth/work-orders/{id}/reject               # 駁回

GET    /v1/auth/device-templates/{deviceType}/schema   # 取得模板 schema
PUT    /v1/auth/device-templates/{deviceType}/schema   # 更新模板
```

### 4.3 Permission（沿用現有權限命名規則）

```
DEVICE_VIEW, DEVICE_MANAGE
WORK_ORDER_VIEW, WORK_ORDER_MANAGE
DEVICE_TEMPLATE_MANAGE
```

### 驗收標準
- [ ] 所有 API 通過 Postman/curl 測試
- [ ] generate-contract.py 正確產出 TS 合約

---

## Phase 5：Workflow 選擇性整合 — 選配（1 天）

> 僅在 Phase 4 驗證後，確認有「多層簽核」需求時才執行。

### 5.1 WorkOrderService 增加 workflow callback

```java
// 在 submitForReview() 中：
if (wo.getRepairCost() >= EXPENSIVE_THRESHOLD) {
    WorkflowInstance instance = workflowEngine.start(
        "WORK_ORDER_REVIEW",
        wo.getTenantId(),
        Map.of("workOrderId", wo.getId(), "amount", wo.getRepairCost())
    );
    wo.setReviewWorkflowInstanceId(instance.getId());
}

// 新增 callback endpoint
POST /v1/auth/work-orders/callback/workflow
```

### 驗收標準
- [ ] 金額超過門檻時自動啟動 workflow instance
- [ ] workflow 完成後 callback 正確更新工單狀態

---

## Phase 6：知識庫同步 — 0.5 天

> 這是給你自己（專案維護者）的 SOP，不是程式碼變更。

### 6.1 執行分析腳本

```bash
# 重新建立知識庫
python scripts/tree-sitter-analyzer.py
python scripts/phase35-enhancer.py
python scripts/phase5a-constraints.py --db knowledge.db
python scripts/phase5b-semantics.py --db knowledge.db

# 驗證
python scripts/generate-contract.py --module device
# → 確認產生 frontend/src/types/generated/device.contracts.ts
```

### 6.2 補充 `@Schema` 註解

在 DTO 上補上 `@Schema(description=...)`，讓 generate-contract.py 產出含 JSDoc 的 TS 合約：

```java
// DeviceRequest.java
@Schema(description = "設備類型；對應 DeviceTemplate 定義的類型")
private String deviceType;

@Schema(description = "設備自訂屬性；需符合 DeviceTemplate.schema 定義的 JSON Schema")
private Map<String, Object> attributes;
```

### 驗收標準
- [ ] `get_feature_contract('/v1/auth/devices')` 回傳完整 TS Interface
- [ ] `get_code_constraints(module='device')` 列出業務規則

---

## 實作時程總表

| Phase | 內容 | 預估天數 | 依賴 |
|-------|------|---------|------|
| 1 | Entity + Migration | 1-2 | 無 |
| 2 | Repository + Enum | 1 | Phase 1 |
| 3 | Service 層 | 2-3 | Phase 2 |
| 4 | Controller + API | 1-2 | Phase 3 |
| 5 | Workflow 整合（選配） | 1 | Phase 4 |
| 6 | 知識庫同步 | 0.5 | Phase 4 |

**總計**：6-9 工作天（不含 Phase 5 選配則為 5-8 天）

---

