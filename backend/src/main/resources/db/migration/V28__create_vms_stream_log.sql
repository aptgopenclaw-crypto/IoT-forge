-- V28__create_vms_stream_log.sql
CREATE TABLE vms_stream_log (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           VARCHAR(50) NOT NULL,
    user_id             BIGINT NOT NULL,
    camera_id           BIGINT NOT NULL REFERENCES vms_camera_mapping(id),
    stream_type         VARCHAR(10) NOT NULL,
    session_token       VARCHAR(36) NOT NULL,
    started_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at            TIMESTAMPTZ,
    duration_seconds    INTEGER,
    playback_start_time TIMESTAMPTZ,
    playback_end_time   TIMESTAMPTZ
);

CREATE INDEX idx_vms_stream_log_user ON vms_stream_log(user_id);
CREATE INDEX idx_vms_stream_log_camera ON vms_stream_log(camera_id);
CREATE INDEX idx_vms_stream_log_started ON vms_stream_log(started_at);
