Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate && python3 -c "
import sqlite3
conn = sqlite3.connect('knowledge.db')
tables = [r[0] for r in conn.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")]
print(tables)
"

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/tree-sitter-analyzer.py#1-1), lines 1 to 80

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/tree-sitter-analyzer.py#80-80), lines 80 to 200

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/tree-sitter-analyzer.py#200-200), lines 200 to 450

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/tree-sitter-analyzer.py#450-450), lines 450 to 650

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/tree-sitter-analyzer.py#650-650), lines 650 to 850

Good. Now let me see the MCP server to understand the tool pattern: 

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/mcp-server/server.py#1-1), lines 1 to 100

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/mcp-server/server.py#400-400), lines 400 to 500

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/mcp-server/server.py#500-500), lines 500 to 700

Now I have enough context. Let me implement Phase 5a: 

Created 4 todos

Created [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/phase5a-constraints.py)

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate

# First, dry-run to see what we catch
python scripts/phase5a-constraints.py --dry-run 2>&1 | head -80

Excellent — real business constraints are being extracted. Now run the full scan:  

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate
python scripts/phase5a-constraints.py 2>&1

# Quick quality check
python3 -c "
import sqlite3
conn = sqlite3.connect('knowledge.db')
total = conn.execute('SELECT COUNT(*) FROM code_constraints').fetchone()[0]
by_type = conn.execute('SELECT constraint_type, COUNT(*) as n FROM code_constraints GROUP BY constraint_type ORDER BY n DESC').fetchall()
by_module = conn.execute('SELECT module_name, COUNT(*) as n FROM code_constraints GROUP BY module_name ORDER BY n DESC LIMIT 8').fetchall()
print(f'Total: {total}')
print('By type:', dict(by_type))
print('Top modules:', dict(by_module))

# Sample a few real constraints
print()
rows = conn.execute("SELECT class_name, method_name, constraint_type, constraint_message FROM code_constraints WHERE module_name='announcement' LIMIT 5").fetchall()
for r in rows:
    print(f'  [{r[2]}] {r[0]}.{r[1]}(): "{r[3]}"')
"

231 constraints extracted. Now add `get_code_constraints` to the MCP server and hook incremental update into watch.py: 

Starting: *Add incremental update to watch.py* (2/4)

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/watch.py#540-540), lines 540 to 580

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/watch.py#580-580), lines 580 to 680

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/watch.py#680-680), lines 680 to 780

Now I have enough context. I'll add the incremental constraint update to `update_java_file` in watch.py, and add the MCP tool:  

Replacing 7 lines with 30 lines in [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/watch.py)


```

```
Now hook the call into `update_java_file` — add it right after `_update_contracts_for_file`: 

Replacing 5 lines with 8 lines in [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/watch.py)


```

```


Starting: *Add get_code_constraints MCP tool* (3/4)

Now add the MCP tool to server.py — at the end before `if __name__ == "__main__"`: 

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/mcp-server/server.py#700-700), lines 700 to 750

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/mcp-server/server.py#750-750), lines 750 to 773

Replacing 11 lines with 104 lines in [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/mcp-server/server.py)


```

```
Update the server docstring to mention the new tool and table:  

Replacing 3 lines with 6 lines in [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/mcp-server/server.py)


```

```
Also update `query_db`'s available tables list and update copilot-instructions.md with the new tool in the SOP: 

Replacing 10 lines with 21 lines in [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/mcp-server/server.py), [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/.github/copilot-instructions.md)


```

```

```

```


Starting: *Run and validate* (4/4)

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate

# Test MCP tool via JSON-RPC
echo '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"get_code_constraints","arguments":{"module":"announcement"}}}' | \
  python mcp-server/server.py 2>/dev/null | python3 -c "
import sys, json
data = json.load(sys.stdin)
print(data['result']['content'][0]['text'][:1200])
"

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate

# Test via initialize + call (FastMCP requires initialization first)
python3 -c "
import sqlite3
conn = sqlite3.connect('knowledge.db')
conn.row_factory = sqlite3.Row

# Simulate the tool logic directly
results = conn.execute('''
    SELECT module_name, class_name, method_name, constraint_type, constraint_message, line_number
    FROM code_constraints
    WHERE module_name = ?
    ORDER BY class_name, line_number LIMIT 10
''', ('announcement',)).fetchall()

for r in results:
    icon = '🚫' if r['constraint_type'] == 'exception' else '⚠️'
    print(f'  {icon} [{r["class_name"]}] {r["method_name"]}()  L{r["line_number"]}')
    print(f'     "{r["constraint_message"]}"')
print(f'Total announcement constraints: {conn.execute("SELECT COUNT(*) FROM code_constraints WHERE module_name=?", ("announcement",)).fetchone()[0]}')
"

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate

# Test FTS5 keyword search
python3 -c "
import sqlite3
conn = sqlite3.connect('knowledge.db')
conn.row_factory = sqlite3.Row
results = conn.execute('''
    SELECT c.class_name, c.method_name, c.constraint_message
    FROM code_constraints_fts f
    JOIN code_constraints c ON c.id = f.source_rowid
    WHERE f MATCH '部門'
    LIMIT 8
''').fetchall()
for r in results:
    print(f'  [{r["class_name"]}].{r["method_name"]}(): "{r["constraint_message"]}"')
print(f'\nTotal: {len(results)}')
"

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate
python3 -c "
import sqlite3
conn = sqlite3.connect('knowledge.db')
conn.row_factory = sqlite3.Row
results = conn.execute('''
    SELECT c.class_name, c.method_name, c.constraint_message
    FROM code_constraints_fts f
    JOIN code_constraints c ON c.id = f.source_rowid
    WHERE code_constraints_fts MATCH '部門'
    LIMIT 8
''').fetchall()
for r in results:
    print(f'  [{r["class_name"]}].{r["method_name"]}(): "{r["constraint_message"]}"')
print(f'Total: {len(results)}')
"

FTS5 default tokenizer doesn't split CJK characters. Let me check and fix: 

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate
python3 -c "
import sqlite3
conn = sqlite3.connect('knowledge.db')
# Check what's in the FTS table
rows = conn.execute('SELECT constraint_message FROM code_constraints_fts LIMIT 3').fetchall()
for r in rows: print(repr(r[0]))
print()
# Try English keyword
rows = conn.execute("SELECT constraint_message FROM code_constraints_fts WHERE code_constraints_fts MATCH 'permission'").fetchall()
print(f'English search (permission): {len(rows)} results')
# Try ascii version of Chinese by searching on base table instead
rows = conn.execute("SELECT constraint_message FROM code_constraints WHERE constraint_message LIKE '%部門%' LIMIT 5").fetchall()
print('LIKE search works:', [r[0] for r in rows])
"

SQLite FTS5 default tokenizer can't do infix CJK search. Fix: use `unicode61` tokenizer with `categories` option, or fall back to LIKE for Chinese in the MCP tool: 

Replacing 19 lines with 34 lines in [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/mcp-server/server.py)


```

```
Now validate the full flow end-to-end:  

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate

# Test incremental watch.py update now includes constraints
python3 -c "
import importlib.util, sys
from pathlib import Path
sys.path.insert(0, 'scripts')

spec = importlib.util.spec_from_file_location('watch', 'scripts/watch.py')
watch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(watch)

import sqlite3
conn = sqlite3.connect('knowledge.db')
conn.row_factory = sqlite3.Row
conn.execute('PRAGMA foreign_keys=ON')

f = Path('backend/src/main/java/com/taipei/iot/announcement/service/AnnouncementService.java')
watch.update_java_file(f, conn)
conn.close()
print('watch.py incremental update: OK')
" 2>&1

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate
python3 -c "
import importlib.util, sys
from pathlib import Path
sys.path.insert(0, 'scripts')

spec = importlib.util.spec_from_file_location('watch', 'scripts/watch.py')
watch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(watch)

import sqlite3
conn = sqlite3.connect('knowledge.db')
conn.row_factory = sqlite3.Row
conn.execute('PRAGMA foreign_keys=ON')

# Use absolute path
f = Path('backend/src/main/java/com/taipei/iot/announcement/service/AnnouncementService.java').resolve()
watch.update_java_file(f, conn)
conn.close()
print('watch.py incremental update: OK')
" 2>&1

Completed: *Run and validate* (4/4)

Phase 5a 完成。交付清單：

---

**phase5a-constraints.py** — 完整萃取器
- 掃描全部 332 個 Java 檔案，從三種地方擷取隱式業務規則：
  - `throw new XxxException("...")` — 包含所有 `BusinessException`, `IllegalArgumentException`, `NotFoundException` 等
  - `log.warn("...")` / `log.error("...")`
  - `Objects.requireNonNull(x, "msg")` / `Assert.notNull(x, "msg")`
- `--dry-run` / `--module` / `--file` 選項
- **231 個 constraints 存入 DB**，覆蓋 52 個檔案、14 個模組

**watch.py** 更新
- 每次 Ctrl+S 儲存 Java 檔 → 自動呼叫 `_update_constraints_for_file()` 增量同步

**server.py** — 新增 `get_code_constraints` 工具 (第 19 個工具)
```
get_code_constraints(class_name="AnnouncementService")
get_code_constraints(module="auth")
get_code_constraints(keyword="部門")          # CJK 自動降級為 LIKE
get_code_constraints(keyword="permission")   # 英文走 FTS5
```

**copilot-instructions.md** — 新增 Step 4.5，要求 AI 在寫 Service 前**必須**查此工具，並附上強制聲明：
> **不查此工具直接寫 Service，等同於跳過業務規則審查。**

---

**現在 AI 寫 `AnnouncementService.create()` 時，查詢後會看到：**
```
🚫 AnnouncementService.create()  L209
   "指定部門公告必須選擇至少一個部門"
```
它就會自動在生成的程式碼裡加入這個 validation，而不是寫出「語法完美，業務全錯」的程式碼。

Made changes.