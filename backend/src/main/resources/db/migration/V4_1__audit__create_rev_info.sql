-- ============================================================
-- AUDIT Flyway V4_1: Create rev_info table (Envers)
-- ============================================================

CREATE TABLE rev_info (
    id              SERIAL PRIMARY KEY,
    timestamp       BIGINT  NOT NULL,
    action_user_id  VARCHAR(50)
);
