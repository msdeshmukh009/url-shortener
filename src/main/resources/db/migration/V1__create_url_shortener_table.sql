CREATE TABLE IF NOT EXISTS url_shortener (
    id           SERIAL PRIMARY KEY,
    original_url VARCHAR(100) NOT NULL,
    short_code   VARCHAR(50)  NOT NULL UNIQUE,
    created_at   TIMESTAMP    NOT NULL,
    visit_count  INTEGER      DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_original_url ON url_shortener(original_url);