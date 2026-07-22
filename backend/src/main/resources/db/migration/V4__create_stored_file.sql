CREATE TABLE stored_file (
    id            BIGSERIAL PRIMARY KEY,
    review_run_id BIGINT NOT NULL REFERENCES review_run(id) ON DELETE CASCADE,
    file_path     VARCHAR(512) NOT NULL,
    content       TEXT NOT NULL,
    size_bytes    BIGINT NOT NULL,
    UNIQUE(review_run_id, file_path)
);
