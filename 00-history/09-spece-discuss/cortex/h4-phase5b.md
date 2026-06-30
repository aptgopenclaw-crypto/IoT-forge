反饋的藍圖完全正確。在動手前只需確認兩個陷阱：

1. `test_rules_fts` 用了 `content='test_rules'` → 和 Phase 3.5 一樣要改成 standalone FTS5
2. 偽碼裡的 `get_child_text` 不存在 → 用 `node_text(find_child(...))`

開始實作： 

Created 5 todos

Created [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/phase5b-semantics.py)

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate

# Dry-run to preview quality first
python scripts/phase5b-semantics.py --dry-run 2>&1 | head -80

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate

# Preview test rules quality  
python scripts/phase5b-semantics.py --dry-run --skip-swagger 2>&1 | head -60

Both sources look excellent. Run the full write: 

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate
python scripts/phase5b-semantics.py 2>&1

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate
python3 -c "
import sqlite3
conn = sqlite3.connect('knowledge.db')
conn.row_factory = sqlite3.Row

# Stats
ops = conn.execute('SELECT COUNT(*) FROM feature_contracts WHERE api_summary != ""').fetchone()[0]
jsdoc = conn.execute('SELECT COUNT(*) FROM feature_contracts WHERE request_schema_jsdoc != ""').fetchone()[0]
tests = conn.execute('SELECT COUNT(*) FROM test_rules').fetchone()[0]
print(f'@Operation updated : {ops}')
print(f'JSDoc enriched     : {jsdoc}')
print(f'Test rules         : {tests}')

# Sample @Operation
print()
r = conn.execute("SELECT feature_name, api_summary, api_description FROM feature_contracts WHERE api_summary != '' LIMIT 3").fetchall()
for row in r:
    print(f'  [{row["feature_name"]}]')
    print(f'   summary: {row["api_summary"]}')
    print(f'   desc:    {row["api_description"][:60]}')

# Sample JSDoc schema
print()
r = conn.execute("SELECT feature_name, request_schema_jsdoc FROM feature_contracts WHERE request_schema_jsdoc LIKE '%/**%' LIMIT 2").fetchall()
for row in r:
    print(f'  [{row["feature_name"]}]')
    print(row['request_schema_jsdoc'][:300])
    print()

# Sample test rules
print()
r = conn.execute('SELECT module_name, class_name, readable_rule FROM test_rules WHERE module_name="workflow" LIMIT 5').fetchall()
for row in r:
    print(f'  [{row["module_name"]}] {row["class_name"]}: {row["readable_rule"]}')
"

All three data sources populated. Now add the MCP tool and update `get_feature_contract`, then update copilot-instructions:  

Starting: *Enhance generate-contract.py with JSDoc* (2/5)

`generate-contract.py` — emit `request_schema_jsdoc` instead of `request_schema` when available:  

Replacing 4 lines with 4 lines in [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/generate-contract.py)

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/generate-contract.py#90-90), lines 90 to 115

The `_format_ts_block` function reads from contract dicts returned by `extract_method_contracts`. These dicts don't include the JSDoc columns from the DB. The cleanest fix: in `process_controller_file`, after getting contracts from `extract_method_contracts`, look up the DB's JSDoc columns and use them if available.   

Replacing 5 lines with 8 lines in [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/generate-contract.py)


```

```
Now load JSDoc columns when reading from DB in `process_controller_file`:  

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/generate-contract.py#195-195), lines 195 to 230

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/generate-contract.py#280-280), lines 280 to 320

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/generate-contract.py#330-330), lines 330 to 380

Now add JSDoc enrichment after contracts are extracted — load from DB after write:   

Replacing 2 lines with 16 lines in [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/generate-contract.py)


```

```
Now add `get_test_rules` MCP tool and update `get_feature_contract`:   

Replacing 1 lines with 4 lines in [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/mcp-server/server.py)


```

```
Update `get_feature_contract` to show `api_summary` and `api_description`:  

Replacing 11 lines with 15 lines in [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/mcp-server/server.py)


```

```


Replacing 7 lines with 12 lines in [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/mcp-server/server.py)


```

```


Replacing 15 lines with 15 lines in [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/mcp-server/server.py)


```

```
Now add the `get_test_rules` tool before the entry point:   

Replacing 4 lines with 94 lines in [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/mcp-server/server.py)


```

```
Update copilot-instructions.md SOP:   

