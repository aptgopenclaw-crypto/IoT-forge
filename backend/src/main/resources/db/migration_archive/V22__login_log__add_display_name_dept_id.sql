-- Migrate existing user_login_log data into user_event_log (consolidation)
INSERT INTO user_event_log (
    tenant_id, user_id, username, user_label, email,
    event_type, event_desc, api_endpoint,
    error_code, message,
    ip_address, user_agent, execution_time, dept_id, create_time
)
SELECT
    ull.tenant_id,
    ull.user_id,
    ull.email,                                       -- username = email
    u.display_name,                                  -- user_label from users table
    ull.email,
    ull.event_type,                                  -- LOGIN_SUCCESS, LOGIN_FAIL, TENANT_SWITCH
    'USER_AUTH',                                     -- event_desc 統一為 USER_AUTH
    NULL,                                            -- api_endpoint（原表無此欄位）
    CASE WHEN ull.event_type LIKE '%SUCCESS%' OR ull.event_type = 'TENANT_SWITCH'
         THEN '00000' ELSE '99999' END,              -- error_code
    ull.detail,                                      -- message = detail
    ull.ip_address,
    ull.user_agent,
    0,                                               -- execution_time
    utm.dept_id,                                     -- dept_id from mapping
    ull.create_time
FROM user_login_log ull
LEFT JOIN users u ON ull.user_id = u.user_id
LEFT JOIN user_tenant_mapping utm ON ull.user_id = utm.user_id AND ull.tenant_id = utm.tenant_id
WHERE NOT EXISTS (
    -- 避免重複：若已有相同 user_id + event_type + create_time 的紀錄就跳過
    SELECT 1 FROM user_event_log uel
    WHERE uel.user_id = ull.user_id
      AND uel.event_type = ull.event_type
      AND uel.create_time = ull.create_time
      AND uel.event_desc = 'USER_AUTH'
);
