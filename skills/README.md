從需求訪談到完成所有 Task，各階段使用的工具與技能如下：

## 階段 1：需求釐清 + 設計 (Brainstorming)

| 工具 / 技能 | 用途 |
| :--- | :--- |
| Skill: superpowers:brainstorming | 引導整個需求訪談流程：探索專案、一個一個問問題、提出方案比較、呈現設計 |
| MCP codegraph (codegraph_explore, codegraph_node, codegraph_search) | 探查既有 device module 結構、Entity/DTO/Service/Repository、ErrorCode 列舉、ConnectivityType、相依套件 |
| Bash | 列出 device module 所有 Java 檔、前端 device 相關檔案、檢查 pom.xml 既有相依 |
| Read | 讀取 DeviceService、DeviceRequest、DeviceListView.vue、locale 檔等關鍵程式碼 |

## 階段 2：撰寫設計文件

| 工具 / 技能 | 用途 |
| :--- | :--- |
| Write | 寫出 h1-device-import-design.md |
| Edit | 自檢修訂（錯誤報告 API 從 GET 改 POST、補上名稱模糊比對說明） |

## 階段 3：撰寫實作計畫

| 工具 / 技能 | 用途 |
| :--- | :--- |
| Skill: superpowers:writing-plans | 產出結構化的實作計畫，含檔案結構、8 個 Task、完整程式碼 |
| codegraph + Read + Bash | 再次確認 ConnectivityType 實際值、CircuitRepository 方法、前端 i18n 結構等 |
| Write | 寫出 h2-device-import-plan.md |
| Edit | 修正計畫中 desenvolv 筆誤、補上 raw 欄位的 validate 邏輯 |

## 階段 4：實作 (Subagent-Driven Development)

| 工具 / 技能 | 用途 |
| :--- | :--- |
| Skill: superpowers:subagent-driven-development | 主流程控制 — 一個 Task 派一個獨立子 agent + review |
| Agent (general-purpose, sonnet) × 8 次實作 + 8 次 review + 3 次 fix | 每個 Task 的 implementer 與 reviewer |
| skill scripts (task-brief, review-package) | 萃取 Task brief 到獨立檔案、產生 diff review package |
| codegraph (codegraph_node) | 確認 BaseResponse 實際方法簽章 |
| Write / Edit | 修復 controller 的 locale zh-CN→zh-TW 及 ImportResponse body |
| Bash | mvn compile, mvn test, npm run type-check, git commit |

## 階段 5：最終 Review

| 工具 / 技能 | 用途 |
| :--- | :--- |
| Skill: superpowers:requesting-code-review | 執行整支 branch 的最終 code review |
| Agent (general-purpose, sonnet) | 讀取 diff 檔並產出 Strengths + Issues + Assessment |
| Agent (fix subagent) | 一次修復 3 個 findings（execute guard、maxFileSize 檢查、新增 OrchestratorTest） |
| Bash | 驗證 19 個 tests 全部通過 |

## 階段 6：完成收尾

| 工具 / 技能 | 用途 |
| :--- | :--- |
| Skill: superpowers:finishing-a-development-branch | 偵測環境狀態並呈現選項（keep / push / discard） |

## 總結用量

| 類別 | 使用次數 |
| :--- | :--- |
| Skills 啟動 | 6 次（brainstorming → writing-plans → subagent-driven-dev → requesting-code-review → finishing-branch） |
| Agent 子 agent 派發 | ~22 次（8 實作 + 8 review + 3 fix + 1 最終 review + 1 fix + 1 recheck） |
| codegraph MCP 呼叫 | ~12 次 |
| Bash | ~30 次 |
| Read / Write / Edit | ~25 次 |