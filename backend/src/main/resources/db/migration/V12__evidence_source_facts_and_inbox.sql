ALTER TABLE evidences ADD COLUMN source_author VARCHAR(200);
ALTER TABLE evidences ADD COLUMN source_merged_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE evidences ADD COLUMN source_labels VARCHAR(1000);
CREATE INDEX ix_evidences_owner_occurred ON evidences(user_id, occurred_at, id);
CREATE INDEX ix_evidences_owner_source_occurred ON evidences(user_id, source, occurred_at, id);
