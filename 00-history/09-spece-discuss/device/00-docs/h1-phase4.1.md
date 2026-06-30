Let me study the existing seed data patterns first:

Study existing seed data patterns

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/resources/db/migration/V3__rbac__create_tables.sql)

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/resources/db/migration/V3_1__rbac__seed_data.sql)

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/resources/db/migration/V19__rbac__add_role_management_menu.sql)

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/resources/db/migration/V12_3__log__seed_permission_menu.sql)

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/resources/db/migration/V48__auth__password_policy_menus.sql)

Optimized tool selection

Read [](file:///home/kevin/.config/Code/User/workspaceStorage/cb384ba95079741d58728dada1449d4b/GitHub.copilot-chat/chat-session-resources/323475da-901c-4779-9db9-773030b26a76/call_00_eJqC5I60VgKlf3b9VC0J2161__vscode-1782195040839/content.txt#1-1), lines 1 to 200

Ran terminal command:  grep -rn "INSERT INTO menus" --include="*.sql" src/main/resources/db/migration/ | grep -oP 'VALUES\s*\n?\s*\(\d+' | sort -t'(' -k2 -n | tail -5 && echo "---" && grep -rn "permission_id" --include="*.sql" src/main/resources/db/migration/ | tail -5

Ran terminal command:  grep -oP '\(\d+,' src/main/resources/db/migration/V91__insert_role_permission.sql | grep -oP '\d+' | sort -n | tail -5 && echo "=== menus ===" && grep -oP '\(\d+,' src/main/resources/db/migration/V79__rbac__asset_transfer_permissions.sql | grep -oP '\d+' | sort -n | tail -10

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/resources/db/migration/V79__rbac__asset_transfer_permissions.sql#1-1), lines 1 to 60

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/resources/db/migration/V77__rbac__asset_transfer_menus.sql)

Searched for regex `DEVICE_VIEW|DEVICE_MANAGE|WORK_ORDER` (`**/*.sql`), 20 results

Read [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/resources/db/migration/V3_1__rbac__seed_data.sql#20-20), lines 20 to 80

Ran terminal command:  grep -rohP "menu_id\s*=\s*\d+" --include="*.sql" src/main/resources/db/migration/ | grep -oP '\d+' | sort -n | tail -10 && echo "=== max menu_id ===" && grep -rohP 'VALUES\s*\n?\s*\(\s*\d+' --include="*.sql" src/main/resources/db/migration/ | grep -oP '\d+' | sort -n | tail -10

Searched for text `INSERT INTO roles` (`**/*.sql`), 4 results

Searched for text `ROLE_ADMIN|ROLE_OPERATOR|ROLE_VIEWER` (`**/*.sql`), 3 results

Searched for text `r.code = 'ADMIN'` (`**/*.sql`), 3 results

Now I have the full picture. Creating V97 seed data migration:

Created [](file:///home/kevin/workspaces/kevin-projects/CodeCortex/backend/src/main/resources/db/migration/V97__device__seed_data.sql)

Ran terminal command:  mvn compile -q 2>&1

編譯通過 ✅。`V97__device__seed_data.sql` 已建立在 migration。

### 摘要

**新增 permissions**（8 筆，idempotent，`ON CONFLICT DO NOTHING`）：

| Permission Code | Group | Sort |
|---|---|---|
| `DEVICE_DELETE` | Device management | 53 |
| `DEVICE_TEMPLATE_MANAGE` | Device management | 54 |
| `WORK_ORDER_VIEW` | Work order management | 55 |
| `WORK_ORDER_MANAGE` | Work order management | 56 |
| `CIRCUIT_VIEW` | Circuit management | 57 |
| `CIRCUIT_MANAGE` | Circuit management | 58 |
| `CONTRACT_VIEW` | Contract management | 59 |
| `CONTRACT_MANAGE` | Contract management | 60 |

**新增 menu 層級**（menu_id 120-125，TENANT scope）：

```
設備管理 (120)  [DIRECTORY]
├── 設備列表 (121) — DEVICE_VIEW
├── 設備模板 (122) — DEVICE_TEMPLATE_MANAGE
├── 工單管理 (123) — WORK_ORDER_VIEW
├── 電力迴路 (124) — CIRCUIT_VIEW
└── 標案契約 (125) — CONTRACT_VIEW
```

**角色綁定**：

| Role | Device Permissions |
|------|-------------------|
| `ADMIN` | 全部（含既有 DEVICE_VIEW/CREATE/UPDATE） |
| `DEPT_ADMIN` | DEVICE_VIEW/CREATE/UPDATE/DELETE + 工單/迴路/契約管理 |
| `DEPT_USER` | DEVICE_VIEW + WORK_ORDER_VIEW/MANAGE（可通報工單） |
| `OPERATOR` | DEVICE_VIEW/CREATE/UPDATE（V3_1 既有，不變） |
| `VIEWER` | DEVICE_VIEW（V3_1 既有，不變） |

> 注意：前端 Vue 元件路徑（`views/admin/device/DeviceListView.vue` 等）尚未建立，需等前端實作時補上。

Made changes.