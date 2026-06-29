-- ============================================================
-- Flyway V15: 修正種子使用者密碼 hash
--   原 hash 對應 Test1234!，更新為 Test0123456!
-- ============================================================
UPDATE users
SET password_hash = '$2a$10$0eUuG1ASZUU7A0B8pukW7.b2pwnrVpgDKUjXqj8Y0D0Ol5ZK6MdOa'
WHERE user_id IN (
    'u-tpe-admin', 'u-squad1-mgr', 'u-squad1-off1', 'u-squad1-off2',
    'u-squad2-mgr', 'u-squad2-off1', 'u-eng-mgr', 'u-eng-off1', 'u-adm-mgr'
);
