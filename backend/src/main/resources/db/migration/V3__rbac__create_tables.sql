-- ============================================================
-- RBAC Flyway V3: Create menus table
-- ============================================================

-- 1. menus (global, no tenant_id)
CREATE TABLE menus (
    menu_id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    parent_id       BIGINT,
    name            VARCHAR(100)    NOT NULL,
    menu_type       VARCHAR(20)     NOT NULL,
    route_name      VARCHAR(100),
    route_path      VARCHAR(200),
    component       VARCHAR(200),
    permission_code VARCHAR(100),
    icon            VARCHAR(50),
    sort_order      INT             DEFAULT 0,
    visible         BOOLEAN         DEFAULT true,
    keep_alive      BOOLEAN         DEFAULT false,
    redirect        VARCHAR(200),
    create_time     TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMP WITHOUT TIME ZONE
);

CREATE INDEX idx_menus_parent ON menus(parent_id);
CREATE INDEX idx_menus_permission ON menus(permission_code);
