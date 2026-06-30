

以下我將基於你現有的程式碼，提出一個**完整的、可擴充的通用 IoT 設備管理模組架構設計**，並說明如何與你的 CodeCortex 知識庫無縫整合。

---

## 一、現有設計盤點與泛化方向

| 現有元件 | 強項 | 需要泛化的部分 |
|---------|------|--------------|
| `Device` | 坐標、父子階層、JSONB attributes、connectivity 等均為通用 | `deviceType` 應從 Enum 改為 String，由 Template 動態決定；`circuitId`、`contractId` 可改為軟關聯或泛化關聯 |
| `DeviceTemplate` | **完美** — JSON Schema 驅動動態欄位 | 無需修改，只需擴充 device_type 的註冊機制 |
| `DeviceEvent` | 通用事件記錄 | 可將 `eventType` 改為 String，由 device_type 自行定義可用事件 |
| `Circuit` | 明確記錄電力迴路 | 泛化為 `InfrastructureChannel`，支援電力、網路、水管等各種通道 |
| `Contract` | 記錄採購/保固合約 | 泛化為 `LifecycleDocument`，支援採購、保固、維護、租賃等各種文件 |
| `DeviceManager` | 人員指派 | 完全通用，保留 |
| 控制器/服務 | 已有 CRUD 與匯出功能 | 需支援「根據 device_type 動態渲染表單」的 API |

---

## 二、推薦的泛化實體模型（PostgreSQL + JSONB）

### 1. 核心設備表 `devices`（微調即可）

```sql
-- 僅調整 device_type 為 VARCHAR，移除 Enum 約束
ALTER TABLE devices ALTER COLUMN device_type TYPE VARCHAR(30);
ALTER TABLE devices DROP CONSTRAINT devices_device_type_check;  -- 若有

-- 新增通用關聯欄位（可空，不強制）
ALTER TABLE devices ADD COLUMN channel_id BIGINT;        -- 指向 infrastructure_channels
ALTER TABLE devices ADD COLUMN document_id BIGINT;       -- 指向 lifecycle_documents
-- 保留原有 contract_id 作為過渡，後續可棄用
```

### 2. 基礎設施通道表 `infrastructure_channels`（取代 `circuits`）

```sql
CREATE TABLE infrastructure_channels (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    channel_type VARCHAR(30) NOT NULL,   -- POWER, NETWORK, WATER, GAS, ...
    channel_code VARCHAR(50) NOT NULL,
    channel_name VARCHAR(200),
    provider VARCHAR(100),               -- 台電、中華電信、自來水公司...
    provider_account VARCHAR(50),
    attributes JSONB,                    -- 存放電壓等級、頻寬、管徑等
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);
```

### 3. 生命週期文件表 `lifecycle_documents`（取代 `contracts`）

```sql
CREATE TABLE lifecycle_documents (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    doc_type VARCHAR(30) NOT NULL,       -- PROCUREMENT, WARRANTY, MAINTENANCE, LEASE, ...
    doc_code VARCHAR(100) NOT NULL,
    doc_name VARCHAR(300) NOT NULL,
    parties JSONB,                       -- 彈性儲存合約方資訊 [{role: "supplier", name: "..."}]
    start_date DATE,
    end_date DATE,
    warranty_years INT,
    attributes JSONB,                    -- 其他擴充欄位
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);
```

### 4. 設備模板表 `device_templates`（保持原樣，擴充版本控制）

```sql
-- 已有，建議加上唯一約束 (tenant_id, device_type) 確保單一模板
-- 並增加 ui_schema 欄位，用於前端動態表單布局
ALTER TABLE device_templates ADD COLUMN ui_schema JSONB;
```

### 5. 事件表 `device_events`（微調）

```sql
-- 將 event_type 改為 VARCHAR，由各 device_type 自行定義
ALTER TABLE device_events ALTER COLUMN event_type TYPE VARCHAR(30);
```

### 6. 工單表 `work_orders`（新增，補齊維護流程）

這是台北市路燈平台的核心需求，也是通用設備管理必備。