Replacing 3 lines with 12 lines in [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/.github/copilot-instructions.md)


```

```
Final validation — regenerate announcement contracts to see JSDoc in .ts file:   

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate

# Regenerate contracts with JSDoc
python scripts/generate-contract.py --module announcement 2>&1

echo "---"
# Show JSDoc in generated .ts
grep -A1 '/\*\*' frontend/src/types/generated/announcement.contracts.ts | head -30

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/generate-contract.py#108-108), lines 108 to 165

The indentation is off — the `for schema_field` loop has wrong indent (4+4 spaces vs 8):   

Replacing 10 lines with 10 lines in [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/generate-contract.py)


```

```


Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate

python scripts/generate-contract.py --module announcement 2>&1
echo "---"
grep -A1 '/\*\*' frontend/src/types/generated/announcement.contracts.ts | head -24

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate
# Check if JSDoc was written
python3 -c "
import sqlite3
conn = sqlite3.connect('knowledge.db')
r = conn.execute("SELECT request_schema_jsdoc FROM feature_contracts WHERE feature_name='AnnouncementController.update'").fetchone()
print(r[0][:400] if r else 'not found')
"

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate
python3 -c "
import sqlite3
conn = sqlite3.connect('knowledge.db')
# check what cols exist
cols = [r[1] for r in conn.execute('PRAGMA table_info(feature_contracts)')]
print('Columns:', cols)
# how many jsdoc rows
n = conn.execute("SELECT COUNT(*) FROM feature_contracts WHERE request_schema_jsdoc != ''").fetchone()[0]
print('JSDoc rows:', n)
r = conn.execute("SELECT feature_name, request_schema_jsdoc FROM feature_contracts WHERE request_schema_jsdoc != '' LIMIT 1").fetchone()
if r:
    print(r[0])
    print(r[1][:300])
"

JSDoc is working. Only 1 row has it because `AnnouncementRequest` (the main DTO) reuses the same class name across modules and phase5b found @Schema on `PlatformAnnouncementRequest`. Now regenerate all to pick it up in the .ts file:   

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate
python scripts/generate-contract.py --all 2>&1 | tail -15

echo ""
echo "=== Sample JSDoc in platform.contracts.ts ==="
grep -B1 -A2 '/\*\*' frontend/src/types/generated/platform.contracts.ts | head -30

Ran terminal command:  cat /home/kevin/workspaces/kevin-projects/CodeCortex/frontend/src/types/generated/platform.contracts.ts | grep -A20 'PlatformAnnouncementRequest'

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate
python3 -c "
import sqlite3
conn = sqlite3.connect('knowledge.db')
conn.row_factory = sqlite3.Row

# Check what the db_contracts query returns for platform module
rows = list(conn.execute('''
    SELECT feature_name, request_type,
           COALESCE(NULLIF(request_schema_jsdoc,""), request_schema) AS request_schema_jsdoc
    FROM feature_contracts WHERE module = "platform" AND request_type != ""
'''))
for r in rows:
    has_jsdoc = '/**' in (r['request_schema_jsdoc'] or '')
    print(f'  [{r["feature_name"]}]  req_type={r["request_type"]}  has_jsdoc={has_jsdoc}')
"

Compacted conversation

Searched for text `COALESCE.*request_schema_jsdoc` (`**/scripts/generate-contract.py`), 1 result

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/generate-contract.py#370-370), lines 370 to 400

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate
python3 -c "
import sqlite3
conn = sqlite3.connect('knowledge.db')
conn.row_factory = sqlite3.Row

