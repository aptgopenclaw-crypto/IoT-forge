Ran terminal command:  ls /home/kevin/workspaces/kevin-projects/CodeCortex/.git/hooks/ && cat /home/kevin/workspaces/kevin-projects/CodeCortex/.git/hooks/pre-commit 2>/dev/null || echo "(no pre-commit hook)"

Ran terminal command:  git rev-parse --git-dir 2>&1

Git repo 在上層 `kevin-projects/`，所以用 project-local hooks 目錄（可以 commit 進 repo）是更乾淨的做法： 

Ran terminal command:  ls /home/kevin/workspaces/kevin-projects/.git/hooks/pre-commit 2>/dev/null || echo "(none)"

Ran terminal command:  mkdir -p /home/kevin/workspaces/kevin-projects/CodeCortex/.githooks

Created [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/.githooks/pre-commit)

Created [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/pre-commit-sync.py)

Ran terminal command:  chmod +x /home/kevin/workspaces/kevin-projects/CodeCortex/.githooks/pre-commit

# Configure git to use project-local hooks (scoped to this repo only)
cd /home/kevin/workspaces/kevin-projects && git config core.hooksPath CodeCortex/.githooks
git config core.hooksPath  # verify

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate

# Dry-run the sync script directly with a real Java file
python scripts/pre-commit-sync.py \
  --db knowledge.db \
  --java backend/src/main/java/com/taipei/iot/announcement/controller/AnnouncementController.java

Relative paths from the git hook need to be resolved against repo root. The sync script receives paths from git, which are relative to the repo root (parent dir). Fix by resolving paths relative to the repo root: 

Replacing 2 lines with 2 lines in [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/pre-commit-sync.py)


```

```


Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate && python scripts/pre-commit-sync.py \
  --db knowledge.db \
  --java backend/src/main/java/com/taipei/iot/announcement/controller/AnnouncementController.java \
  --frontend frontend/src/views/announcement/AnnouncementList.vue 2>&1

Works. Now simulate a real `git commit` to verify the full hook chain:  

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects && source CodeCortex/venv/bin/activate

# Stage a Java file to trigger the hook
git add CodeCortex/backend/src/main/java/com/taipei/iot/announcement/controller/AnnouncementController.java 2>/dev/null || true

# Run the hook manually (same as git commit would)
bash CodeCortex/.githooks/pre-commit 2>&1

完整鏈路全部通過。三個新檔案：

---

**pre-commit** — shell hook 入口
- 偵測 staged 的 `.java` / `.vue` / `.ts`（跳過 `target/`, `dist/`, `node_modules/`）
- 呼叫 `pre-commit-sync.py`，失敗則 **block commit**
- 若 knowledge.db 已被 git 追蹤，自動 `git add` 更新後的 DB
- `git commit --no-verify` 可緊急跳過

**pre-commit-sync.py** — Python 橋接層
- 接收 `--java` / `--frontend` 檔案清單
- 呼叫 `watch.py` 的增量更新函式（複用同一套邏輯）
- 任一檔案失敗即 exit 1，阻止 commit

**啟用方式（只需設定一次）：**
```bash
cd /path/to/kevin-projects   # repo root
git config core.hooksPath CodeCortex/.githooks
```

---

**整個自動化生命週期現在是：**

```
Ctrl+S 儲存  →  watch.py 增量更新（< 1s）
git commit   →  pre-commit hook 再次確認同步 → 若 DB 有更新自動 stage
```

Made changes.