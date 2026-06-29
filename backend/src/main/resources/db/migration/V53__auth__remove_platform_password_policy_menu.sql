-- =============================================================================
-- V53: Remove platform password policy menu (merged into tenant password policy)
-- =============================================================================

DELETE FROM menus WHERE menu_id = 36;