# Direct check for this row
r = conn.execute("""
    SELECT feature_name,
           request_schema_jsdoc,
           LENGTH(request_schema_jsdoc) as jsdoc_len,
           NULLIF(request_schema_jsdoc, '') as nullif_result
    FROM feature_contracts
    WHERE feature_name = 'PlatformAnnouncementController.update'
""").fetchone()
print('jsdoc_len:', r['jsdoc_len'])
print('nullif_result is None:', r['nullif_result'] is None)
print('first 100 chars:', (r['request_schema_jsdoc'] or '')[:100])
"

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate
python3 -c "
import sqlite3
conn = sqlite3.connect('knowledge.db')
# Find the row with JSDoc content
rows = conn.execute("SELECT feature_name, module, LENGTH(request_schema_jsdoc) FROM feature_contracts WHERE LENGTH(request_schema_jsdoc) > 0").fetchall()
print('Rows with request_schema_jsdoc:', rows)
rows2 = conn.execute("SELECT feature_name, module, LENGTH(response_schema_jsdoc) FROM feature_contracts WHERE LENGTH(response_schema_jsdoc) > 0").fetchall()
print('Rows with response_schema_jsdoc:', rows2)
"

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate
# Re-run phase5b with --db flag to write to DB
python scripts/phase5b-semantics.py --db 2>&1 | tail -20

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate
python scripts/phase5b-semantics.py --db knowledge.db 2>&1 | tail -20

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate
python3 -c "
import sqlite3
conn = sqlite3.connect('knowledge.db')
rows = conn.execute("SELECT feature_name, module, LENGTH(request_schema_jsdoc) FROM feature_contracts WHERE LENGTH(request_schema_jsdoc) > 0").fetchall()
for r in rows: print(r)
"
python scripts/generate-contract.py --all 2>&1 | tail -5
echo ""
grep -A15 'PlatformAnnouncementRequest' frontend/src/types/generated/platform.contracts.ts | head -20

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate
python3 -c "
import sqlite3, re
conn = sqlite3.connect('knowledge.db')
r = conn.execute("""
    SELECT request_schema_jsdoc, request_schema
    FROM feature_contracts
    WHERE feature_name='PlatformAnnouncementController.update'
""").fetchone()
schema = r[0]
print('JSDoc schema (repr):', repr(schema[:200]))
print()
# test the regex
matches = list(re.finditer(
    r'((?:/\*\*[^*]*\*/\s*)?interface\s+\w[\w<>, ]*\s*\{[^}]*\})',
    schema, re.DOTALL
))
print('Regex matches:', len(matches))
if matches:
    print('First match:', matches[0].group(1)[:200])
"

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate
python3 -c "
import sqlite3, re
conn = sqlite3.connect('knowledge.db')
rows = conn.execute("SELECT id, feature_name, module, LENGTH(request_schema_jsdoc) FROM feature_contracts WHERE feature_name='PlatformAnnouncementController.update'").fetchall()
print('All matching rows:')
for r in rows: print(' ', r)

# Get the platform one
r = conn.execute("SELECT request_schema_jsdoc FROM feature_contracts WHERE feature_name='PlatformAnnouncementController.update' AND module='platform'").fetchone()
schema = r[0]
print()
print('JSDoc len:', len(schema))
print('First 200:', schema[:200])
print()
# test regex
matches = list(re.finditer(r'((?:/\*\*[^*]*\*/\s*)?interface\s+\w[\w<>, ]*\s*\{[^}]*\})', schema, re.DOTALL))
print('Regex matches:', len(matches))
if matches:
    print(matches[0].group(1)[:400])
"

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate
python3 -c "
import sqlite3
conn = sqlite3.connect('knowledge.db')
# check what phase5b writes
rows = conn.execute('''
    SELECT feature_name, module, 
           request_schema_jsdoc IS NOT NULL AND request_schema_jsdoc != "" as has_req,
           response_schema_jsdoc IS NOT NULL AND response_schema_jsdoc != "" as has_resp
    FROM feature_contracts 
    WHERE request_schema_jsdoc IS NOT NULL AND request_schema_jsdoc != ""
       OR response_schema_jsdoc IS NOT NULL AND response_schema_jsdoc != ""
''').fetchall()
print('JSDoc rows:', len(rows))
for r in rows:
    print(' ', r)
"

Searched for text `request_schema_jsdoc` (`**/scripts/phase5b-semantics.py`), 3 results

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/phase5b-semantics.py#490-490), lines 490 to 580

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/phase5b-semantics.py#620-620), lines 620 to 660

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/phase5b-semantics.py#660-660), lines 660 to 716

Searched for text `def rebuild_schema_with_jsdoc` (`**/scripts/phase5b-semantics.py`), 1 result

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/phase5b-semantics.py#294-294), lines 294 to 360

Searched for text `def extract_schema_field_docs` (`**/scripts/phase5b-semantics.py`), 1 result

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/phase5b-semantics.py#233-233), lines 233 to 293

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate
# Find Java files with @Schema annotation
grep -rl "@Schema" backend/src/main/java --include="*.java" | head -10

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate
python3 -c "
import importlib.util, sys
from pathlib import Path

