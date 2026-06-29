CREATE TABLE IF NOT EXISTS test_tenant_item (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   VARCHAR(50) NOT NULL,
    name        VARCHAR(100)
);

DELETE FROM test_tenant_item;

INSERT INTO test_tenant_item (tenant_id, name) VALUES
  ('T1', 'Item A for T1'),
  ('T1', 'Item B for T1'),
  ('T2', 'Item C for T2');

INSERT INTO tenant (tenant_id, tenant_code, tenant_name, deployment_mode, enabled, create_time, update_time)
VALUES
  ('T1', 'TENANT_A', '測試場域 A', 'CLOUD', true, NOW(), NOW()),
  ('T2', 'TENANT_B', '測試場域 B', 'ON_PREMISE', true, NOW(), NOW()),
  ('DISABLED', 'TENANT_X', '已停用場域', 'CLOUD', false, NOW(), NOW())
ON CONFLICT (tenant_id) DO NOTHING;
