# 設備匯入功能設計

> 版本：v1
> 日期：2026-07-01
> 狀態：設計確認

---

## 1. 概述

提供用戶透過 Excel (.xlsx) 或 CSV 檔案批次匯入設備資料的功能，減少逐筆新增耗時，並確保資料品質。

### 使用情境

- 既有設備資料首次導入系統（初期上線／異質系統搬遷）
- 持續性大批量新增設備（如新建標案交貨後一次性匯入）
- 離線填寫後批量上傳

---

## 2. 需求摘要

| # | 需求 | 說明 |
|---|------|------|
| R1 | 支援 Excel (.xlsx) 與 CSV 格式 | 以 Apache POI + commons-csv 解析 |
| R2 | 同步處理 | 上傳後即時回傳結果 |
| R3 | 可設定筆數上限 | 預設 500 筆，寫入設定檔，不 hardcode |
| R4 | 先驗證全部 → 一次回報 | 遍歷所有列收集錯誤，有錯整批退回 |
| R5 | 欄位對應查詢 | 部門名稱 → dept_id、合約名稱 → contract_id、父設備代碼 → parent_device_id、迴路編號 → circuit_id |
| R6 | 設備類型驗證 | device_type 須存在於 device_template |
| R7 | 租戶繼承 | tenant_id 從 JWT 取得，不寫在檔案中 |
| R8 | 不包含 attributes (JSONB) | 只匯入設備基本欄位 |
| R9 | 下載匯入範本 | 提供標題列範例檔案 |
| R10 | 下載錯誤報告 | 失敗時可匯出含錯誤說明的 CSV |
| R11 | 泛型架構（未來擴充） | 第一版實作 DeviceImportStrategy，保留 Contract/Circuit 匯入擴充點 |

---

## 3. 架構設計

### 3.1 架構圖

```
┌──────────────────────────────────────────────────────────────────┐
│  POST /v1/auth/devices/import (MultipartFile)                    │
│  ImportController                                                 │
└──────────────────────┬───────────────────────────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────────────────────────┐
│  ImportOrchestrator (泛型引擎入口)                                │
│                                                                    │
│  parseAndValidate(file, strategy) → ImportResult<T>               │
│    │  ① 格式檢查（副檔名、MIME、大小）                             │
│    │  ② FileParser 解析成 List<Map<String, String>>              │
│    │  ③ 逐列執行 strategy.mapToDto(row) → T                      │
│    │  ④ 逐列執行 strategy.validate(dto, rowNum) → List<Error>    │
│    │  ⑤ 有 error → 回傳 ImportResult(errors)                     │
│    │     無 error → 回傳 ImportResult(rows)                       │
│                                                                    │
│  execute(result, strategy) → ImportResponse                       │
│    │  ① strategy.beforeAll(rows)                                  │
│    │  ② strategy.saveAll(rows)  (單一 @Transactional)             │
│    │  ③ 回傳 ImportResponse                                       │
└──────┬───────────────────────────────────────────────────────────┘
       │
       ├─────────────────────┬──────────────────────┐
       ▼                     ▼                      ▼
┌──────────────┐   ┌──────────────────┐   ┌──────────────────┐
│ FileParser   │   │ ImportStrategy   │   │ ImportResult     │
│ (factory)    │   │ (interface)      │   │ (entity)         │
├──────────────┤   ├──────────────────┤   ├──────────────────┤
│ ExcelParser  │   │ DeviceStrategy   │   │ 總筆數、錯誤清單  │
│ CsvParser    │   │ ContractStrategy │   │ 每筆：row field   │
│ (統一介面)    │   │ CircuitStrategy  │   │       value msg  │
└──────────────┘   └──────────────────┘   └──────────────────┘
```

### 3.2 模組位置

```
backend/src/main/java/com/taipei/iot/import_/
├── ImportOrchestrator.java          # 泛型匯入引擎
├── ImportStrategy.java              # 策略介面
├── ImportResult.java                # 驗證結果
├── ImportResponse.java              # API 回應 DTO
├── ImportError.java                 # 單筆錯誤 DTO
├── config/
│   └── ImportProperties.java        # @ConfigurationProperties(prefix="import")
├── parser/
│   ├── FileParser.java              # 解析器介面
│   ├── FileParserFactory.java       # 依副檔名回傳對應 parser
│   ├── ExcelParser.java             # .xlsx 解析 (Apache POI)
│   └── CsvParser.java               # .csv 解析 (commons-csv)
├── device/
│   ├── DeviceImportController.java  # /v1/auth/devices/import
│   └── DeviceImportStrategy.java    # Device 策略實作
```