spec = importlib.util.spec_from_file_location('phase5b', 'scripts/phase5b-semantics.py')
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

# Test extract_schema_field_docs on AnnouncementRequest
result = mod.extract_schema_field_docs(Path('backend/src/main/java/com/taipei/iot/announcement/dto/AnnouncementRequest.java'))
print('AnnouncementRequest docs:', result)

# Test on workflow
result2 = mod.extract_schema_field_docs(Path('backend/src/main/java/com/taipei/iot/workflow/dto/WorkflowApproveRequest.java'))
print('WorkflowApproveRequest docs:', result2)
"

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/phase5b-semantics.py#425-425), lines 425 to 455

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate
python3 -c "
import importlib.util, sqlite3
from pathlib import Path

spec = importlib.util.spec_from_file_location('phase5b', 'scripts/phase5b-semantics.py')
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

conn = sqlite3.connect('knowledge.db')
conn.row_factory = sqlite3.Row

# Check if announcement module has field_docs
all_field_docs = {}
for java_file in Path('backend/src/main/java/com/taipei/iot/announcement').rglob('*.java'):
    docs = mod.extract_schema_field_docs(java_file)
    all_field_docs.update(docs)

print('all_field_docs keys:', list(all_field_docs.keys()))

# Check announcement contracts
contracts = conn.execute("SELECT id, feature_name, request_type, LENGTH(request_schema) FROM feature_contracts WHERE module = 'announcement'").fetchall()
print('announcement contracts:', [(r['feature_name'], r['request_type'], r[3]) for r in contracts])
"

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate
python3 -c "
import importlib.util, sqlite3
from pathlib import Path

spec = importlib.util.spec_from_file_location('phase5b', 'scripts/phase5b-semantics.py')
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

conn = sqlite3.connect('knowledge.db')
conn.row_factory = sqlite3.Row

# Build field docs for announcement  
all_field_docs = {}
for java_file in Path('backend/src/main/java/com/taipei/iot/announcement').rglob('*.java'):
    docs = mod.extract_schema_field_docs(java_file)
    all_field_docs.update(docs)

# Manually call write_schema_jsdoc
n = mod.write_schema_jsdoc(conn, all_field_docs, 'announcement', dry_run=False)
print('Enriched:', n)

# Check result
rows = conn.execute("SELECT feature_name, LENGTH(request_schema_jsdoc) FROM feature_contracts WHERE module = 'announcement' AND LENGTH(request_schema_jsdoc) > 0").fetchall()
print('Rows now with JSDoc:', rows)
"

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate
# Check current DB state
python3 -c "
import sqlite3
conn = sqlite3.connect('knowledge.db')
rows = conn.execute("SELECT feature_name, module, LENGTH(request_schema_jsdoc) FROM feature_contracts WHERE LENGTH(request_schema_jsdoc) > 0").fetchall()
print('JSDoc rows:', len(rows))
for r in rows: print(' ', r)
"
# Now run phase5b properly
python scripts/phase5b-semantics.py --db knowledge.db 2>&1 | grep -E 'JSDoc|Done|enriched'

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate
python3 -c "
import sqlite3
conn = sqlite3.connect('knowledge.db')
rows = conn.execute("SELECT feature_name, module, LENGTH(request_schema_jsdoc) FROM feature_contracts WHERE LENGTH(request_schema_jsdoc) > 0").fetchall()
print('JSDoc rows after phase5b:', len(rows))
for r in rows: print(' ', r)
"

python scripts/generate-contract.py --module announcement --module platform 2>&1 | tail -8
echo ""
grep -A15 'export interface AnnouncementRequest' frontend/src/types/generated/announcement.contracts.ts | head -20

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate
python3 -c "
import sqlite3
conn = sqlite3.connect('knowledge.db')
r = conn.execute("SELECT request_schema_jsdoc FROM feature_contracts WHERE feature_name='AnnouncementController.update'").fetchone()
print(repr(r[0]))
"

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate
python3 -c "
import sqlite3, re, importlib.util
from pathlib import Path

spec = importlib.util.spec_from_file_location('gc', 'scripts/generate-contract.py')
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

conn = sqlite3.connect('knowledge.db')
conn.row_factory = sqlite3.Row

