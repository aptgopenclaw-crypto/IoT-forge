1 現有 Spring Boot 專案內新增 `workflow` 模組

2 新建 `workflow_definitions`、`workflow_instances`、`workflow_step_logs` 三張表，直接建在現有 PostgreSQL(flyway)

3. 


---

## 一、角色設計建議

### 1.1 系統現有角色對照

| 角色代碼 | 角色名稱 | 業務說明 |
|---------|---------|---------|
| ROLE_MONITOR | MONITOR | 監造人員 |
| ROLE_OPERATOR | OPERATOR | 維運人員 |
| ROLE_VIEWER | VIEWER | 檢視者 |
| ROLE_FIELD_USER | FIELD_USER | 現場人員 |
| ROLE_DEPT_ADMIN | DEPT_ADMIN | 部門管理者 |
| ROLE_DEPT_USER | DEPT_USER | 部門使用者 |

### 1.2 與簽核流程的映射關係

POC 階段需要將流程中的 `role_code` 映射到現有系統角色：

| 流程角色 (step.role_code) | 對應現有系統角色 | 說明 |
|--------------------------|-----------------|------|
| `APPLICANT` | `ROLE_DEPT_USER` | 申請人（部門使用者） |
| `MANAGER` | `ROLE_DEPT_ADMIN` | 部門主管（部門管理者） |
| `PROPERTY_MANAGER` | `ROLE_DEPT_ADMIN` | 財產管理（可獨立或同主管） |
| `INSPECTOR` | `ROLE_FIELD_USER` | 現場巡查人員 |
| `VENDOR` | `ROLE_OPERATOR` | 廠商維運人員 |
| `SUPERVISOR` | `ROLE_MONITOR` | 監造/督導人員 |
| `VIEWER` | `ROLE_VIEWER` | 僅檢視（無審核權） |

### 1.3 建議調整

| 建議 | 說明 |
|------|------|
| 新增 `ROLE_DIRECTOR` | 機關首長（若需高層核定） |
| 新增 `ROLE_APPLICANT` | 可獨立於 DEPT_USER，方便代理人設定 |
| 保留 `*` 萬用角色 | 支援「任何人都可辦理」的情境 |

---

4. 
## 二、POC 案例選擇建議

### 2.1 選擇：資產異動審核

**強烈建議選擇「資產異動審核」作為第一個 POC 案例**

| 評估維度 | 資產異動審核 | 路燈報修 |
|---------|-------------|---------|
| 步驟數 | 4 步驟 | 8+ 步驟（含故障確認） |
| 角色數 | 3 角色 | 5+ 角色 |
| 退回情境 | 單層退回 | 多層退回 + 補件 |
| 外部相依 | 無 | 需介接 1999、GIS |
| 代理人測試 | ✅ 易測試 | ✅ 可測試 |
| POC 複雜度 | 低 | 高 |
| 驗證核心假設 | ✅ 足夠 | 過度 |

### 2.2 資產異動審核流程定義

```json
{
  "initial_step": "step_applicant",
  "steps": [
    {
      "id": "step_applicant",
      "name": "申請人送審",
      "type": "normal",
      "role_code": "ROLE_DEPT_USER",
      "next": "step_manager",
      "reject_target": null
    },
    {
      "id": "step_manager",
      "name": "部門主管審核",
      "type": "normal",
      "role_code": "ROLE_DEPT_ADMIN",
      "next": "step_property",
      "reject_target": "step_applicant"
    },
    {
      "id": "step_property",
      "name": "財產管理審核",
      "type": "normal",
      "role_code": "ROLE_DEPT_ADMIN",
      "next": "step_end",
      "reject_target": "step_manager"
    },
    {
      "id": "step_end",
      "name": "結案",
      "type": "end",
      "role_code": null,
      "next": null
    }
  ]
}
```

### 2.3 POC 測試案例說明

| 案例 | 說明 | 驗證重點 |
|------|------|---------|
| 案例1 | 正常流程 | start → approve → approve → approve → end |
| 案例2 | 主管退回 | start → approve → reject → resubmit → approve → approve → end |
| 案例3 | 財產管理退回 | start → approve → approve → reject → resubmit → approve → end |
| 案例4 | 代理人動態覆寫 | 案例2 + 中途設定代理人 |
| 案例5 | 併發測試 | 同時兩個 approve 請求，只有一個成功 |

---

5.

## 三、POC 驗收標準選擇

### 3.1 建議選擇：B + A

**建議採用「A + B」組合：單元/整合測試 + REST API 手動呼叫**

| 選項 | 說明 | 建議 |
|------|------|------|
| A | 單元測試 + 整合測試跑通 | ✅ 必須 |
| B | REST API 可手動呼叫（Postman/curl） | ✅ 必須 |
| C | 接現有前端 | ❌ POC 階段不需要 |

### 3.2 理由

| 理由 | 說明 |
|------|------|
| **聚焦核心假設** | POC 要驗證的是「引擎與組織分離」是否可行，不是 UI |
| **節省時間** | 接前端需處理表單、路由、狀態顯示，約增加 50% 工時 |
| **彈性更大** | API 層測試可快速迭代，修改流程定義不需改前端 |
| **後續可銜接** | API 設計好後，前端隨時可接入 |

### 3.3 POC 交付物清單

| 交付物 | 說明 | 對應選項 |
|--------|------|---------|
| 單元測試程式碼 | WorkflowEngine + OrganizationAdapter 測試 | A |
| 整合測試程式碼 | 完整流程測試（含代理人） | A |
| REST API 端點 | POST /workflow/start, approve, reject, resubmit | B |
| Postman Collection | 可手動呼叫的測試腳本 | B |
| 測試報告 | 測試案例執行結果 | A+B |
| README | 如何啟動、測試、呼叫 API | B |

### 3.4 API 端點清單（POC 版）

| Method | Endpoint | 說明 |
|--------|----------|------|
| POST | `/api/poc/workflow/start` | 啟動資產異動流程 |
| POST | `/api/poc/workflow/approve` | 審核通過 |
| POST | `/api/poc/workflow/reject` | 審核退回 |
| POST | `/api/poc/workflow/resubmit` | 補件重送 |
| GET | `/api/poc/workflow/instance/{id}` | 查詢流程狀態 |
| GET | `/api/poc/workflow/history/{id}` | 查詢歷程 |
| POST | `/api/poc/delegate/set` | 設定代理人（POC 輔助用） |

---

6. 現有系統沒有這張表， POC 要一起建