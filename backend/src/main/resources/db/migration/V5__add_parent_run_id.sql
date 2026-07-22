ALTER TABLE review_run ADD COLUMN parent_run_id BIGINT REFERENCES review_run(id);
