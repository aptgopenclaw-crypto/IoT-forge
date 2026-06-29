-- 租戶主檔
CREATE TABLE tenant (
    tenant_id       VARCHAR(50)     PRIMARY KEY,
    tenant_code     VARCHAR(50)     NOT NULL UNIQUE,
    tenant_name     VARCHAR(200)    NOT NULL,
    deployment_mode VARCHAR(20)     NOT NULL DEFAULT 'CLOUD',
    config          JSONB,
    enabled         BOOLEAN         NOT NULL DEFAULT true,
    create_time     TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW(),
    update_time     TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

-- 種子：預設租戶（地端部署/開發環境使用）
INSERT INTO tenant (tenant_id, tenant_code, tenant_name, deployment_mode)
VALUES ('DEFAULT', 'DEFAULT', '預設租戶', 'ON_PREMISE');
