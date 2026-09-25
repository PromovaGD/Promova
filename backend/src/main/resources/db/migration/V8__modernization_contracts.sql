ALTER TABLE saved_analysis_reviews ADD COLUMN idempotency_key VARCHAR(80);

CREATE UNIQUE INDEX uk_review_idempotency
    ON saved_analysis_reviews (saved_analysis_id, reviewer_user_id, idempotency_key);

CREATE INDEX ix_saved_analysis_review_status_lookup
    ON saved_analysis_reviews (saved_analysis_id, id DESC);
