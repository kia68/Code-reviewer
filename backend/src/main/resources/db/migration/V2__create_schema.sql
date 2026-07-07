CREATE TABLE project (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
);

CREATE TABLE review_run (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL,
    triggered_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT fk_review_run_project FOREIGN KEY (project_id) REFERENCES project(id) ON DELETE CASCADE
);

CREATE TABLE finding (
    id BIGSERIAL PRIMARY KEY,
    review_run_id BIGINT NOT NULL,
    file_path VARCHAR(512) NOT NULL,
    line_number INT NOT NULL,
    category VARCHAR(100) NOT NULL,
    severity VARCHAR(50) NOT NULL,
    description TEXT NOT NULL,
    suggestion TEXT,
    CONSTRAINT fk_finding_review_run FOREIGN KEY (review_run_id) REFERENCES review_run(id) ON DELETE CASCADE
);