# Get the db_contracts for announcement module like process_controller_file does
db_contracts = [
    dict(r) for r in conn.execute(
        'SELECT feature_name, http_method, endpoint, request_type, response_type, '
        'request_schema, response_schema, '
        "COALESCE(NULLIF(request_schema_jsdoc,''), request_schema) AS request_schema_jsdoc, "
        "COALESCE(NULLIF(response_schema_jsdoc,''), response_schema) AS response_schema_jsdoc "
        "FROM feature_contracts WHERE module = 'announcement'",
    )
]

# Find the update contract
update_c = next((c for c in db_contracts if 'update' in c['feature_name']), None)
if update_c:
    print('has jsdoc key:', 'request_schema_jsdoc' in update_c)
    print('jsdoc value length:', len(update_c.get('request_schema_jsdoc', '') or ''))
    print('jsdoc first 100:', (update_c.get('request_schema_jsdoc') or '')[:100])
else:
    print('update contract not found')
" 2>&1

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/generate-contract.py#75-75), lines 75 to 165

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate
python3 -c "
import re, sqlite3

conn = sqlite3.connect('knowledge.db')
r = conn.execute("SELECT request_schema_jsdoc FROM feature_contracts WHERE feature_name='AnnouncementController.update'").fetchone()
schema = r[0]

# Test the exact regex used in _format_ts_block
matches = list(re.finditer(
    r'((?:/\*\*[^*]*\*/\s*)?interface\s+\w[\w<>, ]*\s*\{[^}]*\})',
    schema,
    re.DOTALL
))
print('Matches:', len(matches))
for m in matches:
    print('--- match ---')
    print(m.group(1)[:300])
"

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate
python3 -c "
import sqlite3, re

conn = sqlite3.connect('knowledge.db')
conn.row_factory = sqlite3.Row

# Reproduce _format_ts_block for announcement module
db_contracts = [
    dict(r) for r in conn.execute(
        'SELECT feature_name, http_method, endpoint, request_type, response_type, '
        'request_schema, response_schema, '
        "COALESCE(NULLIF(request_schema_jsdoc,''), request_schema) AS request_schema_jsdoc, "
        "COALESCE(NULLIF(response_schema_jsdoc,''), response_schema) AS response_schema_jsdoc "
        "FROM feature_contracts WHERE module = 'announcement'",
    )
]

for c in db_contracts:
    if c['feature_name'] == 'AnnouncementController.update':
        jsdoc = c.get('request_schema_jsdoc', '')
        plain = c.get('request_schema', '')
        print('update contract jsdoc len:', len(jsdoc or ''))
        print('update contract plain len:', len(plain or ''))
        print('jsdoc != plain:', jsdoc != plain)
        print('jsdoc starts with JSDoc:', '/**' in (jsdoc or ''))
        break
"

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate
python3 -c "
import sqlite3, re, sys
from pathlib import Path
sys.path.insert(0, 'scripts')

# Simulate _format_ts_block for announcement
conn = sqlite3.connect('knowledge.db')
conn.row_factory = sqlite3.Row

db_contracts = [
    dict(r) for r in conn.execute(
        'SELECT feature_name, http_method, endpoint, request_type, response_type, '
        'request_schema, response_schema, '
        "COALESCE(NULLIF(request_schema_jsdoc,''), request_schema) AS request_schema_jsdoc, "
        "COALESCE(NULLIF(response_schema_jsdoc,''), response_schema) AS response_schema_jsdoc "
        "FROM feature_contracts WHERE module = 'announcement'",
    )
]

# Test _format_ts_block manually
emitted_interfaces = set()
blocks = []

for c in db_contracts:
    if 'update' not in c['feature_name']:
        continue
    print('Processing:', c['feature_name'])
    for schema_field, type_field in [('request_schema', 'request_type'), ('response_schema', 'response_type')]:
        jsdoc_key = schema_field.replace('_schema', '_schema_jsdoc')
        schema = (c.get(jsdoc_key) or c.get(schema_field, '')).strip()
        type_name = (c.get(type_field) or '').strip()
        print(f'  schema_field={schema_field}, type_name={type_name!r}, schema_len={len(schema)}')
        if schema and type_name:
            matches = list(re.finditer(r'((?:/\*\*[^*]*\*/\s*)?interface\s+\w[\w<>, ]*\s*\{[^}]*\})', schema, re.DOTALL))
            print(f'  regex matches: {len(matches)}')
            for m in matches:
                iface_name_m = re.search(r"interface\s+(\w+)", m.group(1))
                iname = iface_name_m.group(1) if iface_name_m else ''
                print(f'  iface: {iname}, in_emitted: {iname in emitted_interfaces}')
                print(f'  first 200: {m.group(1)[:200]}')