**為什麼採用新 package `import_` 而非放入 `device` 模組？**
- 泛型引擎 `ImportOrchestrator`、`FileParser` 為跨模組共用元件，不應放在 device 底下
- 未來合約、迴路匯入只需在各自的模組放 Strategy + Controller
- import 是 Java 保留字，以 `import_` 規避

### 3.3 泛型策略介面

```java
public interface ImportStrategy<T> {

    /** 實體類型名稱（用於日誌、錯誤訊息） */
    String getEntityType();

    /** 預期的 excel/csv 標題列（用於比對欄位是否正確） */
    Set<String> expectedHeaders();

    /** 將一列原始資料 Map<欄位名, 值> 映射為 DTO */
    T mapToDto(Map<String, String> row);

    /** 逐筆驗證 DTO（必填、格式、關聯查詢），回傳錯誤清單 */
    List<ImportError> validate(T dto, int rowNum);

    /** 批量寫入前的批次驗證（如 device_code 租戶內唯一） */
    List<ImportError> batchValidate(List<T> dtos);

    /** 批量寫入（單一 transaction） */
    void saveAll(List<T> rows);

    /** 寫入前預處理（可選，預設空實作） */
    default void beforeAll(List<T> rows) {}

    /** 寫入後後處理（可選，預設空實作） */
    default void afterAll(List<T> rows) {}
}
```

---

## 4. DeviceImportStrategy 實作細節

### 4.1 匯入範本欄位

| 欄位 | 中文 | 必填 | 型別 | 驗證規則 |
|---|---|---|---|---|
| `device_type` | 設備類型 | ✅ | 字串 | 須存在於 `device_template` |
| `device_code` | 設備代碼 | ✅ | 字串 | 租戶內唯一；長度 ≤ 100 |
| `device_name` | 設備名稱 | | 字串 | 長度 ≤ 200 |
| `twd97_x` | TWD97 X | | 數值 | 精度 12,3 |
| `twd97_y` | TWD97 Y | | 數值 | 精度 12,3 |
| `lng` | 經度 | | 數值 | 精度 11,7 |
| `lat` | 緯度 | | 數值 | 精度 10,7 |
| `elevation` | 海拔高度 | | 數值 | 精度 8,3 |
| `dept_name` | 所屬部門 | | 字串 | 依名稱查 `dept_info` |
| `contract_name` | 標案合約 | | 字串 | 依名稱查 `contracts` |
| `property_owner` | 財產所有人 | | 字串 | 長度 ≤ 200 |
| `installed_at` | 安裝日期 | | 日期 | YYYY-MM-DD 格式 |
| `parent_device_code` | 父設備代碼 | | 字串 | 依代碼查同租戶設備 |
| `mount_position` | 掛載位置 | | 字串 | 長度 ≤ 50 |
| `connectivity_type` | 連線方式 | | 列舉 | `WIRED`/`WIFI`/`4G`/`NB_IOT`/`LORA`/`OTHER` |
| `circuit_number` | 迴路編號 | | 字串 | 依編號查 `circuits` |

### 4.2 設備匯入流程

```
開始
  │
  ▼
檢查檔案格式 & 大小
  │
  ▼
解析為 List<Map<String, String>> rawRows
  │
  ▼
檢查總筆數 ≤ maxRows (預設 500)
  │
  ▼
for each rawRow:
    mapToDto(rawRow) → DeviceImportRow
    validate(dto, rowNum) → List<ImportError>   ← 逐筆個別驗證
    (必填、格式、device_type、dept_name、connectivity_type)
  │
  ▼
batchValidate(dtos) → List<ImportError>         ← 批次關聯驗證
  │  (device_code 租戶內重複、parent_device_code 存在、
  │   contract_name 存在、circuit_number 存在)
  │
  ▼
errors.isEmpty()?
  ├→ NO   → 回傳 400 + ImportResult(errors)
  └→ YES  → 開始匯入
                ↓
             saveAll(dtos)  (單一 @Transactional)
                ↓
             回傳 200 + ImportResponse(successCount)
```

### 4.3 DeviceImportRow DTO

