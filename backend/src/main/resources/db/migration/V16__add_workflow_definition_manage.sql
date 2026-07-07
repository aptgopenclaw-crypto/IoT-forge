-- V16: 新增 WORKFLOW_DEFINITION_MANAGE 權限與工作流程定義管理選單

-- ── 1. 新增 permission ──────────────────────────────────────────────
INSERT INTO permissions (permission_id, code, name, group_name, sort_order)
VALUES ('PERM_WORKFLOW_DEFINITION_MANAGE', 'WORKFLOW_DEFINITION_MANAGE', '管理工作流程定義', '工作流程管理', 60)
ON CONFLICT (permission_id) DO NOTHING;

-- ── 2. ROLE_ADMIN 取得此權限 ─────────────────────────────────────────
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
VALUES ('ROLE_ADMIN', 'PERM_WORKFLOW_DEFINITION_MANAGE', NULL)
ON CONFLICT DO NOTHING;

-- ── 3. 選單：在「代理人管理」目錄（menu_id=114）旁加「工作流程管理」目錄 ──
INSERT INTO menus
    (menu_id, parent_id, name, menu_type, route_name, route_path, component,
     permission_code, icon, sort_order, visible, keep_alive, redirect, create_time, update_time, scope)
OVERRIDING SYSTEM VALUE VALUES
    (136, NULL, '工作流程管理', 'DIRECTORY', NULL, '/workflow-mgmt', NULL,
     NULL, 'GitBranch', 61, true, false, NULL, NOW(), NULL, 'TENANT'),
    (137, 136, '流程定義', 'PAGE', 'WorkflowDefinitions', '/workflow-mgmt/definitions',
     'views/workflow/WorkflowDefinitionView.vue',
     'WORKFLOW_DEFINITION_MANAGE', 'FileCode', 10, true, false, NULL, NOW(), NULL, 'TENANT')
ON CONFLICT (menu_id) DO NOTHING;
