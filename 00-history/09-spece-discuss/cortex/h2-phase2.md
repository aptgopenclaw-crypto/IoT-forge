

Phase 2 完成！成果如下：

## ✅ Phase 2 完成 — 前端知識已進 knowledge.db

| 新增資料 | 數量 | 說明 |
|---|---|---|
| **frontend_api_functions** | 121 | API 呼叫函數，含 HTTP method + path |
| **frontend_stores** | 10 | Pinia stores，含使用的 API 清單 |
| **frontend_views** | 31 | Vue views，含使用的 stores/API/components |
| **frontend_components** | 19 | 共用元件 |
| **frontend_routes** | 31 | 路由（7 Platform + 24 Tenant） |
| **fe_be_bindings** | 85 | **前後端 endpoint 對應關係** |

### 最有價值的資料：FE→BE 覆蓋率 **83%**

```
Backend 100 endpoints → 83 個已有前端 API 函數對應
```

---

### AI Agent 現在能做到

```sql
-- "我需要公告模組，前後端完整清單給我"
SELECT fe.name, fe.http_method, fe.api_path,
       be.controller, be.method
FROM fe_be_bindings b
JOIN frontend_api_functions fe ON fe.name = b.fe_function
JOIN endpoints be ON be.id = b.be_endpoint_id
WHERE fe.module = 'announcement';

-- "workflow 模組需要哪些 Pinia stores 和 routes?"
SELECT * FROM frontend_stores WHERE module = 'workflow';
SELECT * FROM frontend_routes WHERE module = 'workflow';

-- "要用 assettransfer，transitive 依賴是哪些後端模組?"
SELECT depends_on FROM module_deps WHERE module = 'assettransfer';
```

---

| 腳本 | 指令 |
|---|---|
| 重新解析後端 | `python scripts/tree-sitter-analyzer.py` |
| 重新解析前端 | `python scripts/frontend-analyzer.py` |

Phase 3（MCP Server）要繼續嗎？讓 Copilot Agent 可以直接 call tool 查這個 DB。

Made changes.