```java
public class DeviceImportRow {
    private String deviceType;          // 驗證存在
    private String deviceCode;          // 驗證唯一
    private String deviceName;
    private BigDecimal twd97X;
    private BigDecimal twd97Y;
    private BigDecimal lng;
    private BigDecimal lat;
    private BigDecimal elevation;
    private String deptName;            // → 解析為 deptId
    private String contractName;        // → 解析為 contractId
    private String propertyOwner;
    private LocalDate installedAt;
    private String parentDeviceCode;    // → 解析為 parentDeviceId
    private String mountPosition;
    private ConnectivityType connectivityType;
    private String circuitNumber;       // → 解析為 circuitId
}
```

### 4.4 saveAll 實作

```java
@Transactional
public void saveAll(List<DeviceImportRow> rows) {
    String tenantId = TenantContext.getCurrentTenantId();

    // 批次查詢關聯資料
    Map<String, Long> deptMap = loadDeptMap(rows);
    Map<String, Long> contractMap = loadContractMap(rows);
    Map<String, Long> circuitMap = loadCircuitMap(rows);
    Map<String, Long> parentDeviceMap = loadParentDeviceMap(tenantId, rows);

    List<Device> devices = rows.stream()
        .map(row -> Device.builder()
            .tenantId(tenantId)
            .deviceType(row.getDeviceType())
            .deviceCode(row.getDeviceCode())
            .deviceName(row.getDeviceName())
            .twd97X(row.getTwd97X())
            .twd97Y(row.getTwd97Y())
            .lng(row.getLng())
            .lat(row.getLat())
            .elevation(row.getElevation())
            .deptId(deptMap.get(row.getDeptName()))
            .contractId(contractMap.get(row.getContractName()))
            .propertyOwner(row.getPropertyOwner())
            .status(DeviceStatus.ACTIVE)
            .installedAt(row.getInstalledAt())
            .parentDeviceId(parentDeviceMap.get(row.getParentDeviceCode()))
            .mountPosition(row.getMountPosition())
            .connectivityType(row.getConnectivityType())
            .circuitId(circuitMap.get(row.getCircuitNumber()))
            .build())
        .toList();

    deviceRepository.saveAll(devices);
}
```

---

## 5. API 規格

### 5.1 設備匯入

```
POST /v1/auth/devices/import
Content-Type: multipart/form-data

Parameters:
  file: MultipartFile (.xlsx or .csv)
```

**Response 200 — 全部成功**
```json
{
  "errorCode": "00000",
  "errorMsg": "操作成功",
  "timestamp": 1719000000,
  "body": {
    "entityType": "device",
    "totalRows": 50,
    "successCount": 50,
    "errors": []
  }
}
```

**Response 400 — 有驗證錯誤，整批退回**
```json
{
  "errorCode": "DEVICE_IMPORT_VALIDATION_FAILED",
  "errorMsg": "部分資料驗證未通過，共 3 筆錯誤，請修正後重新上傳",
  "timestamp": 1719000000,
  "body": null,
  "errors": [
    { "row": 3,  "field": "device_code",   "value": "",                 "message": "device_code 為必填" },
    { "row": 5,  "field": "device_type",   "value": "UNKNOWN_TYPE",     "message": "設備類型 UNKNOWN_TYPE 不存在於 DeviceTemplate" },
    { "row": 7,  "field": "device_code",   "value": "SL-001",           "message": "設備代碼 SL-001 已存在於此租戶" },
    { "row": 10, "field": "dept_name",     "value": "不存在部門",       "message": "部門名稱「不存在部門」對應不到任何部門" },
    { "row": 12, "field": "installed_at",  "value": "2026/01/01",        "message": "安裝日期格式應為 YYYY-MM-DD" },
    { "row": 15, "field": "connectivity_type", "value": "SATELITE",     "message": "連線方式 SATELITE 不在允許值中 (WIRED, WIFI, 4G, NB_IOT, LORA, OTHER)" }
  ]
}
```

**Response 400 — 檔案格式問題**
```json
{
  "errorCode": "DEVICE_IMPORT_MAX_ROWS_EXCEEDED",
  "errorMsg": "匯入筆數 600 超過單次上限 500 筆",
  "timestamp": 1719000000,
  "body": null
}
```

### 5.2 下載匯入範本

```
GET /v1/auth/devices/import/template
Accept: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet  (default)
         or text/csv
```

回傳含標題列的 `.xlsx` 或 `.csv` 檔案（無資料列，僅欄位定義）。

### 5.3 下載錯誤報告

```
POST /v1/auth/devices/import/error-report
Content-Type: application/json

Request:
{
  "originalFileName": "devices.xlsx",
  "headers": ["device_type", "device_code", ...],
  "rows": [["STREET_LIGHT", "SL-001", ...], ...],
  "errors": [...]
}
```

