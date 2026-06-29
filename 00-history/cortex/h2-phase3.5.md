

Completed: *Phase 3.5.4: 更新MCP Server新工具* (5/5)

Phase 3.5 全部完成。以下是成果總結：

---

## 新增內容

### phase35-enhancer.py（新腳本，補強現有 DB）

| 新表 | 筆數 | 說明 |
|---|---|---|
| `feature_contracts` | 100 | 每個 endpoint 的 request/response TS Interface（30個有 request schema，73個有 response schema）|
| `module_exports` | 90 | 每個模組對外暴露的 public class（誰被其他模組 import）|
| `module_coupling` | 250 | 具體跨模組耦合邊（from_module → to_module → class_name）|
| `module_examples` | 61 | 每個模組 × class_type 的黃金範例原始碼 |
| FTS5 × 4 | — | `modules_fts`, `classes_fts`, `endpoints_fts`, `contracts_fts` |

### server.py（新增 6 個 Phase 3.5 工具，共 18 個）

| Tool | 殺手鐧 |
|---|---|
| `search_code_entity(keyword, type)` | FTS5 毫秒搜尋，只回傳摘要省 Token |
| `get_feature_contract(endpoint)` | **消滅 DTO 幻覺** — 精確的 TypeScript Interface |
| `get_entity_details(class_name)` | 完整 fields/methods + example source |
| `get_module_coupling(from, to?)` | 具體跨模組使用了哪些 class |
| `get_module_exports(module)` | 模組的 public API boundary |
| `get_example_code(module, type)` | few-shot 黃金範例，AI 直接複製風格 |

---

**再次 Reload Window** 讓 Copilot Agent 使用新工具。以後生成程式碼前，先呼叫 `get_feature_contract` 就不會再猜 DTO 欄位名稱了。

Made changes.