-- ============================================================
-- V21: Backfill user_event_log.dept_id, user_label, email
-- ============================================================

-- 1. Backfill dept_id from user_tenant_mapping
UPDATE user_event_log uel
SET dept_id = utm.dept_id
FROM user_tenant_mapping utm
WHERE uel.user_id = utm.user_id
  AND uel.tenant_id = utm.tenant_id
  AND uel.dept_id IS NULL
  AND utm.dept_id IS NOT NULL;

-- 2. Backfill user_label and email from users table
UPDATE user_event_log uel
SET user_label = u.display_name,
    email = u.email
FROM users u
WHERE uel.user_id = u.user_id
  AND (uel.user_label IS NULL OR uel.email IS NULL);
