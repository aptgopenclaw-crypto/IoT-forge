-- V27__create_vms_camera_mapping.sql
CREATE TABLE vms_camera_mapping (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     VARCHAR(50) NOT NULL,
    server_id     BIGINT NOT NULL REFERENCES vms_server(id) ON DELETE CASCADE,
    vms_camera_id VARCHAR(100) NOT NULL,
    display_name  VARCHAR(200),
    dept_id       BIGINT,
    status        VARCHAR(20) NOT NULL DEFAULT 'ONLINE',
    rtsp_url      VARCHAR(500),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_vms_camera_server ON vms_camera_mapping(server_id);
CREATE INDEX idx_vms_camera_dept ON vms_camera_mapping(dept_id);
