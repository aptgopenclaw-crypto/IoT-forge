請幫我分析 Spring Boot 專案中的 [訂單模組] (目錄路徑: src/main/java/com/example/order)。

請自動讀取該目錄（包含子目錄）下的所有 Controller, Service, Entity, DTO, Repository 檔案。
針對該模組下的「所有 API」，分析其 Data Flow 與資料變化，並將最終結果直接寫入一個 Markdown 檔案：`docs/order-module-data-flow.md`。

請嚴格按照以下結構生成 Markdown 內容：

## 1. 模組全局視圖
- 繪製該模組核心 Entity 之間的 ER 圖 (Mermaid erDiagram)。

## 2. API 資料流與狀態變化矩陣
請掃描所有 Controller 的 API 端點，並用一個大表格批次列出所有 API 的資料流：
| API 路徑與方法 | 業務目的 | Input (DTO/Param) | 核心處理邏輯 (Data Transform) | Output (VO) | DB 狀態變化 (CUD) | 副作用 (Cache/MQ/External) |
(請確保表格涵蓋該模組下所有 API，不要遺漏)

## 3. 核心 API 詳細時序圖
從上述表格中，挑選出「邏輯最複雜、涉及最多 DB 寫入或副作用」的 2 個核心 API，為它們分別繪製詳細的 Mermaid Sequence Diagram。

執行要求：
1. 請先列出你打算讀取的檔案清單讓我確認。
2. 確認後，請開始分析並直接將結果寫入 `IoT-forge/00-history/00-module-dococs/${模組名稱}-module-data-flow.md`。