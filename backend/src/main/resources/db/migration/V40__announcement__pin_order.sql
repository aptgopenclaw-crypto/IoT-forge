-- V40: 公告置頂順序（drag-sortable pin order）
--
-- 目的：
--   既有 pinned BOOLEAN 僅能標記「是否置頂」，多個置頂只能以 publish_at 排序。
--   新增 pin_order INT NULL 後，管理端可拖曳調整置頂順序（數字越小越靠前）。
--
-- 設計選擇：
--   * 預設 NULL：未置頂或未指定順序的公告維持 NULL。
--   * 排序語意：ORDER BY pinned DESC, pin_order ASC NULLS LAST, publish_at DESC。
--     - 置頂優先；同為置頂時依 pin_order 升冪；最後再以發佈時間倒序。
--   * 取消置頂時 service 層會將 pin_order 設回 NULL，避免遺留順序值。
--   * 部分索引：僅針對 pinned=true 且 pin_order 非空建索引，加速置頂排序。

ALTER TABLE announcements
    ADD COLUMN pin_order INT;

-- 回填既有置頂公告：以 publish_at DESC 為序，分配 1, 2, 3, ...
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY tenant_id
               ORDER BY publish_at DESC NULLS LAST, id ASC
           ) AS rn
    FROM announcements
    WHERE pinned = TRUE
)
UPDATE announcements a
SET pin_order = r.rn
FROM ranked r
WHERE a.id = r.id;

-- 索引：加速置頂清單排序
CREATE INDEX idx_announcements_pin_order
    ON announcements(tenant_id, pin_order ASC)
    WHERE pinned = TRUE AND pin_order IS NOT NULL;
