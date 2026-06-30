# Workflow 功能模組「易擴展」設計指南

> 對象：`com.taipei.iot.workflow` 後端模組（流程引擎：定義 / 實例 / 審核步驟 / 代理，被 `assettransfer`、`dispatch` 等業務模組共用）
> 目的：說明如何設計、以及有哪些具體作法，讓工作流（Workflow）模組能「易擴展」——在最少修改、不破壞既有行為的前提下，加入新的節點類型（並簽 / 條件分支 / 自動節點 / 知會節點）、新的審核人解析策略、新的業務流程接入、新的審核後動作（hook），以及讓非開發者也能在後台設計流程。

---

## 1. 什麼叫「易擴展」？（先定義目標）

對 workflow 模組而言，「易擴展」要滿足以下可衡量目標：

| 目標 | 白話描述 | 衡量方式 |
|------|----------|----------|
| **開放封閉（OCP）** | 新增節點類型／審核人策略／業務流程時「只新增類別或設定」，不改引擎核心 | 加一種節點類型所需修改 `WorkflowEngine` 的分支數趨近 0（現況需改 `approve()`） |
| **單一職責（SRP）** | 流轉、審核人解析、通知、業務回呼各自獨立 | 改通知不波及流轉 |
| **可設定（Configurable）** | 流程節點/順序由 DB 定義，後台可改 | 非開發者可新增流程（現況部分具備：JSON 定義） |
| **可組合（Composable）** | 審核完成後可掛多個動作（通知/業務回呼/稽核） | 加一個 hook 不改引擎 |
| **可測試（Testable）** | 每個擴展點可獨立 mock 測試 | 解析器、節點處理器可單測 |
| **多租戶安全** | 流程定義/實例以租戶隔離 | 定義可 fallback DEFAULT、實例嚴格租戶 |
| **流轉正確性** | 並發審核不重複推進、狀態不亂 | 悲觀鎖 + 狀態守衛（現況具備） |

---

## 2. 現況盤點：已經做對的事

workflow 模組已有相當不錯的擴展骨架，特別是審核人解析與通知解耦：

### 2.1 模組結構

```
workflow/
├── entity/      WorkflowDefinitionEntity（範本，steps 存單一 JSONB；含 version）
│                WorkflowInstanceEntity（實例，currentStepId/status/businessId/businessType）
│                WorkflowStepLogEntity（每步一筆軌跡，action/comment/assignee/targetStep）
│                DelegateSettingEntity（代理設定，含日期區間與 businessType 範圍）
├── model/       StepDefinition（id/name/type/roleCode/next/rejectTarget/slaDays）
│                WorkflowStepsJson（initialStep + List<StepDefinition>）
│                WorkflowContext（businessId/businessType/applicantId/departmentId...）
│                WorkflowStatus / WorkflowStepType / WorkflowAction（enum）
├── service/     WorkflowEngine（核心狀態機：start/approve/reject/resubmit/cancel）
│                IAssigneeResolver（★ 審核人解析策略介面）
│                OrgAssigneeResolver(@Primary) / MockAssigneeResolver
│                WorkflowSlaService（SLA KPI 計算）
├── event/       WorkflowStepAssignedEvent / WorkflowStepCompletedEvent
│                WorkflowNotificationListener（@TransactionalEventListener AFTER_COMMIT）
├── repository/  4 個（含 findByIdForUpdate 悲觀鎖、findActiveDelegate）
├── controller/  WorkflowPocController（/v1/api/poc/workflow/*）
├── dto/         6 個請求 DTO
└── exception/   WorkflowException 家族（細分例外）
```

### 2.2 已具備的良好擴展性

