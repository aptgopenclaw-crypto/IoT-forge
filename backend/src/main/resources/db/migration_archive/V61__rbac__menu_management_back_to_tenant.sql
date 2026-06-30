-- =============================================================================
-- V61: Move "選單管理" (Menu Management) back to TENANT scope
--   menu_id=11 was relocated to PLATFORM scope (under 平台管理, parent_id=100)
--   in V59. However, super_admin in tenant context and 場域管理者 (ADMIN) need
--   access to this menu. Restore it under 系統管理 (parent_id=10) with TENANT scope.
-- =============================================================================

UPDATE menus
   SET parent_id = 10,
       scope = 'TENANT'
 WHERE menu_id = 11;
