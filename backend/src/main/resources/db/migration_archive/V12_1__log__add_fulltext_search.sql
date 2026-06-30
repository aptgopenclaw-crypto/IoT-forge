-- LOG-SUMMARY Flyway V12_1: Add fulltext search support
-- search_vector tsvector column + GIN index + auto-update trigger

ALTER TABLE log_summary ADD COLUMN search_vector tsvector;

UPDATE log_summary
SET search_vector = to_tsvector('english',
    coalesce(level, '') || ' ' || coalesce(source, '') || ' ' || coalesce(message, ''));

CREATE INDEX idx_log_summary_fts ON log_summary USING GIN (search_vector);

CREATE OR REPLACE FUNCTION log_summary_search_trigger() RETURNS trigger AS $$
BEGIN
    NEW.search_vector := to_tsvector('english',
        coalesce(NEW.level, '') || ' ' ||
        coalesce(NEW.source, '') || ' ' ||
        coalesce(NEW.message, ''));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_log_summary_search
    BEFORE INSERT OR UPDATE ON log_summary
    FOR EACH ROW EXECUTE FUNCTION log_summary_search_trigger();
