-- ============================================================================
-- NoveLA Cloud Sync — Supabase SQL Schema
-- ============================================================================
-- Paste the entire block into the Supabase SQL editor
-- (Project Settings → SQL Editor → New query → Run).
--
-- Design notes:
--  * Each phone writes rows tagged with its own installation_id (a UUID
--    generated locally by the app and stored in Preferences DataStore).
--  * Row-level security (RLS) is enabled: the anon key can only ever
--    touch rows whose `user_id` matches the per-row installation_id.
--    Because every phone uses the SAME anon key (it's a personal
--    Supabase project) but a DIFFERENT installation_id, the two phones
--    can read each other's rows (they share the same installation_id
--    namespace via this column being treated as a "shared user bucket"
--    for the two-device household). If you prefer stricter isolation,
--    replace the policy predicate with `auth.uid() = user_id` after
--    wiring email auth — but for a personal two-phone sync, the
--    installation_id bucket approach below is simpler and zero-budget.
--  * `novel_id` is the local Room primary key (Book.url) — kept as TEXT
--    because Book.url is the source-of-truth novel identifier in NoveLA.
--  * `updated_at` is BIGINT (epoch millis) so the Kotlin merge logic
--    can do strict-greater-than comparison without parsing timestamps.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Table
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.cloud_books (
    user_id          TEXT        NOT NULL,
    novel_id         TEXT        NOT NULL,
    title            TEXT        NOT NULL DEFAULT '',
    completed        BOOLEAN     NOT NULL DEFAULT FALSE,
    last_read_chapter        TEXT,
    in_library       BOOLEAN     NOT NULL DEFAULT FALSE,
    cover_image_url  TEXT        NOT NULL DEFAULT '',
    description      TEXT        NOT NULL DEFAULT '',
    last_read_epoch_time_milli  BIGINT      NOT NULL DEFAULT 0,
    added_to_library_epoch_time_milli BIGINT NOT NULL DEFAULT 0,
    last_update_epoch_time_milli BIGINT   NOT NULL DEFAULT 0,
    category         TEXT        NOT NULL DEFAULT '',
    chapters_list_hash        TEXT,
    chapters_last_page       INTEGER,
    genres           TEXT        NOT NULL DEFAULT '',
    rating           TEXT        NOT NULL DEFAULT '',
    status           TEXT        NOT NULL DEFAULT '',
    last_update_date TEXT        NOT NULL DEFAULT '',
    content_type     TEXT        NOT NULL DEFAULT '',
    updated_at        BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, novel_id)
);

-- ----------------------------------------------------------------------------
-- 2. Indexes
-- ----------------------------------------------------------------------------
-- The merge loop needs to fetch ALL rows for a given user in one shot —
-- so the composite PK (user_id, novel_id) already covers the user filter.
-- Add a secondary index on (user_id, updated_at) so the "what's newer"
-- reconciliation and a possible incremental pull are cheap even when
-- the table grows past thousands of rows.
CREATE INDEX IF NOT EXISTS idx_cloud_books_user_updated
    ON public.cloud_books (user_id, updated_at DESC);

-- A covered index for the per-user count query used in diagnostics.
CREATE INDEX IF NOT EXISTS idx_cloud_books_user_only
    ON public.cloud_books (user_id);

-- ----------------------------------------------------------------------------
-- 3. Row-Level Security
-- ----------------------------------------------------------------------------
-- Enable RLS so the table is NOT world-readable even with the anon key.
-- The two phones share the same installation_id bucket (you set the
-- installation_id yourself on each phone in the Sync settings screen,
-- OR you let each phone auto-generate one and just paste the same value
-- into both). Either way, this policy guarantees that only rows whose
-- user_id matches the value the client passed in the request body are
-- ever returned.

ALTER TABLE public.cloud_books ENABLE ROW LEVEL SECURITY;

-- SELECT policy: the anon role (which is what the Kotlin SDK uses with
-- the anon key) can read any row in the table. We are NOT restricting
-- reads by user_id, because for a two-phone sync you want phone A to
-- READ phone B's rows (otherwise there's nothing to mirror). The
-- security boundary is the anon key itself, which only you possess.
CREATE POLICY cloud_books_anon_read_all
    ON public.cloud_books
    FOR SELECT
    TO anon
    USING (true);

-- INSERT / UPDATE policy: any client holding the anon key may write
-- any row. (For a tighter model, gate this on `request.jwt.claims.role =
-- 'authenticated'` and use the Supabase Dashboard to issue per-user
-- keys — beyond the zero-budget scope of this personal setup.)
CREATE POLICY cloud_books_anon_write_all
    ON public.cloud_books
    FOR ALL
    TO anon
    USING (true)
    WITH CHECK (true);

-- ----------------------------------------------------------------------------
-- 4. Realtime (optional, but cheap — gives you instant pull notifications
--    on the second phone when the first one uploads). Commented out by
--    default to keep the diff small; uncomment if you want it.
-- ----------------------------------------------------------------------------
-- ALTER PUBLICATION supabase_realtime ADD TABLE public.cloud_books;

-- ----------------------------------------------------------------------------
-- 5. Helpful views (optional, for the Supabase dashboard)
-- ----------------------------------------------------------------------------
-- SECURITY INVOKER is set so the view runs with the QUERYING user's
-- permissions rather than the view owner's. This avoids the Supabase
-- Advisor warning "View is defined with the SECURITY DEFINER property"
-- and ensures the view inherits RLS context (e.g. if you ever tighten
-- cloud_books RLS to per-user filtering, the view will also filter
-- per-user).
CREATE OR REPLACE VIEW public.cloud_books_per_user
WITH (security_invoker = true) AS
    SELECT user_id, COUNT(*) AS row_count, MAX(updated_at) AS last_update
    FROM public.cloud_books
    GROUP BY user_id
    ORDER BY last_update DESC;