| 設計 | 說明 | 對擴展的意義 |
|------|------|-------------|
| **審核人解析策略** | `IAssigneeResolver.resolve(step, context)`；`OrgAssigneeResolver` 為 `@Primary` | ★ 換/加審核人來源 = 換一個實作，引擎不動 |
| **JSON 流程定義** | 整份流程存 `steps_json`，`findLatestEnabledByCode` 取最新啟用版 | 新增流程可純 DB insert、免改碼；版本化支援 |
| **通知解耦事件** | `WorkflowStepAssignedEvent/CompletedEvent` → `@TransactionalEventListener(AFTER_COMMIT)` | 通知與引擎解耦、提交後才送 |
| **代理（Delegation）** | `findActiveDelegate` 依日期/businessType 解析，且防「代理人=申請人」 | 代理邏輯內建於解析器 |
| **狀態機 + 悲觀鎖** | `findByIdForUpdate` + status 守衛（僅 IN_PROGRESS 可轉換） | 並發安全、流轉正確 |
| **完整軌跡** | 每步一筆 `WorkflowStepLog`，駁回/重送各新增一筆 | 稽核可追溯 |
| **多租戶** | 定義可 fallback DEFAULT、實例嚴格租戶 | 平台預設 + 租戶客製 |

> **結論：審核人解析（誰來審）與通知（審了之後通知誰）已解耦得不錯；但「節點類型、流轉拓樸、業務回呼、後台設計能力」偏硬編碼與線性。本文件聚焦補上這些擴展點。**

---

## 3. 擴展維度：會被擴展的是哪些東西？

| 維度 | 範例需求 | 現況痛點 |
|------|----------|----------|
| **A. 節點類型** | 並簽（多人同時審）、會簽門檻、條件分支、自動/系統節點、知會（CC）節點 | `WorkflowStepType` 僅 `NORMAL/END`；流轉邏輯寫死在 `WorkflowEngine.approve()` |
| **B. 流轉拓樸** | 平行分支、合併（join）、依條件決定下一步/駁回目標 | `next`/`rejectTarget` 皆單一且定義期固定，無條件求值 |
| **C. 審核人解析** | 依職稱、主管階層、外部系統、輪值 | 已具策略介面，但 `OrgAssigneeResolver` 用 `switch(roleCode)` 硬編碼角色 |
| **D. 業務回呼（hook）** | 審核通過/駁回時，業務模組要做事（改狀態、發單、扣庫存） | 業務模組「直接呼叫引擎再自行更新」，無回呼介面；事件僅供通知 |
| **E. 業務流程接入** | 新增一種需審核的業務（如請假、採購） | 需手刻 service 編排（start→approve…）、複製 `resolveCurrentAssignee` 等樣板 |
| **F. 後台可設計** | 非開發者用 UI 拉流程、設審核人、發布版本 | 目前無管理 API/UI；JSON 需手動寫入 DB |
| **G. 審核動作** | 加簽、轉派、撤回、催辦、會簽 | `WorkflowAction` 僅 4 種；加動作要改引擎 |

---

## 4. 設計手法（Patterns & Practices）

### 手法 1：節點類型以「處理器策略」抽象 — 維度 A、B（**最高優先**）

**問題**：流轉邏輯（到 END 就完成、否則取 `next`）全寫在 `WorkflowEngine.approve()`。要加並簽/條件分支/自動節點，就得在引擎裡加 `if/switch`，違反 OCP。

**做法**：把「某類節點被進入/被審核完該怎麼推進」抽成 `NodeHandler`，引擎只負責鎖定、權限、軌跡等通用骨架，**推進交給對應節點處理器**。

```java
public interface NodeHandler {
    StepType type();                                   // NORMAL, END, PARALLEL, CONDITION, AUTO, NOTIFY
    /** 進入節點：建立待辦、解析審核人、必要時自動完成（AUTO/NOTIFY） */
    void onEnter(WorkflowInstanceEntity inst, StepDefinition step, WorkflowContext ctx);
    /** 節點完成後決定下一步（回傳下一節點 id 或 終止） */
    TransitionResult onComplete(WorkflowInstanceEntity inst, StepDefinition step,
                                WorkflowAction action, WorkflowContext ctx);
}

@Component class NormalNodeHandler   implements NodeHandler { type(){return NORMAL;} ... }
@Component class EndNodeHandler      implements NodeHandler { type(){return END;} ... }
// 未來新增——不動引擎：
@Component class ParallelNodeHandler implements NodeHandler { /* 會簽門檻、join 邏輯 */ }
@Component class ConditionNodeHandler implements NodeHandler { /* 依運算式選 next */ }
@Component class AutoNodeHandler     implements NodeHandler { /* 系統自動通過 */ }
@Component class NotifyNodeHandler   implements NodeHandler { /* 知會後直接流過 */ }
```

