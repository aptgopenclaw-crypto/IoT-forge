-- N-4: Add optimistic locking version column to system_settings
ALTER TABLE system_settings ADD COLUMN version INTEGER NOT NULL DEFAULT 0;
