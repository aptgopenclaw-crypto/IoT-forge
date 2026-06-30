-- V39: 公告需確認回條（acknowledgement）
--
-- 目的：
--   針對「需確認」類公告（如員工守則修訂、政策公告），
--   要求使用者明確點擊「我已閱讀並了解」才會寫入已讀紀錄；
--   管理端據以統計「已讀比例」與「未讀名單」。
--
-- 設計選擇：
--   * 預設 false → 不影響既有公告行為（展開即視為已讀）。
--   * 已讀紀錄沿用 announcement_reads 表，無需新欄位；
--     差異僅在前端 UX：requires_ack=true 時不再「展開即標為已讀」，
--     必須點擊明確按鈕。後端 POST /{id}/read 端點同時承擔兩種語意。

ALTER TABLE announcements
    ADD COLUMN requires_ack BOOLEAN NOT NULL DEFAULT FALSE;

-- 部分索引：管理端列表中「需確認且已發佈」的公告通常為熱點查詢
-- （需要在列表顯示已讀比例徽章），縮小索引以節省空間與寫入開銷。
CREATE INDEX idx_announcements_requires_ack
    ON announcements (tenant_id, publish_at DESC)
    WHERE requires_ack = TRUE AND status = 'PUBLISHED';