```sql
CREATE TABLE work_orders (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(50) NOT NULL,
    device_id BIGINT REFERENCES devices(id),
    channel_id BIGINT REFERENCES infrastructure_channels(id), -- 可針對通道進行工作
    order_type VARCHAR(20) NOT NULL,   -- REPAIR, RENEWAL, INSPECTION, INSTALL, DECOMMISSION
    source_type VARCHAR(20) NOT NULL,  -- CITIZEN, AUTO, SYSTEM, PATROL
    status VARCHAR(20) NOT NULL,       -- PENDING, ASSIGNED, IN_PROGRESS, REVIEWING, REJECTED, COMPLETED, CLOSED
    priority VARCHAR(10),              -- HIGH, MEDIUM, LOW
    reporter_name VARCHAR(100),
    reporter_contact VARCHAR(100),
    reported_at TIMESTAMP,
    description TEXT,
    assigned_to VARCHAR(50),
    assigned_at TIMESTAMP,
    reviewer_id VARCHAR(50),
    reviewed_at TIMESTAMP,
    reject_reason TEXT,
    fault_cause VARCHAR(100),
    completed_at TIMESTAMP,
    completion_remark TEXT,
    attachments JSONB,                 -- 照片、影片等
    auto_reported_at TIMESTAMP,        -- 智能通報時間，用於比對
    created_by VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- 索引
CREATE INDEX idx_wo_device ON work_orders(device_id);
CREATE INDEX idx_wo_status_created ON work_orders(status, created_at);
CREATE INDEX idx_wo_source ON work_orders(source_type);
```

### 7. 庫存管理 `materials` 與 `inventory_transactions`（如前述）

此為選配，但若要支援換裝維護，必不可少。

---

## 三、模組目錄結構（Java/Spring Boot 風格）

```
device-management/
├── controller/
│   ├── DeviceController.java           // CRUD + 查詢 + 匯出
│   ├── DeviceTemplateController.java   // 模板管理
│   ├── WorkOrderController.java        // 工單生命週期
│   ├── ChannelController.java          // 基礎設施通道
│   ├── DocumentController.java         // 生命週期文件
│   └── InventoryController.java        // 庫存管理
├── service/
│   ├── DeviceService.java              // 設備業務邏輯（含模板驗證）
│   ├── DeviceExportService.java        // CSV/XLSX 匯出
│   ├── TemplateValidationService.java  // JSON Schema 驗證
│   ├── WorkOrderService.java           // 工單流轉
│   ├── ChannelService.java
│   ├── DocumentService.java
│   └── InventoryService.java
├── repository/
│   ├── DeviceRepository.java
│   ├── DeviceTemplateRepository.java
│   ├── WorkOrderRepository.java
│   └── ...
├── dto/
│   ├── DeviceRequest.java              // 接收 Device 資料，含 attributes Map
│   ├── DeviceResponse.java             // 返回 Device 資料
│   ├── DeviceTemplateRequest.java
│   ├── WorkOrderRequest.java
│   └── ...
├── entity/
│   ├── Device.java
│   ├── DeviceTemplate.java
│   ├── InfrastructureChannel.java
│   ├── LifecycleDocument.java
│   ├── WorkOrder.java
│   ├── Material.java
│   └── InventoryTransaction.java
├── enums/ (僅保留真正通用的枚舉)
│   ├── DeviceStatus.java              // ACTIVE, INACTIVE, DECOMMISSIONED
│   ├── WorkOrderStatus.java
│   ├── ChannelType.java
│   ├── DocumentType.java
│   └── ConnectivityType.java          // WIFI, LORAWAN, NB-IoT, etc.
├── config/
│   ├── TenantInterceptor.java          // 多租戶過濾
│   ├── JsonSchemaValidator.java        // 初始化 JSON Schema 驗證器
│   └── WebMvcConfig.java
└── util/
    ├── GeometryUtil.java               // 坐標轉換 (TWD97 ↔ WGS84)
    └── ExportUtil.java
```

---

## 四、動態設備類型的運作流程

### 1. 定義模板（範例：智慧路燈）

