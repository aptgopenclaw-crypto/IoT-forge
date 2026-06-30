-- V41: 多語系公告 (announcement_translations)
--
-- 目的：
--   既有 announcements.title / content / content_text 僅支援單一語言。
--   為配合前端 i18n（zh-TW / zh-CN / en），公告也需依使用者語言切換顯示。
--
-- 設計選擇：
--   * 子表（child table）優於於主表加 lang_code 欄位：
--     - 公告其他欄位（publish_at、pinned、scope、target_depts、attachments、reads…）
--       不需跟著語言複製，子表只記錄翻譯。
--     - 避免「同一公告」變成多筆獨立 row，所有跨語言操作（已讀、置頂、附件）
--       仍以單一 announcement_id 為主鍵。
--   * 保留主表 title / content / content_text 作為「預設語言（zh-TW）」的快取與
--     fallback：若請求語言無對應翻譯，回退到主表欄位，UI 永遠有內容可顯示。
--   * Service 層 create/update 時保證主表 = zh-TW 翻譯內容；其他語言寫入子表。
--   * lang_code 採 IETF 標籤格式 (BCP-47)：zh-TW、zh-CN、en；長度 10 足夠。
--   * UNIQUE(announcement_id, lang_code) 防止同一公告同一語言重複。
--   * ON DELETE CASCADE：公告刪除時自動清除翻譯。
--
-- 不做的事：
--   * 不為既有資料 backfill 子表 — 主表已能 fallback；
--     管理者可於下次編輯時補上其他語言翻譯。

CREATE TABLE announcement_translations (
    id              BIGSERIAL PRIMARY KEY,
    announcement_id BIGINT      NOT NULL REFERENCES announcements(id) ON DELETE CASCADE,
    lang_code       VARCHAR(10) NOT NULL,
    title           VARCHAR(200) NOT NULL,
    content         TEXT         NOT NULL,
    content_text    TEXT         NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_announcement_translation UNIQUE (announcement_id, lang_code)
);

CREATE INDEX idx_announcement_translations_ann
    ON announcement_translations(announcement_id);
