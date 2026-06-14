-- ============================================================
-- V2: Add pgvector extension and document embeddings table
--
-- WHY pgvector?
-- Traditional databases store text and query by exact/fuzzy match.
-- pgvector adds a new column type: vector(N) — an array of floats
-- that represents semantic meaning. You can query by cosine similarity:
-- "give me chunks semantically similar to this query vector."
--
-- This is the foundation of our RAG (Retrieval-Augmented Generation) system.
-- ============================================================

-- Enable pgvector extension
-- WHY CREATE EXTENSION IF NOT EXISTS?
-- Idempotent — safe to run even if already installed.
-- In AKS, we'll ensure pgvector is installed in the PostgreSQL image.
CREATE EXTENSION IF NOT EXISTS vector;

-- Document chunks + embeddings table
-- WHY separate from documents table?
-- One document produces hundreds of chunks.
-- Keeping them in a separate table allows:
-- 1. Efficient vector index on just the embedding column
-- 2. Fast counting (SELECT COUNT(*) FROM document_chunks WHERE document_id = ?)
-- 3. Clean deletion (DELETE FROM document_chunks WHERE document_id = ?)
CREATE TABLE IF NOT EXISTS document_chunks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    user_id         VARCHAR(255) NOT NULL,    -- denormalized for query performance
    chunk_index     INTEGER NOT NULL,          -- position of this chunk in the document
    chunk_text      TEXT NOT NULL,             -- actual text content
    token_count     INTEGER,                   -- how many tokens in this chunk

    -- vector(1536): OpenAI text-embedding-3-small produces 1536-dimensional vectors
    -- Each dimension is a float32 (4 bytes) => 1536 * 4 = 6KB per row
    embedding       vector(1536),

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_document_chunk UNIQUE (document_id, chunk_index)
);

-- Standard index for filtering by document
CREATE INDEX idx_chunks_document_id ON document_chunks(document_id);
CREATE INDEX idx_chunks_user_id ON document_chunks(user_id);

-- ============================================================
-- VECTOR INDEX — This is where the magic happens
--
-- WHY ivfflat (not hnsw)?
-- ivfflat (Inverted File with Flat Quantization):
--   - Divides vectors into 'lists' clusters
--   - At query time, searches only nearby clusters
--   - Faster index builds, slightly lower recall
--   - Good for up to ~1M vectors
--
-- hnsw (Hierarchical Navigable Small Worlds):
--   - Navigable graph structure
--   - Higher recall at query time
--   - Slower index builds, more memory
--   - Better for >1M vectors or when recall matters most
--
-- For MVP/development: ivfflat is fine.
-- For production with millions of docs: consider hnsw.
--
-- lists=100: rule of thumb is sqrt(num_rows).
-- We'll increase this as data grows.
-- cosine_ops: use cosine distance (standard for text embeddings)
-- ============================================================
CREATE INDEX idx_chunks_embedding
    ON document_chunks
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);