```json
POST /api/templates
{
  "deviceType": "STREET_LIGHT",
  "schema": {
    "type": "object",
    "properties": {
      "height": { "type": "number", "minimum": 3, "maximum": 15 },
      "lampWatt": { "type": "integer", "enum": [50, 100, 150] },
      "colorTemp": { "type": "integer", "enum": [3000, 4000, 5000] }
    },
    "required": ["height", "lampWatt"]
  },
  "uiSchema": {
    "height": { "ui:widget": "range", "ui:min": 3, "ui:max": 15 },
    "lampWatt": { "ui:widget": "select" }
  }
}
```

### 2. 創建設備（自動驗證）

```json
POST /api/devices
{
  "deviceType": "STREET_LIGHT",
  "deviceCode": "SL-001",
  "deviceName": "忠孝東路一段1號燈",
  "attributes": {
    "height": 8.5,
    "lampWatt": 100,
    "colorTemp": 4000
  }
}
```

後端 `DeviceService` 會根據 `deviceType` 載入對應模板的 JSON Schema，使用 `everit-json-schema` 或 `networknt/json-schema-validator` 進行校驗，失敗則拋出 `BadRequestException`。

### 3. 前端動態表單渲染

前端調用 `GET /api/templates/{deviceType}/ui-schema`，取得 `uiSchema` 後用 React JSON Schema Form 等元件自動生成表單，完全不需要前後端約定新欄位。

---

## 五、與 CodeCortex 知識庫的整合點

你的 CodeCortex 知識庫（從歷史對話推測，是一個用於記錄業務規則、測試契約、API 合約的系統）可以與此設備管理模組深度結合：

| 知識庫功能 | 對應設備管理模組的應用 |
|-----------|----------------------|
| **API 契約生成** | 根據 `DeviceTemplate.schema` 自動生成 OpenAPI 文件，描述 `attributes` 的結構。 |
| **前端型別生成** | 透過 MCP tool `get_feature_contract('/v1/devices')` 輸出 `DeviceRequest` 的 TypeScript 介面，含 `attributes` 的具體型別（根據該租戶的模板動態推導）。 |
| **業務規則提取** | `DeviceService` 中的約束（如「已報廢設備不可編輯」、「工單完成後自動更新設備狀態」）可被掃描並記錄為 `code_constraints`，納入知識庫。 |
| **測試案例生成** | 基於模板 schema 自動生成邊界值測試案例（如 height 超出範圍、缺少必填欄位），儲存為 `test_rules`。 |
| **合規性檢查** | 可將台北市路燈平台的績效指標（如「智能通報不得晚於民眾通報」）轉化為監控規則，定期檢查 `work_orders` 數據，產出報告並自動記錄到知識庫。 |

具體實作方式：
- 在 `DeviceService` 的 CRUD 方法中埋入 `@BusinessRule` 註解，由 AOP 攔截後發送事件給 CodeCortex 收集器。
- 提供一個管理端 API，允許知識庫查詢當前所有 `DeviceTemplate` 的 schema，以便生成前後端契約。

---

## 六、遷移路徑建議

1. **保留現有 `Circuit` 和 `Contract` 表**，新增 `infrastructure_channels` 和 `lifecycle_documents` 表，資料遷移時一一對應。
2. **逐步將 `device_type` 從 Enum 改為 String**，建立一個 `DeviceTypeRegistry` 服務，從資料庫或配置中心讀取所有已註冊的類型（即 `device_templates` 中存在的類型）。
3. **先實作核心 CRUD 與模板驗證**，再導入工單與庫存，最後才整合 CodeCortex。
4. **善用現有 `DeviceExportService`**，泛化其匯出邏輯，支援任意 JSONB 欄位的扁平化匯出（如將 attributes 中的欄位展開為獨立列）。

---

## 七、總結

你的設計已經立於不敗之地 — 抽象層級適當，擴充性強。只要將 `Circuit` 和 `Contract` 適度泛化為通道與文件，並引入工單與庫存管理，即可構成一個能涵蓋路燈、水錶、號誌、感測器等各類設備的**通用 IoT 設備管理平台**。

而 CodeCortex 知識庫的整合，則能讓這個平台不僅是 CRUD 工具，更成為**智慧合約驅動的自動化運維中樞**。

