評估 bpmn-js 轉 JSON 的成本，若不強制要求標準 BPMN XML，建議改用 AntV X6 或 LogicFlow 等原生支援 JSON 的流程圖庫。

> 這個建議，是為了什麼


這個建議的核心目的是：**避免不必要的開發成本與複雜度**。

讓我詳細解釋：

## 問題根源：bpmn-js 的設計定位

### 1. **bpmn-js 是「標準遵循者」**
```javascript
// bpmn-js 的預設行為
import BpmnJS from 'bpmn-js';

const viewer = new BpmnJS();
await viewer.importXML(bpmnXml);

// 匯出時，只能得到標準 BPMN 2.0 XML
const { xml } = await viewer.saveXML({ format: true });
// ❌ 無法直接得到 JSON
```

bpmn-js 的設計目標是**符合 BPMN 2.0 規範**，因此：
- ✅ 預設匯出：標準 BPMN 2.0 XML
- ❌ 不支援：直接匯出自訂 JSON

---

## 2. XML 轉 JSON 的真實成本

如果你堅持用 bpmn-js，但後端自建引擎要吃 JSON，你需要寫一個**複雜的轉換器**：

### 轉換器的開發工作量

```typescript
// 你需要寫的轉換器（示意）
class BpmnXmlToJsonConverter {
  convert(xml: string): CustomWorkflowJson {
    // 1. 解析 XML（需處理 BPMN 命名空間、屬性等）
    const parser = new DOMParser();
    const xmlDoc = parser.parseFromString(xml, 'text/xml');
    
    // 2. 提取流程元素（需理解 BPMN 語義）
    const startEvents = xmlDoc.getElementsByTagName('bpmn:startEvent');
    const tasks = xmlDoc.getElementsByTagName('bpmn:task');
    const gateways = xmlDoc.getElementsByTagName('bpmn:exclusiveGateway');
    const flows = xmlDoc.getElementsByTagName('bpmn:sequenceFlow');
    
    // 3. 映射到自訂 JSON Schema（需處理各種 edge cases）
    return {
      nodes: Array.from(tasks).map(task => ({
        id: task.getAttribute('id'),
        type: this.mapBpmnTypeToCustomType(task), // 需處理多類型
        position: this.extractPosition(task), // BPMN XML 通常不存位置，需從 DI 圖形資訊提取
        assignee: this.extractAssignee(task), // 需解析 extensionElements
        conditions: this.extractConditions(task) // 需解析條件表達式
      })),
      edges: Array.from(flows).map(flow => ({
        source: flow.getAttribute('sourceRef'),
        target: flow.getAttribute('targetRef'),
        condition: this.extractConditionExpression(flow) // 需解析條件
      }))
    };
  }
  
  // 需要處理的複雜情況：
  // - Boundary Events（附著在 Task 上的事件）
  // - Multi-Instance Tasks（會簽/多實例）
  // - Sub-Processes（子流程）
  // - Message/Timer Events（事件定義）
  // - Data Objects / Data Stores（資料物件）
  // - Lanes / Pools（泳道）
  // ... 等等數十種 BPMN 元素
}
```

### 實際開發成本估算

| 項目 | 工時 | 說明 |
|------|------|------|
| XML 解析與驗證 | 8h | 處理命名空間、屬性、結構驗證 |
| 基礎元素映射（Task, Gateway, Flow） | 16h | 常見元素的轉換 |
| 事件處理（Start/End/Boundary/Timer） | 16h | 各種事件的語義轉換 |
| 條件表達式解析 | 12h | BPMN 條件 → 自訂 DSL |
| 圖形資訊提取（DI 元素） | 8h | 從 BPMN DI 提取座標、大小 |
| Edge Cases 處理 | 20h | 子流程、多實例、錯誤處理 |
| 測試與除錯 | 20h | 確保各種流程圖都能正確轉換 |
| **總計** | **~100 工時** | 約 2-3 週全職開發 |

---

## 3. AntV X6 / LogicFlow 的優勢

### 原生 JSON 支援

```typescript
// AntV X6 範例
import { Graph } from '@antv/x6';

const graph = new Graph({ container: document.getElementById('container') });

// 使用者拖拽節點、連線後...

// 直接匯出 JSON（無需轉換！）
const jsonData = graph.toJSON();
// {
//   cells: [
//     { id: 'node1', shape: 'rect', label: '送審', x: 100, y: 100 },
//     { id: 'node2', shape: 'rect', label: '審核', x: 300, y: 100 },
//     { id: 'edge1', source: { cell: 'node1' }, target: { cell: 'node2' } }
//   ]
// }

// 後端直接吃這個 JSON，定義自己的 schema 即可
```

### 開發成本對比

| 方案 | 前端開發 | 轉換器開發 | 後端開發 | 總成本 |
|------|----------|------------|----------|--------|
| **bpmn-js + XML 轉 JSON** | 8h（使用現成編輯器） | **100h**（寫轉換器） | 16h（解析 JSON） | **~124h** |
| **AntV X6 / LogicFlow** | 24h（自訂節點/工具列） | **0h**（無需轉換） | 16h（解析 JSON） | **~40h** |
| **節省成本** | - | **-100h** | - | **~84h (70%)** |

---

## 4. 什麼時候「應該」用 bpmn-js？

雖然成本較高，但以下情況**仍建議用 bpmn-js**：

### ✅ 情境 1：機關強制要求標準 BPMN 2.0
- 採購規格書明確寫明「需符合 BPMN 2.0 標準」
- 需與外部系統交換流程定義（如 Camunda、Flowable）
- 需長期保存標準格式（10 年後仍能讀取）

### ✅ 情境 2：未來可能導入完整 BPMN 引擎
- 現在自建，但預計 3 年後遷移到 Camunda
- 需要標準 XML 才能無縫遷移

### ✅ 情境 3：流程極度複雜
- 需要 BPMN 的高級功能（事件子流程、交易、補償處理）
- 通用圖庫無法滿足需求

---

## 5. 對你這個專案的建議

根據你的需求文件（03-06）：

### ❌ **你不需要的 BPMN 高級功能**
- 事件子流程（Event Sub-Process）
- 交易（Transaction）
- 補償處理（Compensation）
- 多實例會簽（Multi-Instance）← 自建引擎的 Token 機制即可
- 訊息事件（Message Event）← 用通知模組即可

### ✅ **你需要的只是「視覺化流程設計」**
- 畫出節點（送審、審核、結案）
- 畫出連線（順序流）
- 設定簡單條件（金額 > 10 萬 → 局長審核）
- 匯出 JSON 給自建引擎

### 🎯 **結論：用 AntV X6 或 LogicFlow 更適合**

| 評估維度 | bpmn-js | AntV X6 / LogicFlow |
|----------|---------|---------------------|
| 學習曲線 | 中（需懂 BPMN 語義） | 低（直覺圖形編輯） |
| 開發成本 | 高（需寫轉換器） | 低（原生 JSON） |
| 彈性 | 低（受限于 BPMN 標準） | 高（自訂節點/連線） |
| 符合需求 | 過度設計 | 剛好 |

---

## 一句話總結

> **如果你的後端是自建引擎（不吃標準 BPMN XML），就不要用 bpmn-js，除非機關強制要求。**

用 AntV X6 或 LogicFlow，可以省下 **約 100 工時的轉換器開發成本**，同時獲得更高的彈性。