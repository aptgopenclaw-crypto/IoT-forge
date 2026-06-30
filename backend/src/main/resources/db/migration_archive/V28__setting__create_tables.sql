-- ============================================================
-- V28: system_settings table (multi-tenant key-value store)
-- ============================================================

CREATE TABLE system_settings (
    id            BIGSERIAL    PRIMARY KEY,
    tenant_id     VARCHAR(50)  NOT NULL REFERENCES tenant(tenant_id),
    setting_key   VARCHAR(100) NOT NULL,
    setting_value VARCHAR(500) NOT NULL,
    description   VARCHAR(500),
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, setting_key)
);

CREATE INDEX idx_system_settings_tenant_id ON system_settings(tenant_id);

-- Seed idle_timeout_minutes for every existing tenant
INSERT INTO system_settings (tenant_id, setting_key, setting_value, description)
SELECT tenant_id, 'idle_timeout_minutes', '15', '使用者閒置自動登出時間（分鐘）'
FROM tenant;

-- ============================================================
-- Menu: System Settings page (under System Management, parent_id=10)
-- ============================================================
INSERT INTO menus (menu_id, parent_id, name, menu_type, route_name, route_path, component, permission_code, icon, sort_order, visible)
OVERRIDING SYSTEM VALUE VALUES
(32, 10, '系統設定', 'PAGE', 'SystemSettings', '/admin/system/settings', 'views/admin/setting/SystemSettingsView.vue', 'SYSTEM_SETTINGS_VIEW', 'Setting', 40, true)
ON CONFLICT (menu_id) DO NOTHING;

SELECT setval('menus_menu_id_seq', GREATEST((SELECT MAX(menu_id) FROM menus), 32));

-- ============================================================
-- Permissions
-- ============================================================
INSERT INTO permissions (permission_id, code, name, group_name, sort_order)
VALUES ('PERM_SYS_SETTINGS_VIEW', 'SYSTEM_SETTINGS_VIEW', '檢視系統設定', '系統設定', 1)
ON CONFLICT (code) DO NOTHING;

INSERT INTO permissions (permission_id, code, name, group_name, sort_order)
VALUES ('PERM_SYS_SETTINGS_MANAGE', 'SYSTEM_SETTINGS_MANAGE', '管理系統設定', '系統設定', 2)
ON CONFLICT (code) DO NOTHING;

-- Bind to ADMIN role
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
SELECT r.role_id, p.permission_id, NULL
FROM roles r, permissions p
WHERE r.code = 'ADMIN' AND p.code IN ('SYSTEM_SETTINGS_VIEW', 'SYSTEM_SETTINGS_MANAGE')
ON CONFLICT DO NOTHING;
