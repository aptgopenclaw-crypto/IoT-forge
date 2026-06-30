我仔細審閱了這份 h1.md。以下是逐項反饋：

---

## 總評：方向正確，但有幾個實作層面的問題

整體框架（DeviceTemplate 驅動動態類型、JSONB attributes）是對的，這跟你現有 `DeviceTemplate` 的設計完全一致。以下針對需要調整的部分逐條說明。

---

### 一、關於「Circuit → InfrastructureChannel」的泛化

文件建議把 `Circuit` 泛化為 `InfrastructureChannel`（電力/網路/水管…）。這個抽象層級**太高了**，原因：

- 你現有的 `Circuit` 有 `taipowerAccount`、`usageType`，這是非常具體的電力迴路概念
- 如果一個 `channel_type = 'WATER'` 的通道，它的欄位（管徑、水壓）跟電力迴路完全不一樣，放同一個 JSONB 只會讓查詢/報表變得困難
- **建議做法**：保留 `Circuit` 作為電力迴路專用（這是 IoT 最常見的基礎設施），未來有其他通道類型時再另外建表，不要硬塞進同一張表

```
// 建議：保持 Circuit 專用，不泛化
Circuit (電力迴路專用)
  └─ 台電電號、計費類型、用電分類…

// 未來擴充：NetworkSegment（網路迴路）
// 未來擴充：PipeSegment（管線區段）
```

---

### 二、關於「Contract → LifecycleDocument」的泛化

同樣的問題。`Contract` 的欄位（`budgetYear`、`procurementNumber`、`contractorName`）是具體的**標案契約**概念。

如果把「採購合約」、「保固書」、「租賃合約」全部塞進 `parties JSONB` + `attributes JSONB`，你會失去：

1. **資料完整性** — 無法用 `NOT NULL` 約束關鍵欄位
2. **查詢效能** — JSONB 裡面不能建立有效索引
3. **報表能力** — 無法直接 GROUP BY 標案年份

**建議**：保留 `Contract` 為標案專用。只有當你真的需要「多種文件類型且欄位差異極大」時，才考慮泛化。現在不需要。

---

### 三、`DeviceType` 從 Enum 改為 String ✅

這是正確的。但你需要注意一個關鍵問題：

**文件沒提到的地方**：用 String 之後，`@RequestMapping("/v1/auth/devices")` 的分頁查詢 `filterByDeviceType` 就不能用 Enum 做 type-safe query 了。

```java
// ⚠️ 現有 DeviceRepository.findByFilters() 用了 DeviceType 參數
// 改成 String 後要確保 JPA Query 的相容性
```

解決方案也簡單 — repository 層用 String 參數即可，controller 端保持 `@RequestParam(required = false) String deviceType`。

---

### 四、關於工單 `work_orders`

這部分的設計**非常好**。特別是：

- `source_type` 有 `CITIZEN / AUTO / SYSTEM / PATROL` — 這是路燈平台的真實需求，你用過所以知道要有
- `auto_reported_at` 與 `reported_at` 的對比設計 — 這是績效指標（KPI）的關鍵欄位，文件有抓到這個重點

但文件漏了一個重要欄位：

```sql
-- 建議新增：location_snapshot JSONB
-- 工單建立時「凍結」設備位置，防止設備移動後工單失準
location_snapshot JSONB  -- { "lng": 121.xxx, "lat": 25.xxx, "address": "..." }
```

---

### 五、與 CodeCortex 知識庫的整合

這部分寫得**太空泛**。文件中提到的：

> `@BusinessRule` 註解 + AOP 攔截

這不存在於你的 codebase，而且短期內不需要建立這樣的基礎設施。你現有的做法更務實：

```
✅ 已有：code_constraints 表 — 從 throw new BusinessException("...") 自動萃取
✅ 已有：test_rules — 從 @Test 方法名萃取
✅ 已有：generate-contract.py — Controller → TS 合約
✅ 已有：watch.py — Ctrl+S 增量同步

不需要：@BusinessRule 註解、AOP 攔截
```

正確的整合方式是：**把這個模組寫好後，執行現有的分析腳本就好**。

```
python scripts/tree-sitter-analyzer.py       # 自動抓到 DeviceController/Service
python scripts/phase35-enhancer.py            # 自動建立 feature_contracts
python scripts/phase5a-constraints.py --db knowledge.db  # 自動抓 BusinessException
python scripts/phase5b-semantics.py --db knowledge.db    # 自動抓 @Test 規則
```

不需要任何特殊的整合程式碼 — 這正是你設計這些工具的用意。

---

### 六、文件沒提到的兩件事

**1. 設備階層的深度限制**

你的 `Device` 已經有 `parent_device_id`，但沒有控制深度。建議加上：

```java
// DeviceService.create()
if (depth > MAX_DEPTH) {
    throw new BusinessException(ErrorCode.DEVICE_HIERARCHY_TOO_DEEP);
}
```

路燈平台的組合深度通常是：`回路 → 燈桿 → 燈具 → 控制器`，最多 4 層。這個約束應該寫在 Service 層。

**2. `DeviceTemplate` 的版本兼容**

文件有提到 `version` 欄位，但沒有說明「當模板更新後，既有設備的 attributes 怎麼辦」。這是真實世界的關鍵問題：

```
情境：原本 STREET_LIGHT 模板要求 height 必填
新模板：新增 lumens 欄位，height 改為選填
問題：舊設備的 height 還在，但新設備不填 height 的話，
      DeviceResponse 回傳的 height 可能為 null
```

解決方案：`DeviceResponse` 不要直接暴露 `attributes` Map，而是提供一個 `getAttribute(String key)` 方法，允許回傳 `null`，前端自行處理缺省值。

---

## 總結

| 項目 | 評分 | 備註 |
|------|------|------|
| 核心架構（Template + JSONB） | ✅ 正確 | 與你現有設計一致 |
| Circuit 泛化 | ⚠️ 過度 | 保持 Circuit 專用 |
| Contract 泛化 | ⚠️ 過度 | 保持 Contract 專用 |
| WorkOrder 設計 | ✅ 優秀 | 建議加上 location_snapshot |
| Inventory 管理 | ✅ 選配合理 | 有需求再實作 |
| CodeCortex 整合方式 | ❌ 太複雜 | 用現有分析腳本即可，不需新基礎設施 |
| 深度限制 & 版本兼容 | ⚠️ 遺漏 | 需要補上 |

如果你同意這些調整方向，我可以開始把這個模組實作出來 — 從 Entity 開始，然後 Service/Controller，最後同步知識庫。