-- V17: 修正流程定義選單位置
-- V16 錯誤地建立了獨立「工作流程管理」目錄（menu_id=136），
-- 本次修正將「流程定義」（menu_id=137）移入「系統管理」（menu_id=10）並刪除無用目錄。

-- 1. 將 menu_id=137 改掛到「系統管理」(menu_id=10) 並修正路徑
UPDATE menus
SET parent_id     = 10,
    route_path    = '/admin/system/workflow-definitions',
    icon          = 'GitBranch',
    sort_order    = 60,
    update_time   = NOW()
WHERE menu_id = 137;

-- 2. 刪除無用的「工作流程管理」頂層目錄
DELETE FROM menus WHERE menu_id = 136;
