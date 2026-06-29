> 既然 Phase 5a 已經讓 AI 知道了 **「不能做什麼（業務底線）」**，Phase 5b 將要解決 **「應該做什麼（業務語意）」**。

> 按照之前極具洞察力的規劃，要從兩個**絕對會有人維護**的地方萃取：

> 1. **Swagger 註解 (`@Schema`, `@Operation`)**
> 2. **JUnit 測試方法名 (`@Test should_xxx_when_yyy`)**



## 理解方向正確，但有一個實際情況需要先確認再決定優先順序： 

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex

# Check 1: How many @Schema annotations with descriptions exist?
grep -r '@Schema' backend/src/main/java --include="*.java" -l | wc -l
grep -r '@Schema.*description' backend/src/main/java --include="*.java" | wc -l

# Check 2: How many @Operation annotations?
grep -r '@Operation' backend/src/main/java --include="*.java" | wc -l

# Check 3: Are there actual Java unit test files with @Test methods?
find backend/src/test -name "*.java" | wc -l
find backend/src/test -name "*.java" | head -10

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex

# Check test method naming quality
grep -r '@Test' backend/src/test --include="*.java" -A1 | grep 'void ' | head -20

# Check @Operation content sample
grep -r '@Operation' backend/src/main/java --include="*.java" | head -8

兩個維度的資料都很優質。你的理解**完全正確**，但需要補充一個重要細節：

---

### `@Schema` / `@Operation` ← 高優先（字面意思直接可用）

你說的「應該做什麼」只說了一半。`@Schema` 的作用更精確：
- `@Schema(description = "公告 ID")` → 告訴 AI **這個欄位的業務意義**
- `@Operation(summary = "取消流程", description = "申請人主動取消...僅限申請人本人操作")` → 告訴 AI **這個 API 的業務限制**（注意這句：「僅限申請人本人操作」其實也是業務底線）

換句話說，`@Operation.description` 有時比 Phase 5a 的 `throw` 更能捕捉**存取控制類**的業務規則。兩者互補。

實作目標：把這些描述直接寫入現有的 `feature_contracts` 表的 `description` 欄位（加新欄），不必建新表。

---

### JUnit 測試方法名 ← 這個 codebase 的品質超乎預期

剛才看到的方法名品質非常高：
```
approve_shouldThrowPermissionException_whenWrongUser()
cancel_byNonApplicant_shouldThrowPermissionException()
reject_shouldThrowInvalidTransition_whenTargetNotAllowed()
```

這些完全符合「活文件」標準。但有一個技術問題：**`@Test` 在 `grep` 結果裡沒出現在 main，只在 test 目錄** — 現有的 tree-sitter-analyzer.py 只掃 main，Phase 5b 需要**額外掃 test**。

---

### 結論：正確，但有一個設計調整

不需要建新的 `business_rules` 表，應該這樣落地：

| 來源 | 存入哪裡 | 新增欄位 |
|------|---------|---------|
| `@Operation(summary, description)` | 現有 `feature_contracts` | `+ api_summary, api_description` |
| `@Schema(description)` on DTO fields | 現有 `feature_contracts.request_schema` / `response_schema` 加 JSDoc | 不需要新欄 |
| JUnit `@Test void method_name()` | 新表 `test_rules` | 全新 |