引擎以 `Map<StepType, NodeHandler>`（Spring 注入 `List<NodeHandler>` 聚合）分派：

```java
// WorkflowEngine.approve() 核心簡化為：
completeCurrentStepLog(action, comment);
TransitionResult t = handlers.get(currentStep.type()).onComplete(inst, currentStep, action, ctx);
applyTransition(inst, t);   // 進入下一節點時呼叫對應 handler.onEnter（可能再自動推進）
```

> **效益**：新增節點類型 = 新增一個 `NodeHandler` + 在 `WorkflowStepType` 加值，引擎主流程零修改。並簽、條件分支、自動節點都落在各自處理器，互不干擾。

---

### 手法 2：條件求值以「運算式評估器」抽象 — 維度 B

**問題**：`next` 與 `rejectTarget` 在定義期固定，無法「依金額大於 X 走主管簽核、否則直接通過」這類條件分支。

**做法**：在 `StepDefinition` 增加可選的條件描述（如 `conditions: [{expr, next}]`），由 `ConditionEvaluator` 在 `WorkflowContext` 上求值。

```java
public interface ConditionEvaluator {
    boolean matches(String expression, WorkflowContext ctx);  // 對 context 變數求值
}
@Component class SpelConditionEvaluator implements ConditionEvaluator { /* Spring SpEL */ }
```

- `ConditionNodeHandler`（手法 1）依序評估 `conditions`，命中即走對應 `next`，否則走預設 `next`。
- 駁回目標同理可改為條件式（`rejectTarget` 或 `rejectExpression`）。

> 安全注意：運算式只允許讀 `WorkflowContext` 白名單變數，避免 SpEL 注入；context 變數來源受控。

---

### 手法 3：審核人解析改「可註冊的策略集合」 — 維度 C

**問題**：已有 `IAssigneeResolver` 介面（很好），但 `OrgAssigneeResolver` 內以 `switch(roleCode)` 硬編碼 `ROLE_DEPT_USER/ROLE_DEPT_ADMIN/其他`。新增「依職稱」「依主管階層」「外部系統」解析需改這個 switch。

**做法**：把「依某種規則找人」再下沉成 `AssigneeStrategy` 集合，`OrgAssigneeResolver` 變成依 `step` 的解析類型挑策略。

```java
public interface AssigneeStrategy {
    String kind();                                         // APPLICANT, DEPT_ROLE, TENANT_ROLE, JOB_TITLE, MANAGER_CHAIN, EXTERNAL
    List<String> resolve(StepDefinition step, WorkflowContext ctx);  // 回傳候選人（支援並簽）
}
@Component class ApplicantStrategy ... @Component class DeptRoleStrategy ...
// 未來：JobTitleStrategy / ManagerChainStrategy / ExternalApiStrategy（@ConditionalOnProperty）
```

- `StepDefinition` 增 `assigneeKind`（取代 roleCode 的硬分類），解析器依 kind 選策略。
- 代理（delegate）疊加邏輯維持在解析器一處套用。
- **回傳 `List<String>`** 而非單一 userId，為「並簽」（手法 1 的 `ParallelNodeHandler`）鋪路。

> **效益**：新增審核人來源 = 新增一個 `AssigneeStrategy`，不再改 switch；同時打通並簽所需的多候選人。

---

### 手法 4：業務回呼以「事件 + 回呼介面」雙軌 — 維度 D、E（**強烈建議**）

**問題**：業務模組（assettransfer/dispatch）是「直接呼叫引擎 → 再自行更新自己的實體狀態」，並複製 `resolveCurrentAssignee` 等樣板。事件目前只餵通知，業務副作用無處掛。新增業務流程要重抄一遍編排。

**做法**：
1. **同步回呼介面**（需強一致時，如「核准即發單」）：

