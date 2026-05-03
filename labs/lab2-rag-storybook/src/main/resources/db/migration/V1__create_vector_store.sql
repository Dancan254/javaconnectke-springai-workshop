-- =============================================================================
-- Lab 2 — RAG Storybook: Vector Store Schema
-- Migration: V1__create_vector_store.sql
-- =============================================================================
--
-- This migration creates the pgvector infrastructure used by Spring AI's
-- PgVectorStore to store and search story embeddings.
--
-- What happens here:
--   1. Enable the pgvector extension (requires superuser on first run).
--      On Neon, this extension is pre-installed — just activate it.
--   2. Create the vector_store table that Spring AI reads/writes directly.
--      Column names and types MUST match what PgVectorStore expects.
--   3. Create an IVFFlat index for approximate nearest-neighbour search.
--      Much faster than exact search for >1000 vectors.
--
-- Dimension: 1536 — the output size of text-embedding-ada-002.
--   If you switch embedding models, you must change the dimension AND
--   re-embed all documents (delete rows + re-ingest).
--
-- IVFFlat lists=100: good default for up to ~1M vectors.
--   Rule of thumb: lists ≈ sqrt(num_rows).
-- =============================================================================

-- Step 1: Enable pgvector extension
-- IF NOT EXISTS means this is safe to run multiple times (idempotent).
CREATE EXTENSION IF NOT EXISTS vector;

-- Step 2: Create the vector_store table
-- Spring AI PgVectorStore expects exactly these column names.
CREATE TABLE IF NOT EXISTS vector_store (
    id        UUID    DEFAULT gen_random_uuid() PRIMARY KEY,
    content   TEXT    NOT NULL,
    metadata  JSONB,
    embedding VECTOR(1536)
);

-- Step 3: IVFFlat index for fast approximate nearest-neighbour search
-- vector_cosine_ops = use cosine distance (matches distance-type: COSINE_DISTANCE)
-- lists = 100 is appropriate for the story chunk volume (~20-50 documents)
CREATE INDEX IF NOT EXISTS vector_store_embedding_idx
    ON vector_store USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

-- Step 4: Index on metadata for fast filtered searches
-- Useful when filtering by source, chapter, or other metadata fields.
CREATE INDEX IF NOT EXISTS vector_store_metadata_idx
    ON vector_store USING gin (metadata);
