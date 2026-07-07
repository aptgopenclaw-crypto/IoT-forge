INSERT INTO telemetry_ingest_client
    (tenant_id, client_name, api_key, secret_hash, enabled, rate_limit_per_min, device_scope, created_at, updated_at)
VALUES
    (
      'T_7BA4E9A8DA6B',
      'Telemetry Simulator',
      'telemetry-sim-client-20260707',
      '$2y$10$GkrKcXJWAskUuvaXTPL0TOkD0qhDD2UzD6mxxLRpQz2QyitZ9fBmG',
      true,
      1200,
      null,
      now(),
      now()
    );