```java
public interface WorkflowCallback {
    String businessType();                                  // ASSET_TRANSFER, WORK_ORDER, ...
    default void onStepCompleted(WorkflowInstanceEntity inst, StepContext step) {}
    default void onWorkflowCompleted(WorkflowInstanceEntity inst) {}
    default void onWorkflowRejected(WorkflowInstanceEntity inst) {}
}
```
引擎在狀態轉換點，依 `businessType` 找對應 callback 呼叫（找不到則略過）。業務模組實作此介面即可，不必散落呼叫。

2. **非同步事件**（最終一致即可，如儀表板、索引）：擴充既有 `WorkflowStepCompletedEvent` 讓業務模組也能 `@TransactionalEventListener` 訂閱。

3. **共用編排樣板**：把「start → 自動完成申請人步驟 → 回填 currentAssignee」這段重複邏輯抽成 `WorkflowFacade`，業務模組呼叫 facade 而非自行拼裝。

> **效益**：新增業務流程 = 實作 `WorkflowCallback` + 呼叫 `WorkflowFacade`，不再複製編排與 assignee 解析樣板；副作用一致、可測。

---

### 手法 5：審核動作可擴展 — 維度 G

**問題**：`WorkflowAction` 僅 `APPROVE/REJECT/RESUBMIT/CANCEL`。加「加簽、轉派、撤回、催辦」要改引擎多處。

**做法**：把動作抽成 `WorkflowCommand` 處理器（命令模式），與節點處理器協作。

```java
public interface WorkflowCommandHandler {
    WorkflowAction action();
    WorkflowInstanceEntity handle(WorkflowInstanceEntity inst, CommandPayload p, String userId);
}
@Component class ApproveCommand ... @Component class RejectCommand ...
// 未來：AddSignCommand（加簽）/ ReassignCommand（轉派）/ WithdrawCommand（撤回）
```

> 引擎/Controller 依 action 取對應 handler，新增動作不改既有命令。

---

### 手法 6：後台可設計流程（治理）— 維度 F

**問題**：流程雖是 JSON（可免改碼），但**沒有管理 API/UI**，只能手動寫 DB；非開發者無法新增/改版流程。

**做法**：
- 提供 `WorkflowDefinition` 管理 API（CRUD + 發布/停用 + 版本），把 `WorkflowPocController`（POC）正式化。
- **定義驗證器**：發布前驗證 JSON（節點 id 唯一、`next/rejectTarget` 指向存在、有 END 可達、`assigneeKind` 合法、無孤島/死迴圈）。
- 後台 UI 以節點類型清單（手法 1）+ 審核人策略清單（手法 3）為「可選元件」，資料驅動，新增類型/策略後台自動出現。

> **效益**：新增/調整流程從「工程改碼部署」降為「後台設定發版」，真正做到非開發者可擴展。

---

## 5. 共通的工程實踐（讓擴展不出錯）

| 實踐 | 作法 | 為何重要 |
|------|------|----------|
| **引擎只留骨架** | 鎖定/權限/軌跡/狀態守衛留引擎；推進交 `NodeHandler` | 加節點類型不動引擎 |
| **策略集合自動聚合** | `List<NodeHandler/AssigneeStrategy/CommandHandler>` 注入建 Map | 新增 = 加 `@Component` |
| **特性開關** | 外部來源策略/節點加 `@ConditionalOnProperty` | 逐租戶/環境灰度 |
| **事務後事件** | 業務/通知副作用用 `AFTER_COMMIT` | 避免回滾仍發單/送信 |
| **強一致用回呼、最終一致用事件** | 區分 `WorkflowCallback`（同交易）與事件（提交後） | 正確選擇一致性等級 |
| **定義先驗證再發布** | `WorkflowDefinitionValidator` | 防孤島/死迴圈/壞引用上線 |
| **運算式沙箱化** | 條件只讀 context 白名單變數 | 防 SpEL 注入 |
| **並發安全** | 維持 `findByIdForUpdate` 悲觀鎖 + status 守衛 | 防重複推進 |
| **多租戶** | 定義 fallback DEFAULT、實例嚴格租戶 | 平台預設 + 客製隔離 |
| **可測試** | 每個 handler/strategy/callback 單測；引擎以 mock handler 測流轉 | 擴展有回歸保護 |

---

## 6. 目前缺口與優先順序

