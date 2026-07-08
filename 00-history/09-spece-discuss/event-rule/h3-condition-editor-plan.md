# 條件編輯器 — 實作計畫

> 日期：2026-07-08
> 延續：`h1.md`（選單定位）、`h2-module-design.md`（模組設計、條件樹規格）
> 定位：前端條件群組編輯器 + BETWEEN 運算子實作

---

## 一、範圍與目標

### 要實作的功能

1. **前端條件群組編輯器**：支援使用者在 UI 上建立任意巢狀 AND / OR / NOT 條件樹
2. **BETWEEN 運算子**：後端補上求值邏輯，前端支援 min / max 雙輸入框
3. **後端營運運算子補齊**：GTE / LTE / NEQ 一併實作（switch case 皆已定義，只缺邏輯）

### 不受影響

- 資料表結構（event_rule.condition 已是 JSONB，相容巢狀樹）
- 後端 API 合約（create / update 接受 ConditionNode，已支援巢狀）
- 後端白名單驗證（validateConditionFields 已遞迴走訪整棵樹）
- 規則求值流程（RuleEvaluator / ConditionEvaluator 已支援巢狀 AND/OR/NOT）

---

## 二、實作項目

### Phase 1：後端運算子補齊（低風險、可獨立上線）

| 檔案 | 修改內容 |
|---|---|
| `ConditionEvaluator.java` | 在 switch 中補上 `GTE`、`LTE`、`NEQ`、`BETWEEN` 的實作分支 |

**BETWEEN 規格**：
- `node.getValue()` 為 `List<Number>` 或 `[min, max]` 陣列
- 比對邏輯：`actual >= min && actual <= max`
- 前端送 JSON 時格式：`"value": [0, 100]`

**GTE / LTE / NEQ 規格**：
- GTE → `compareNumeric(actual, expected) >= 0`
- LTE → `compareNumeric(actual, expected) <= 0`
- NEQ → `!compareEqual(actual, expected)`

### Phase 2：前端條件編輯器（核心 UI 改動）

#### 2a. 資料模型

```typescript
/** 條件群組（分支節點） */
interface ConditionGroup {
  op: 'AND' | 'OR' | 'NOT'
  children: (ConditionLeaf | ConditionGroup)[]
}

/** 單一條件（葉節點）—— 沿用目前結構 */
interface ConditionLeaf {
  field: string
  operator: ConditionOperator
  value: unknown       // 單值或 [min, max] 陣列
}
```

前端內部 `form.condition` 統一為 `ConditionGroup | ConditionLeaf`。向後端送出的 JSON 直接序列化即可（ConditionNode 相容）。

#### 2b. 群組編輯器元件

新增 `ConditionEditor.vue` 元件（遞迴自渲染）：

```
┌─────────────────────────────────────────┐
│ [AND ▾]                             [×] │  ← 群組標頭：邏輯運算子下拉 + 刪除群組
├─────────────────────────────────────────┤
│ ┌─────────────────────────────────────┐ │
│ │ voltage  [GT ▾]  [75    ]      [×] │ │  ← 條件行（葉節點）
│ └─────────────────────────────────────┘ │
│ ┌─────────────────────────────────────┐ │
│ │ [AND ▾]                         [×] │ │  ← 巢狀子群組
│ │  ├─ brightness  [LT ▾]  [20]   [×] │ │
│ │  └─ powerFactor [EQ ▾]  [0.5]  [×] │ │
│ └─────────────────────────────────────┘ │
│ [+ 新增條件] [+ 新增群組]                │  ← 操作按鈕
└─────────────────────────────────────────┘
```

**操作行為**：

| 操作 | 行為 |
|---|---|
| 新增條件 | 在當前群組插入一個新的空白葉節點 |
| 新增群組 | 在當前群組插入一個空白子群組（預設 AND） |
| 刪除條件行 | 從 children 移除該葉節點 |
| 刪除群組 | 從父層 children 移除該子群組 |
| 切換 op | 改變當前群組的 AND / OR / NOT |
| NOT 限制 | NOT 只能有 1 個 child（編輯器強制約束） |

#### 2c. 單一條件行（ConditionRow）

每列條件行包含：

```
[欄位下拉]  [運算子下拉]  [值輸入]
```

**運算子與值輸入的對應**：

| 運算子 | 值輸入 UI |
|---|---|
| GT / LT / GTE / LTE / EQ / NEQ | 單一 `<el-input>` |
| BETWEEN | 兩個 `<el-input-number>`（min / max） |
| CONTAINS | 單一 `<el-input>`（字串） |

