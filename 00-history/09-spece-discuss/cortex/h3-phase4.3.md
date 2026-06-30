Compacted conversation

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/watch.py#1-1), lines 1 to 80

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/phase35-enhancer.py#1-1), lines 1 to 80

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/watch.py#80-80), lines 80 to 200

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/phase35-enhancer.py#80-80), lines 80 to 250

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/phase35-enhancer.py#250-250), lines 250 to 380

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/watch.py#200-200), lines 200 to 350

Searched for files matching `**/CodeCortex/.vscode/tasks.json`, no matches

Searched for files matching `**/CodeCortex/.github/copilot-instructions.md`, no matches

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/watch.py#350-350), lines 350 to 450

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/phase35-enhancer.py#380-380), lines 380 to 480

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/watch.py#420-420), lines 420 to 560

Good, I have the full picture. The plan:
1. `scripts/generate-contract.py` — parses controller(s) → updates DB + **writes real `.ts` type files** to `frontend/src/types/generated/`
2. `.vscode/tasks.json` — VS Code tasks to invoke it (including "active file" shortcut)
3. `.github/copilot-instructions.md` — instructs the AI to self-trigger the script after generating code 

Created 4 todos

Created [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/generate-contract.py)

Starting: *Create .vscode/tasks.json* (2/4)

Created [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/.vscode/tasks.json)

Starting: *Create .github/copilot-instructions.md* (3/4)

Ran terminal command:  ls /home/kevin/workspaces/kevin-projects/CodeCortex/.github/

Created [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/.github/copilot-instructions.md)

Starting: *Test end-to-end* (4/4)

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate

# Test 1: single controller → should generate .ts file
python scripts/generate-contract.py \
  --file backend/src/main/java/com/taipei/iot/announcement/controller/AnnouncementController.java 2>&1

Ran terminal command:  cat /home/kevin/workspaces/kevin-projects/CodeCortex/frontend/src/types/generated/announcement.contracts.ts | head -60

Fields show empty types — the DB `fields_json` doesn't store the `type` key the same way `fields_to_ts_interface` expects. Let me check: 

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate
python3 -c "
import sqlite3, json
conn = sqlite3.connect('knowledge.db')
row = conn.execute("SELECT fields_json FROM classes WHERE class_name = 'AnnouncementResponse'").fetchone()
if row:
    fields = json.loads(row[0])
    print(json.dumps(fields[:3], indent=2, ensure_ascii=False))
"

Ran terminal command:  find /home/kevin/workspaces/kevin-projects/CodeCortex/backend -name "AnnouncementResponse.java" 2>/dev/null | head -1 | xargs head -30 2>/dev/null

The empty types are a DB data quality issue from the initial parse. The fix: enrich `type_dict` by re-parsing DTO source files directly, so new AI-generated DTOs work even before they're in the DB. 

Replacing 5 lines with 110 lines in [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/generate-contract.py)


```

```
Now hook the enrichment into `process_controller_file`: 

Replacing 3 lines with 10 lines in [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/generate-contract.py)


```

```


Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate

python scripts/generate-contract.py \
  --file backend/src/main/java/com/taipei/iot/announcement/controller/AnnouncementController.java 2>&1

echo "---"
head -55 frontend/src/types/generated/announcement.contracts.ts | tail -25

Types are now fully resolved. Run a full sweep to generate all modules: 

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate
python scripts/generate-contract.py --all 2>&1

Completed: *Test end-to-end* (4/4)

All done. 11 TypeScript contract files generated across 10 modules. Here's what was built:

---

### 新增三個檔案

**generate-contract.py** — 核心腳本  
- `--file FooController.java` — AI 生成完程式碼後立即呼叫（最常用）
- `--module <name>` — 重生整個模組
- `--all` — 全量重生
- `--dry-run` — 預覽 TypeScript interface，不寫入磁碟/DB
- **關鍵增強**：`_enrich_type_dict_from_source()` — 直接用 tree-sitter 從 `.java` 原始碼重新解析欄位型別，解決了兩個問題：(1) DB 舊資料 `type: ""` 的缺陷；(2) AI 剛生成的新 DTO 尚未存入 DB

**tasks.json** — 五個 VS Code Task  
- `⇧⌘B` → **Generate Contract (active file)** ← 預設 build task，直接對當前開啟的 Controller 執行
- Module prompt / Generate All / Dry-run / Start File Watcher

**copilot-instructions.md** — AI 行為指令  
- 告知 Copilot Agent：每次生成新 Controller 後**必須**呼叫 `generate-contract.py`，確保知識庫「自我生長」

---

### 自我生長效果
```
AI 寫完 Controller + DTO
     ↓
python scripts/generate-contract.py --file XxxController.java
     ↓
feature_contracts 表更新 + contracts_fts 重建
frontend/src/types/generated/<module>.contracts.ts 生成
     ↓
下次 get_feature_contract() 拿到精確的 TS 型別
前端直接 import type { XxxRequest } from '@/types/generated/xxx.contracts'
```

Made changes.