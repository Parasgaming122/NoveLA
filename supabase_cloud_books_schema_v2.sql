-- ============================================================================
-- NoveLA Cloud Sync — Additional Tables (v2)
-- ============================================================================
-- Paste these INTO the same Supabase SQL Editor window where you ran
-- 1_supabase_schema.sql. These two tables extend the sync engine to also
-- mirror downloaded chapter BODIES (so phone B can read offline) and
-- the ReadingHistory entries (the separate "History" tab in NoveLA).
--
-- Both new tables share the same RLS strategy as cloud_books:
--   * anon role can SELECT any row (so phone A can read phone B's data)
--   * anon role can INSERT/UPDATE any row (because the anon key is the
--     secret — only you have it).
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. cloud_chapter_bodies
-- ----------------------------------------------------------------------------
-- Mirror of the local Room `ChapterBody` table. Used to sync the actual
-- TEXT content of downloaded chapters across phones so the user can read
-- offline on phone B without re-downloading.
--
-- NOTE: chapter bodies are LARGE — single rows can be hundreds of KB.
-- This table is gated behind the `sync_include_downloaded_chapters`
-- toggle in the app settings. Leave it OFF if you don't want every
-- downloaded chapter mirrored to your Supabase storage.
CREATE TABLE IF NOT EXISTS public.cloud_chapter_bodies (
    user_id          TEXT        NOT NULL,
    chapter_url      TEXT        NOT NULL,        -- = ChapterBody.url
    body             TEXT        NOT NULL,         -- the actual chapter text
    updated_at        BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, chapter_url)
);

CREATE INDEX IF NOT EXISTS idx_cloud_chapter_bodies_user
    ON public.cloud_chapter_bodies (user_id);

CREATE INDEX IF NOT EXISTS idx_cloud_chapter_bodies_user_updated
    ON public.cloud_chapter_bodies (user_id, updated_at DESC);

ALTER TABLE public.cloud_chapter_bodies ENABLE ROW LEVEL SECURITY;

CREATE POLICY cloud_chapter_bodies_anon_read_all
    ON public.cloud_chapter_bodies
    FOR SELECT
    TO anon
    USING (true);

CREATE POLICY cloud_chapter_bodies_anon_write_all
    ON public.cloud_chapter_bodies
    FOR ALL
    TO anon
    USING (true)
    WITH CHECK (true);

-- ----------------------------------------------------------------------------
-- 2. cloud_reading_history
-- ----------------------------------------------------------------------------
-- Mirror of the local Room `ReadingHistory` table — powers the
-- "History" tab in NoveLA (the list of recently-read books). Syncing
-- this means phone B's History tab will look the same as phone A's.
CREATE TABLE IF NOT EXISTS public.cloud_reading_history (
    user_id                  TEXT        NOT NULL,
    book_url                 TEXT        NOT NULL,    -- = ReadingHistory.bookUrl
    book_title               TEXT        NOT NULL DEFAULT '',
    book_cover_url           TEXT        NOT NULL DEFAULT '',
    last_read_chapter_url    TEXT,
    last_read_chapter_title  TEXT,
    last_read_epoch_time_milli BIGINT   NOT NULL DEFAULT 0,
    total_chapters            INTEGER    NOT NULL DEFAULT 0,
    read_chapters             INTEGER    NOT NULL DEFAULT 0,
    updated_at                BIGINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, book_url)
);

CREATE INDEX IF NOT EXISTS idx_cloud_reading_history_user
    ON public.cloud_reading_history (user_id);

CREATE INDEX IF NOT EXISTS idx_cloud_reading_history_user_updated
    ON public.cloud_reading_history (user_id, updated_at DESC);

ALTER TABLE public.cloud_reading_history ENABLE ROW LEVEL SECURITY;

CREATE POLICY cloud_reading_history_anon_read_all
    ON public.cloud_reading_history
    FOR SELECT
    TO anon
    USING (true);

CREATE POLICY cloud_reading_history_anon_write_all
    ON public.cloud_reading_history
    FOR ALL
    TO anon
    USING (true)
    WITH CHECK (true);