**欄位下拉**：沿用已實作的 `fieldOptions`（從 device_template schema 萃取）

#### 2d. BETWEEN 值輸入

當 `operator === 'BETWEEN'` 時，value 改為雙輸入框：

```
[min ██████] ~ [max ██████]
```

- 送出前組合成 `[min, max]` 陣列
- 編輯回填時從陣列解構成 min / max

### Phase 3：編輯（openEdit）逆向序列化

- `openEdit(row)` 時，`row.condition` 可能是巢狀樹或單一葉節點
- 編輯器需遞迴載入整個樹結構
- 單一葉節點（無 op/children）向後相容顯示為單一條件行

---

## 三、檔案變更清單

| 檔案 | 異動 |
|---|---|
| `backend/.../evaluation/ConditionEvaluator.java` | 補上 GTE/LTE/NEQ/BETWEEN 求值邏輯 |
| `frontend/src/views/admin/eventrule/EventRuleListView.vue` | 將條件段抽離為元件、加上群組操作 |
| `frontend/src/views/admin/eventrule/ConditionEditor.vue` | **新增** — 遞迴群組編輯器 |
| `frontend/src/views/admin/eventrule/ConditionRow.vue` | **新增** — 單一條件行編輯（含 BETWEEN 雙輸入） |
| `frontend/src/types/telemetry.ts` | 新增 ConditionGroup / ConditionLeaf 型別 |
| `frontend/src/locales/*.ts` | 補上群組編輯器相關 i18n 鍵值 |

---

## 四、實作順序

```
Phase 1 (後端運算子)         → ConditionEvaluator.java
     ↓
Phase 2a (型別定義)          → telemetry.ts
     ↓
Phase 2b (ConditionRow 元件) → 可獨立測試單一條件行的 BETWEEN 顯示
     ↓
Phase 2c (ConditionEditor)   → 遞迴群組 + AND/OR/NOT
     ↓
Phase 2d (整合進 Dialog)     → EventRuleListView.vue 條件段替換
     ↓
Phase 3 (openEdit 逆向)      → 巢狀樹編輯載入
```

每階段可獨立驗證：

| Phase | 驗證方式 |
|---|---|
| 1 | `mvn test -Dtest=ConditionEvaluatorTest` |
| 2a–2c | `npm run test:run` (Vitest 元件測試) |
| 2d–3 | 手動操作：新增含 AND 條件的規則 → 驗證 DB JSONB → 開編輯確認回填正確 |

---

## 五、風險與注意事項

### 向後相容

- 既有「單一條件」規則（v1 格式）：`{ field, operator, value }`（無 op/children）
  - `ConditionNode.isLeaf()` → true → `ConditionEditor` 將其視為根層級的單一條件行
  - 編輯後儲存時若不移除 op/children，可能變為分支格式 → 需確保不影響求值
  - **策略**：編輯器統一用群組模式，單一條件視為 `{ op: "AND", children: [{ field, operator, value }] }` 的群組

### NOT 限制

- `ConditionEvaluator` 要求 NOT 只能有 1 個 child
- 編輯器需限制：切換到 NOT 時，若 children > 1，提示並自動刪除多餘 child

### BETWEEN 值格式

- 資料庫 JSONB 中存為陣列：`[0, 100]`
- 需確保 `EventRuleService.validateConditionFields` 對 BETWEEN 節點不做數值型別檢查（value 是陣列而非單值）

### 測試要點

- 空 children 群組（使用者新增群組後未加入任何條件）
- NOT 群組編輯為一般群組再切回 NOT
- 從 AND 切換到 OR（children 不變，只改 op）
- BETWEEN 填入非數值（前端應做基本防呆）
- 編輯已存在的巢狀規則（逆向序列化正確性）

---

## 六、附錄： ConditionEvaluator 修改示意

```java
// 在 evaluateLeaf switch 中補上：
case GTE  -> compareNumeric(actual, expected) >= 0;
case LTE  -> compareNumeric(actual, expected) <= 0;
case NEQ  -> !compareEqual(actual, expected);
case BETWEEN -> {
    if (!(expected instanceof List<?> list) || list.size() != 2) {
        log.warn("[ConditionEvaluator] BETWEEN requires [min, max] array");
        yield false;
    }
    try {
        double a = toDouble(actual);
        double min = toDouble(list.get(0));
        double max = toDouble(list.get(1));
        yield a >= min && a <= max;
    } catch (NumberFormatException ex) {
        log.warn("[ConditionEvaluator] BETWEEN numeric cast failed");
        yield false;
    }
}
```
