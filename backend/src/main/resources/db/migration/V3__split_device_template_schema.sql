--
-- Telemetry Step 2 — split device_templates.schema into nested `attributes` / `telemetry`.
--
-- The single `schema` JSONB column is kept (see SchemaProviderPort javadoc); its content is
-- normalised to the shape { "attributes": {...}, "telemetry": {...} } so that:
--   * attributes  → Device 的靜態屬性欄位（建立/編輯 Device 時驗證）
--   * telemetry   → 設備上送的時序量測欄位定義（SchemaProviderPort 取此段做 JSON Schema 驗證）
--
-- schema-agnostic: no schema prefix, no search_path. Idempotent: rows already carrying both
-- keys are skipped by the WHERE clause.
--

UPDATE device_templates
SET schema = jsonb_build_object(
        'attributes',
        CASE
            -- already split: keep existing attributes section
            WHEN schema ? 'attributes' THEN schema -> 'attributes'
            -- has telemetry only: start attributes as empty object
            WHEN schema ? 'telemetry' THEN '{}'::jsonb
            -- empty placeholder: empty object
            WHEN schema = '{}'::jsonb THEN '{}'::jsonb
            -- legacy flat schema (no attributes/telemetry keys): treat the whole blob as attributes
            ELSE schema
        END,
        'telemetry',
        CASE
            WHEN schema ? 'telemetry' THEN schema -> 'telemetry'
            ELSE '{}'::jsonb
        END
    )
WHERE NOT (schema ? 'attributes' AND schema ? 'telemetry');
