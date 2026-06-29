-- ============================================================
-- DEPT Flyway V55: Add index on hierarchy_path for LIKE prefix queries
-- Addresses: dept-module-code-review-v2 N-1
-- ============================================================

-- text_pattern_ops ensures LIKE 'prefix%' can use this B-tree index
-- regardless of the database collation setting.
-- Partial index (status = 1) matches the Repository query condition,
-- keeping the index smaller and more efficient.
CREATE INDEX idx_dept_hierarchy_path
    ON dept_info (hierarchy_path text_pattern_ops)
    WHERE status = 1;