"

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate
python3 -c "
import sqlite3, re

conn = sqlite3.connect('knowledge.db')
conn.row_factory = sqlite3.Row

db_contracts = list(conn.execute(
    'SELECT feature_name, http_method, endpoint, request_type, response_type, '
    'request_schema, response_schema, '
    "COALESCE(NULLIF(request_schema_jsdoc,''), request_schema) AS request_schema_jsdoc, "
    "COALESCE(NULLIF(response_schema_jsdoc,''), response_schema) AS response_schema_jsdoc "
    "FROM feature_contracts WHERE module = 'announcement'",
))

emitted_interfaces = set()
for c in db_contracts:
    c = dict(c)
    for schema_field, type_field in [('request_schema', 'request_type'), ('response_schema', 'response_type')]:
        jsdoc_key = schema_field.replace('_schema', '_schema_jsdoc')
        schema = (c.get(jsdoc_key) or c.get(schema_field, '')).strip()
        type_name = (c.get(type_field) or '').strip()
        if not schema or not type_name:
            continue
        if schema.startswith('// ') and '\n' not in schema:
            continue
        for m in re.finditer(r'((?:/\*\*[^*]*\*/\s*)?interface\s+\w[\w<>, ]*\s*\{[^}]*\})', schema, re.DOTALL):
            iname_m = re.search(r'interface\s+(\w+)', m.group(1))
            iname = iname_m.group(1) if iname_m else ''
            has_jsdoc = '/**' in m.group(1)
            in_emitted = iname in emitted_interfaces
            if iname:
                emitted_interfaces.add(iname)
            if iname == 'AnnouncementRequest':
                print(f'AnnouncementRequest found in {c["feature_name"]} ({schema_field})')
                print(f'  has_jsdoc={has_jsdoc}, was_already_emitted={in_emitted}')
"

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/generate-contract.py#290-290), lines 290 to 370

Found it. The `process_controller_file` does a `DELETE` + `INSERT` without JSDoc columns. The INSERT only writes the 8 basic columns, which means `request_schema_jsdoc` gets reset to the default `''`. Then the subsequent `SELECT` with COALESCE falls back to the plain schema. 

Replacing 17 lines with 28 lines in [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/generate-contract.py)


```

```


Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate
# Regenerate all
python scripts/generate-contract.py --all 2>&1 | tail -8

echo ""
# Verify JSDoc in announcement.contracts.ts
grep -A20 'export interface AnnouncementRequest' frontend/src/types/generated/announcement.contracts.ts | head -20

JSDoc is now rendering correctly in the TypeScript output. Here's a summary of what was completed in this session:

**Completed the remaining Phase 5b tasks:**

1. **copilot-instructions.md — Step 4.6 added**: The "查詢測試邊界" step now appears between Step 4.5 and Step 5, instructing the AI agent to call `get_test_rules()` before modifying any Service logic.

2. **server.py — `get_test_rules` tool added**: The 20th MCP tool, with dual CJK/FTS5 search paths and grouped output by class.

3. **generate-contract.py — JSDoc preservation bug fixed**: The DELETE+INSERT cycle was clearing `request_schema_jsdoc`/`response_schema_jsdoc` columns. The fix reads the existing JSDoc values before deleting and re-inserts them, so Phase 5b enrichment is preserved across re-generations.

**Current state of announcement.contracts.ts** now shows full `/** description */` JSDoc on all `AnnouncementRequest` fields — sourced from `@Schema(description=...)` annotations in the Java DTO.

是的，全部完成了。清單上所有項目：

| 任務 | 狀態 |
|------|------|
| phase5b-semantics.py 建立 | ✅ |
| generate-contract.py JSDoc 支援 | ✅（含修復 DELETE+INSERT 清除 JSDoc 的 bug）|
| `get_test_rules` MCP tool | ✅ |
| copilot-instructions.md Step 4.6 | ✅ |
| 驗證執行 | ✅ |

最後修復的關鍵 bug：generate-contract.py 每次執行都會 DELETE+INSERT，導致 Phase 5b 寫入的 `request_schema_jsdoc` 被清空。現在改為先讀取既有 JSDoc 值再保留寫回，`AnnouncementRequest` 的所有欄位 JSDoc 已正確輸出至 announcement.contracts.ts。