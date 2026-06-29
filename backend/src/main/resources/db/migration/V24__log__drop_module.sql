-- V24: Remove log module — drop table, menus, permission, role binding

-- 1. Remove role_permissions binding for LOG_SUMMARY_VIEW
DELETE FROM role_permissions
WHERE permission_id IN (SELECT permission_id FROM permissions WHERE code = 'LOG_SUMMARY_VIEW');

-- 2. Remove menu entries (page first, then directory)
DELETE FROM menus WHERE menu_id = 31;
DELETE FROM menus WHERE menu_id = 30
  AND NOT EXISTS (SELECT 1 FROM menus WHERE parent_id = 30);

-- 3. Remove permission
DELETE FROM permissions WHERE code = 'LOG_SUMMARY_VIEW';

-- 4. Drop log_summary table (CASCADE removes indexes, triggers, constraints)
DROP TABLE IF EXISTS log_summary CASCADE;

-- 5. Clean up any audit events referencing removed event types
DELETE FROM user_event_log WHERE event_type IN (
  'VIEW_LOG_DASHBOARD', 'VIEW_LOG_LEVEL', 'SEARCH_LOG', 'SEMANTIC_SEARCH_LOG'
);