用戶端在收到 400 回應後，將前端暫存的原始資料 + errors 一併 POST 至後端，後端產生含錯誤說明的 CSV 檔回傳。

回傳 CSV 格式：
```
列,欄位,原始值,錯誤說明
3,device_code,,device_code 為必填
5,device_type,UNKNOWN_TYPE,設備類型 UNKNOWN_TYPE 不存在於 DeviceTemplate
```

> **注意**：此 endpoint 為 stateless 工具型 API，僅將用戶端提供的資料包裝為 CSV 下載，不儲存任何內容。

---

## 6. 驗證規則細項

### 6.1 單列驗證 (validate)

| 規則 | 條件 | 錯誤訊息範例 |
|---|---|---|
| device_code 必填 | 空值或空白 | `device_code 為必填` |
| device_code 長度 | > 100 字元 | `device_code 長度不得超過 100` |
| device_name 長度 | > 200 字元 | `device_name 長度不得超過 200` |
| device_type 必填 | 空值 | `device_type 為必填` |
| device_type 存在 | 不在 device_template 中 | `設備類型 XXX 不存在於 DeviceTemplate` |
| installed_at 格式 | 非 YYYY-MM-DD 或無效日期 | `安裝日期格式應為 YYYY-MM-DD` |
| connectivity_type 合法值 | 非 enum 允許值 | `連線方式 XXX 不在允許值中` |
| 座標格式 | 數值無法解析 | `twd97_x 必須為數值` |
| dept_name | 空白則跳過（可為 null） | — |

> **關於名稱模糊比對**：`dept_name`、`contract_name`、`circuit_number` 在單一租戶內原則上唯一，但若同名部門/同編號迴路存在複數筆，會視為驗證錯誤並提示用戶提供更精確的查詢條件。

### 6.2 批次驗證 (batchValidate)

| 規則 | 條件 | 錯誤訊息範例 |
|---|---|---|
| device_code 租戶內唯一 | 與資料庫既有值重複 | `設備代碼 XXX 已存在於此租戶` |
| device_code 檔案內重複 | 同一次匯入有多筆相同代碼 | `設備代碼 XXX 在檔案中重複（第 3、7 列）` |
| parent_device_code 存在 | 對應代碼在同租戶中不存在 | `父設備代碼 XXX 不存在` |
| parent_device_code 非自身 | 填入自己當父設備 | `父設備代碼 XXX 與自身 device_code 相同` |
| contract_name 存在 | 契約名稱查不到 | `合約名稱 XXX 對應不到任何契約` |
| circuit_number 存在 | 迴路編號查不到 | `迴路編號 XXX 對應不到任何迴路` |

---

## 7. 設定檔

```yaml
# application.yml
import:
  max-rows: 500                    # 單次匯入最大筆數
  max-file-size: 10MB              # 上傳檔案大小上限
  allowed-extensions: xlsx,csv     # 允許的副檔名
```

`ImportProperties.java` 使用 `@ConfigurationProperties(prefix = "import")` 繫結此設定。

---

## 8. 前端設計

### 8.1 匯入按鈕

在 `DeviceListView.vue` 工具列新增「匯入」按鈕，位於「新增設備」旁。

```vue
<template>
  <div class="d-flex ga-2">
    <v-btn color="primary" prepend-icon="mdi-plus" @click="showCreateDialog">
      新增設備
    </v-btn>
    <v-btn variant="tonal" prepend-icon="mdi-file-upload-outline" @click="showImportDialog">
      匯入
    </v-btn>
  </div>
</template>
```

### 8.2 匯入對話框 (ImportDialog.vue)

**狀態一：選擇檔案**
```
┌──────────────────────────────────────────────┐
│  匯入設備                                    │
│                                              │
│  ┌────────────────────────────────────────┐  │
│  │  🔲 拖曳檔案至此處，或點擊選擇檔案     │  │
│  │                                        │  │
│  │  [選擇檔案] ✅ devices.xlsx (3.2 KB)   │  │
│  │  支援格式：.xlsx, .csv                 │  │
│  │  單次最多 500 筆                       │  │
│  │  [📥 下載匯入範本]                     │  │
│  └────────────────────────────────────────┘  │
│                                              │
│         [取消]              [開始匯入]       │
└──────────────────────────────────────────────┘
```

**狀態二：匯入中**
```
┌──────────────────────────────────────────────┐
│  匯入設備                                    │
│                                              │
│  ⟳ 正在驗證資料，請稍候...                  │
│  [████████░░░░░░░░░░░░] 45%                 │
│                                              │
└──────────────────────────────────────────────┘
```