| 缺口 | 現況 | 風險 | 建議優先級 |
|------|------|------|-----------|
| 節點類型/流轉寫死引擎 | `StepType` 僅 NORMAL/END，邏輯在 `approve()` | 加並簽/分支要改引擎核心 | ★★★（先做） |
| 無條件分支/動態駁回 | `next`/`rejectTarget` 定義期固定 | 無法依金額/類別走不同路 | ★★★ |
| 審核人解析有 switch 硬編碼 | `OrgAssigneeResolver` switch(roleCode) | 加職稱/主管鏈/外部來源要改 switch | ★★☆ |
| 無業務回呼介面 | 業務模組直呼引擎 + 自更新 + 複製樣板 | 新流程重抄編排、副作用散落 | ★★★ |
| 無後台管理 API/UI | JSON 需手寫 DB（POC controller） | 非開發者無法設計流程 | ★★☆ |
| 動作不可擴展 | `WorkflowAction` 僅 4 種 | 加簽/轉派/撤回要改引擎 | ★☆☆ |
| 並簽僅單一審核人 | assignee 解析回傳單一 userId | 無法多人會簽 | ★★☆（隨手法1/3一起） |

---

## 7. 落地檢查清單

### 7.1 新增一種「節點類型」（例如並簽 PARALLEL）
- [ ] `WorkflowStepType` 加 `PARALLEL`
- [ ] 新增 `ParallelNodeHandler implements NodeHandler`（onEnter 建多筆待辦、onComplete 判會簽門檻/join）
- [ ] `AssigneeStrategy` 回傳多候選人（手法 3）
- [ ] 軌跡/狀態能表達「部分完成」
- [ ] 單測：全簽通過、部分駁回、門檻達成

### 7.2 新增一種「審核人解析策略」（例如主管階層）
- [ ] 新增 `ManagerChainStrategy implements AssigneeStrategy`（`@ConditionalOnProperty` 視需要）
- [ ] `StepDefinition.assigneeKind` 可指定該策略
- [ ] 確認代理（delegate）疊加仍生效、且不指派給申請人
- [ ] 單測：解析命中、無人時的退路

### 7.3 接入一種「新業務流程」（例如請假）
- [ ] 在 DB 建立 `WorkflowDefinition`（code/version/steps_json）並通過驗證器
- [ ] 業務模組實作 `WorkflowCallback`（onWorkflowCompleted/Rejected）
- [ ] 透過 `WorkflowFacade` 啟動/推進（不自行拼裝編排）
- [ ] 單測：核准全程、駁回重送、取消

### 7.4 新增一種「審核動作」（例如加簽）
- [ ] `WorkflowAction` 加 `ADD_SIGN`
- [ ] 新增 `AddSignCommand implements WorkflowCommandHandler`
- [ ] 軌跡可記錄加簽關係
- [ ] 單測：加簽後流轉正確

> 若上述任一新增**被迫修改 `WorkflowEngine` 的 `approve()/reject()` 核心分支**，代表該擴展點尚未抽出，應回頭補上手法 1~5 的抽象。

---

## 8. 一句話總結

> workflow 模組在**「誰來審」（`IAssigneeResolver` 策略）**與**「審完通知誰」（AFTER_COMMIT 事件）**已解耦得不錯，JSON 定義也讓新增流程可免改碼。
> 但**「節點類型與流轉拓樸」仍寫死在 `WorkflowEngine`、缺條件分支/並簽、缺業務回呼介面、缺後台設計能力**。
> 建議優先補上 **節點處理器策略（NodeHandler）+ 條件評估器（ConditionEvaluator）**、**審核人策略集合（AssigneeStrategy，回傳多候選人以支援並簽）**、**業務回呼介面 + 共用編排 Facade（WorkflowCallback / WorkflowFacade）**，並把 POC controller 正式化為**附驗證器的後台管理 API**，
> 全程貫徹「引擎只留骨架、策略自動聚合、強一致用回呼/最終一致用事件、定義先驗證再發布、運算式沙箱化、並發悲觀鎖」，即可在不動引擎核心的前提下持續演進，並讓非開發者也能設計流程。
