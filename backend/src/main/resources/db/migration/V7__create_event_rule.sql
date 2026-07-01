-- V7: event_rule + event_rule_trigger_log + permissions + menus
-- package: com.taipei.iot.eventrule

CREATE TABLE event_rule (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id   VARCHAR(50)  NOT NULL,
    rule_code   VARCHAR(50)  NOT NULL,
    name        VARCHAR(200) NOT NULL,
    device_type VARCHAR(30)  NOT NULL,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    severity    VARCHAR(20)  NOT NULL,
    scope       JSONB,
    condition   JSONB        NOT NULL,
    trigger_cfg JSONB        NOT NULL,
    actions     JSONB        NOT NULL,
    create_time TIMESTAMP,
    update_time TIMESTAMP,
    CONSTRAINT uq_event_rule_tenant_code UNIQUE (tenant_id, rule_code)
);

CREATE INDEX idx_event_rule_tenant_type ON event_rule (tenant_id, device_type) WHERE enabled = TRUE;

CREATE TABLE event_rule_trigger_log (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id      VARCHAR(50) NOT NULL,
    rule_id        BIGINT      NOT NULL,
    device_id      BIGINT      NOT NULL,
    triggered_at   TIMESTAMP   NOT NULL DEFAULT now(),
    severity       VARCHAR(20),
    matched_values JSONB,
    action_result  JSONB
);

CREATE INDEX idx_evtlog_rule_time   ON event_rule_trigger_log (rule_id, triggered_at DESC);
CREATE INDEX idx_evtlog_device_time ON event_rule_trigger_log (device_id, triggered_at DESC);
CREATE INDEX idx_evtlog_tenant_time ON event_rule_trigger_log (tenant_id, triggered_at DESC);

-- Permissions
INSERT INTO permissions (permission_id, code, name, group_name, sort_order)
VALUES
    ('PERM_EVENT_RULE_VIEW',   'EVENT_RULE_VIEW',   '事件規則檢視', 'IoT Event Rule', 0),
    ('PERM_EVENT_RULE_MANAGE', 'EVENT_RULE_MANAGE', '事件規則管理', 'IoT Event Rule', 1);

-- Grant to ROLE_ADMIN
INSERT INTO role_permissions (role_id, permission_id, tenant_id)
VALUES
    ('ROLE_ADMIN', 'PERM_EVENT_RULE_VIEW',   NULL),
    ('ROLE_ADMIN', 'PERM_EVENT_RULE_MANAGE', NULL);

-- Menus (DIRECTORY sort_order=48 以避免與既有 130/134 衝突)
INSERT INTO menus (menu_id, parent_id, name, menu_type, route_name, route_path, component,
                   permission_code, icon, sort_order, visible, keep_alive, redirect, create_time, update_time, scope)
OVERRIDING SYSTEM VALUE VALUES
    (140, NULL, '事件規則', 'DIRECTORY', NULL, '/event-rules', NULL,
     NULL, 'Bell', 48, TRUE, FALSE, NULL, now(), NULL, 'TENANT'),
    (141, 140, '規則定義', 'PAGE', 'EventRuleList', '/event-rules/list',
     'views/admin/eventrule/EventRuleListView.vue', 'EVENT_RULE_VIEW',
     'AlarmClock', 10, TRUE, FALSE, NULL, now(), NULL, 'TENANT'),
    (142, 140, '觸發記錄', 'PAGE', 'EventRuleLogs', '/event-rules/logs',
     'views/admin/eventrule/EventRuleLogsView.vue', 'EVENT_RULE_VIEW',
     'ClockHistory', 20, TRUE, FALSE, NULL, now(), NULL, 'TENANT');
