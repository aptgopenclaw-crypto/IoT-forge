-- V32: Assign icons to menus that currently have none,
--       and normalise existing icon values to lowercase lucide keys.

-- User Management sub-menus
UPDATE menus SET icon = 'users'      WHERE menu_id = 2;   -- User List
UPDATE menus SET icon = 'userplus'   WHERE menu_id = 3;   -- Create User

-- System Management sub-menus
UPDATE menus SET icon = 'layoutlist' WHERE menu_id = 11;  -- Menu Management
UPDATE menus SET icon = 'building'   WHERE menu_id = 12;  -- Department Management
UPDATE menus SET icon = 'shield'     WHERE menu_id = 13;  -- Role Management

-- Audit Center sub-menus
UPDATE menus SET icon = 'scrolltext' WHERE menu_id = 21;  -- Audit History
UPDATE menus SET icon = 'barchart'   WHERE menu_id = 22;  -- Audit Statistics

-- Monitoring Center sub-menus
UPDATE menus SET icon = 'activity'   WHERE menu_id = 31;  -- Log Summary

-- Normalise existing non-standard icon values
UPDATE menus SET icon = 'megaphone'  WHERE menu_id = 33;  -- 公告管理 (was ChatDotRound)
UPDATE menus SET icon = 'bell'       WHERE menu_id = 34;  -- 公告欄   (was Notification)
