CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS file(
    id SERIAL PRIMARY KEY,
    path TEXT NOT NULL UNIQUE,
    sha256 BYTEA NOT NULL,
    mtime TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS file_sha256 ON file(sha256);

CREATE TABLE IF NOT EXISTS embedding_model_meta(
    id INT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    model_name TEXT NOT NULL,
    dimensions INT NOT NULL
);

CREATE TABLE IF NOT EXISTS embedding(
    id SERIAL PRIMARY KEY,
    file_id INT NOT NULL REFERENCES file(id) ON DELETE CASCADE,
    text TEXT NULL,
    image_embedding VECTOR NULL,
    text_embedding VECTOR NULL,
    model_name TEXT NOT NULL,
    text_search_vector tsvector GENERATED ALWAYS AS (to_tsvector('simple', text)) STORED
);

CREATE UNIQUE INDEX one_embedding_per_model ON embedding(file_id, model_name);
CREATE INDEX IF NOT EXISTS idx_embedding_text_search_vector ON embedding USING gin (text_search_vector);
