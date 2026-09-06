ALTER TABLE evidences RENAME COLUMN evidence TO content;

ALTER TABLE evidences ADD COLUMN occurred_at TIMESTAMP(6) WITH TIME ZONE;
UPDATE evidences SET occurred_at = captured_at WHERE occurred_at IS NULL;
ALTER TABLE evidences ALTER COLUMN occurred_at SET NOT NULL;

DROP INDEX ix_evidences_user_status_captured_at;
CREATE INDEX ix_evidences_user_status_occurred_at
    ON evidences (user_id, status, occurred_at, id);
