-- userId in this system is a UUID string (e.g. "c0d7da4c-5c64-4b11-9d26-5e251b8f59a4"),
-- not a numeric bigint. Change the column type to match.
ALTER TABLE iot_forgedb.vms_stream_log ALTER COLUMN user_id TYPE VARCHAR(50);
