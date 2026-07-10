ALTER TABLE review_run ADD COLUMN source_path VARCHAR(1024);
ALTER TABLE review_run ADD COLUMN file_count INT;
ALTER TABLE review_run ADD COLUMN total_size_bytes BIGINT;
