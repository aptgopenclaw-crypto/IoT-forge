-- LOG-SUMMARY Flyway V12_2: Add vector embedding support (pgvector)
-- Requires pgvector extension to be enabled
-- Note: SET search_path includes 'public' so the vector type is visible

CREATE EXTENSION IF NOT EXISTS vector;

SET search_path TO ${flyway:defaultSchema}, public;

ALTER TABLE log_summary ADD COLUMN embedding vector(768);

CREATE INDEX idx_log_summary_embedding
    ON log_summary USING hnsw (embedding vector_cosine_ops);
