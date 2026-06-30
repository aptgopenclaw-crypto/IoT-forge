`better-sqlite3` 需要編譯工具，改用 Python 寫 MCP Server — Python 內建 `sqlite3`，完全不需要 native 編譯，而且 venv 已就緒： 

Ran terminal command:  source venv/bin/activate && pip install mcp 2>&1 | tail -5

MCP already installed. Now rewrite the server in Python: 

Created [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/mcp-server/server.py)

Ran terminal command:  cd /home/kevin/workspaces/kevin-projects/CodeCortex && source venv/bin/activate && python -c "import mcp; print('mcp version:', mcp.__version__)" && python -c "
import subprocess, json
# Quick smoke test
result = subprocess.run(
    ['python', 'mcp-server/server.py'],
    input=b'',
    capture_output=True,
    timeout=3,
    env={**__import__('os').environ, 'DB_PATH': 'knowledge.db'}
)
print('stderr:', result.stderr.decode())
" 2>&1

Ran terminal command:  source venv/bin/activate && timeout 3 python mcp-server/server.py 2>&1 || true

Compacted conversationServer starts cleanly. Now update mcp.json: 

Searched for files matching `**/.github/mcp.json`, 1 match

Replacing 12 lines with 12 lines in [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/.github/mcp.json)


```

```
Phase 3 is done. Here's a summary:

**Created**: server.py — pure Python MCP server, no native compilation needed  
**Updated**: mcp.json — points to Python venv + `server.py`

### Tools available to Copilot Agent (`#codecortex-knowledge`)

| Tool | Purpose |
|---|---|
| `list_modules` | All 15 backend modules |
| `get_module_info(module)` | Full detail: classes, endpoints, migrations, deps, FE views |
| `resolve_dependencies(modules)` | Transitive dep resolution + migration file list |
| `list_endpoints(module?, http_method?)` | Filter REST endpoints |
| `list_classes(module?, class_type?)` | Filter Java classes |
| `list_migrations(modules?)` | Flyway SQL files |
| `list_config_keys(module?)` | application.yml keys |
| `list_frontend_views(module?)` | Vue views with stores/API used |
| `list_frontend_api(module?)` | Frontend API call functions |
| `list_routes(module?, scope?)` | Vue Router routes |
| `get_fe_be_bindings(module)` | FE function → BE endpoint map |
| `query_db(sql, limit?)` | Raw SELECT queries |

Reload the VS Code window to pick up the new MCP config, then use `#codecortex-knowledge` in Copilot Agent chat.

Made changes.