理解方向完全正確。但有兩個**實作層面的陷阱**要在動手前先修正，否則會踩到我們之前踩過的坑：

---

### 陷阱 1：`content='business_rules'` FTS5 不能用

h4-org3.md 的 Phase 5a schema 寫了：
```sql
CREATE VIRTUAL TABLE code_constraints_fts USING fts5(
    ...,
    content='business_rules', content_rowid='id'   -- ⚠️ 危險
);
```

這正是之前導致 **「database disk image is malformed」** 的 `content=` 模式。Phase 3.5 已經踩過這個坑，最終改用 standalone FTS5 + 手動 INSERT。新表要沿用同樣的解法：
```sql
-- 正確做法：不加 content= 參數
CREATE VIRTUAL TABLE code_constraints_fts USING fts5(
    source_rowid, class_name, method_name, constraint_message
);
```

---

### 陷阱 2：Tree-sitter pseudo-code 的 API 不對

h4-org3.md 裡用的是：
```python
get_child_text(node, 'name')   # ← 這個函式不存在於我們的 codebase
node.text.decode('utf8')       # ← tree-sitter 0.25+ 不能直接 .text
```

實際上要用 watch.py / phase35-enhancer.py 已有的 helper：
```python
node_text(find_child(node, ["identifier"]), source_bytes)
node_text(str_literal_node, source_bytes).strip('"')
```

---

### 其他理解都是對的

- Phase 5a（Exception/Log）自動化、零維護 ✓  
- Phase 5b（`@Schema` + JUnit 方法名）是「活文件」不會腐爛 ✓  
- Phase 5c（ADR）小團隊低優先 ✓  
- watch.py 監控 `docs/**/*.md` 的思路 ✓  

---

