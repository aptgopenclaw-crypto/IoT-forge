Ran terminal command:  source venv/bin/activate && python -c "import watchdog; print(watchdog.__version__)" 2>&1 || pip install watchdog 2>&1 | tail -5

Created [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/watch.py)

Ran terminal command:  source venv/bin/activate && timeout 5 python scripts/watch.py 2>&1 || true

啟動正常。現在做個真實觸發測試： 

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate && python scripts/watch.py &
WATCH_PID=$!
sleep 2

# Simulate Ctrl+S: touch a Java file
touch backend/src/main/java/com/taipei/iot/announcement/controller/AnnouncementController.java
sleep 2

# Check result
sqlite3 knowledge.db "SELECT COUNT(*) as classes FROM classes WHERE module='announcement';"
sqlite3 knowledge.db "SELECT COUNT(*) as contracts FROM feature_contracts WHERE module='announcement';"

kill $WATCH_PID 2>/dev/null
wait $WATCH_PID 2>/dev/null
echo "done"

FK constraint: `fe_be_bindings` references `endpoints`. Need to delete bindings first: 

Replacing 11 lines with 20 lines in [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/watch.py)


```

```


Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate && python scripts/watch.py &
WATCH_PID=$!
sleep 2

touch backend/src/main/java/com/taipei/iot/announcement/controller/AnnouncementController.java
sleep 2

sqlite3 knowledge.db "SELECT COUNT(*) FROM classes WHERE module='announcement';"
sqlite3 knowledge.db "SELECT COUNT(*) FROM feature_contracts WHERE module='announcement';"

kill $WATCH_PID 2>/dev/null
wait $WATCH_PID 2>/dev/null
echo "done"

`contracts_fts` was created without a `module` column. Fix the query to use `feature_name LIKE` for deletion instead: 

Replacing 7 lines with 17 lines in [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/scripts/watch.py)


```

```


Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate && python scripts/watch.py &
WATCH_PID=$!
sleep 2

touch backend/src/main/java/com/taipei/iot/announcement/controller/AnnouncementController.java
sleep 3

echo "=== Log output above ===" 
sqlite3 knowledge.db "SELECT COUNT(*) as classes   FROM classes          WHERE module='announcement';"
sqlite3 knowledge.db "SELECT COUNT(*) as contracts FROM feature_contracts WHERE module='announcement';"
sqlite3 knowledge.db "SELECT COUNT(*) as endpoints FROM endpoints         WHERE module='announcement';"

kill $WATCH_PID 2>/dev/null; wait $WATCH_PID 2>/dev/null; echo "done"

全部通過。觸發 `touch` → 1 秒內完成：`1 class, 16 endpoints, 16 feature_contracts` 正確更新。

---

## watch.py 說明

**啟動方式：**
```bash
source venv/bin/activate
python scripts/watch.py          # 前台（Ctrl+C 停止）
python scripts/watch.py &        # 背景執行
```

**監控範圍：**

| 檔案類型 | 觸發更新的 DB 表 |
|---|---|
| `.java` (Controller) | `classes`, `endpoints`, `feature_contracts`, `fe_be_bindings`, FTS5 |
| `.java` (其他) | `classes`, `imports`, FTS5 |
| `.ts` (在 `src/api/`) | `frontend_api_functions`, `fe_be_bindings` |
| `.vue` | `frontend_views` 或 `frontend_components` |

**設計要點：**
- **Debounce 0.5s**：避免編輯器 swap file 造成雙重觸發
- **FK 順序**：先刪 `fe_be_bindings` → 再刪 `endpoints`，避免 constraint 錯誤
- **FTS5 增量**：只更新受影響的 rowid，不重建整張表
- **跳過 build 目錄**：`/target/`, `/dist/`, `/node_modules/` 不觸發

Made changes.