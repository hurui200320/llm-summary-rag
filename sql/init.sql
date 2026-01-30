-- enable vector extension
CREATE EXTENSION IF NOT EXISTS vector;


-- document table, record the document name
CREATE TABLE IF NOT EXISTS documents
(
    id       SERIAL PRIMARY KEY,
    title    TEXT,
    author   TEXT,
    language TEXT,
    summary  TEXT
);


-- chunk table, record pre content, content, post content, document id
CREATE TABLE IF NOT EXISTS chunks
(
    id           SERIAL PRIMARY KEY,
    index_of_doc INTEGER, -- the chunk index of the document, start with 0
    pre_content  TEXT,
    content      TEXT,
    post_content TEXT,
    summary      TEXT,
    document_id  INTEGER REFERENCES documents (id)
        ON DELETE CASCADE
);


-- vector tables, here we use 1536 instead of 3072 because HNSW index only support up to 2000 dims
CREATE TABLE IF NOT EXISTS chunk_summary_vectors
(
    chunk_id  INTEGER PRIMARY KEY REFERENCES chunks (id)
        ON DELETE CASCADE,
    embedding vector(1536)
);
CREATE TABLE IF NOT EXISTS document_summary_vectors
(
    document_id INTEGER PRIMARY KEY REFERENCES documents (id)
        ON DELETE CASCADE,
    embedding   vector(1536)
);