**狀態三：成功**
```
┌──────────────────────────────────────────────┐
│  ✅ 成功匯入 50 筆設備                       │
│                                              │
│  [確認]                                      │
└──────────────────────────────────────────────┘
```

**狀態四：失敗（錯誤清單）**
```
┌──────────────────────────────────────────────┐
│  ⚠ 資料驗證未通過，共 3 筆錯誤              │
│  請修正後重新上傳                            │
│                                              │
│  [📥 下載錯誤報告]     [關閉]               │
│                                              │
│  ┌────┬─────────────┬──────────────────────┐ │
│  │ 列 │ 欄位        │ 錯誤說明             │ │
│  ├────┼─────────────┼──────────────────────┤ │
│  │ 3  │ device_code │ device_code 為必填   │ │
│  │ 7  │ device_type │ 設備類型不存在       │ │
│  │ 10 │ dept_name   │ 部門名稱對應不到     │ │
│  └────┴─────────────┴──────────────────────┘ │
└──────────────────────────────────────────────┘
```

### 8.3 前端 API

```typescript
// src/api/device/index.ts 新增

export const importDevices = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return axiosIns.post<unknown, BaseResponse<ImportResponse>>(
    '/auth/devices/import',
    formData,
    { headers: { 'Content-Type': 'multipart/form-data' } }
  )
}

export const downloadImportTemplate = (format: 'xlsx' | 'csv' = 'xlsx') => {
  return axiosIns.get('/auth/devices/import/template', {
    params: { format },
    responseType: 'blob',
  })
}

export const downloadErrorReport = (payload: {
  originalFileName: string
  headers: string[]
  rows: string[][]
  errors: ImportError[]
}) => {
  return axiosIns.post('/auth/devices/import/error-report', payload, {
    responseType: 'blob',
  })
}
```

### 8.4 Frontend Types

```typescript
// src/types/device.ts 新增

export interface ImportError {
  row: number
  field: string
  value: string
  message: string
}

export interface ImportResponse {
  entityType: string
  totalRows: number
  successCount: number
  errors: ImportError[]
}
```

---

## 9. ErrorCode 新增

```java
// ErrorCode.java 新增
DEVICE_IMPORT_FILE_EMPTY("A200", 400, "上傳檔案為空"),
DEVICE_IMPORT_FILE_FORMAT("A201", 400, "不支援的檔案格式，僅允許 .xlsx 與 .csv"),
DEVICE_IMPORT_MAX_ROWS_EXCEEDED("A202", 400, "匯入筆數超過單次上限"),
DEVICE_IMPORT_VALIDATION_FAILED("A203", 400, "部分資料驗證未通過"),
DEVICE_IMPORT_HEADER_MISMATCH("A204", 400, "檔案標題列與範本不符"),
```

---

## 10. 安全考量

| 面向 | 措施 |
|---|---|
| 檔案上傳 | 驗證副檔名 + MIME type，限制大小（預設 10MB） |
| SQL Injection | 使用 JPA Repository，不拼接 SQL |
| 租戶隔離 | TenantContext 已由 JWT Filter 設定，所有查詢含租戶過濾 |
| 權限 | `@PreAuthorize("hasAuthority('DEVICE_MANAGE')")` |
| CSV Injection | CSV 輸出時對 `=`, `+`, `-`, `@` 前綴加 prefix 防禦 |

---

## 11. 測試策略

| 層級 | 涵蓋範圍 |
|---|---|
| 單元測試 (DeviceImportStrategyTest) | 各種驗證規則、mapToDto 對映、邊界值 |
| 單元測試 (FileParserTest) | ExcelParser / CsvParser 解析正確性、標題列檢核 |
| 單元測試 (ImportOrchestratorTest) | 空檔案、格式錯誤、筆數超限、整批退回流程 |
| 整合測試 (DeviceImportControllerTest) | mockMultipartFile 上傳、正確回應碼與 body |
| 參數化測試 | 各種驗證錯誤組合（一筆多錯、多筆單錯、混合） |

---

## 12. 未來擴充 (Out of Scope)

| 項目 | 備註 |
|---|---|
| Contract / Circuit 匯入 | Strategy 介面已預留，只需新增實作 |
| 匯入 preview（先暫存再確認） | 需新增 staging table + 前端 preview UI |
| 非同步大檔案匯入 | 超過同步上限時改為排程處理 + WebSocket 通知 |
| 匯入結果 Email 通知 | 非同步模式下通知管理員 |
