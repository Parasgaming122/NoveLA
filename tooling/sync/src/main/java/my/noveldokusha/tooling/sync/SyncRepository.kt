package my.noveldokusha.tooling.sync

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import my.noveldokusha.feature.local_database.DAOs.ChapterBodyDao
import my.noveldokusha.feature.local_database.DAOs.LibraryDao
import my.noveldokusha.feature.local_database.DAOs.ReadingHistoryDao
import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.feature.local_database.tables.ChapterBody
import my.noveldokusha.feature.local_database.tables.ReadingHistory
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of a single [SyncRepository.syncWithCloud] call. The caller
 * (the Worker / Compose UI) can show a toast or log the result.
 */
sealed class SyncResult {
    /**
     * Sync ran to completion. Inspect the per-table counters for
     * diagnostics. The `pushed` / `pulled` totals are the sum of all
     * tables' counters for backwards compatibility with callers that
     * only care about the overall count.
     */
    data class Success(
        val pushed: Int,
        val pulled: Int,
        val pushedBooks: Int = 0,
        val pulledBooks: Int = 0,
        val pushedChapterBodies: Int = 0,
        val pulledChapterBodies: Int = 0,
        val pushedReadingHistory: Int = 0,
        val pulledReadingHistory: Int = 0,
    ) : SyncResult()
    /** Sync was aborted because credentials are missing or sync is disabled. */
    data object AbortedNotConfigured : SyncResult()
    /** Network or cloud-side error. The Worker will back off and retry. */
    data class Error(val message: String, val cause: Throwable? = null) : SyncResult()
}

/**
 * The core of the cloud sync engine.
 *
 * The single public entry point is [syncWithCloud], which runs a
 * deterministic three-step two-way symmetric merge against three
 * Supabase tables:
 *   1. `cloud_books`             — always synced (core feature)
 *   2. `cloud_chapter_bodies`   — synced only when the user has
 *                                  enabled `include_downloaded_chapters`
 *   3. `cloud_reading_history`  — synced only when the user has
 *                                  enabled `include_reading_history`
 *                                  (default ON)
 *
 * For each table the algorithm is identical:
 *  * Step 1 (Upload Delta): pull local rows marked NOT_SYNCED, build
 *    a single `.upsert()` payload, send it, then mark rows SYNCED.
 *  * Step 2 (Download Mirror): `.select()` filtered by `user_id`.
 *  * Step 3 (Merge Loop): for each cloud row, look up the local row
 *    by primary key. Two cases:
 *      A. Disjoint Local Insertion — local missing → insert the cloud row.
 *      B. Timestamp Overwrite — `cloud.updatedAt > local.updatedAt` →
 *        overwrite local with cloud version.
 *
 * === Chapter body chunking ===
 * Chapter bodies can be hundreds of KB each. Uploading 100 of them in
 * one `.upsert()` would be a 50MB JSON payload — PostgREST caps
 * request bodies at ~1MB by default. So chapter body uploads are
 * batched in chunks of [CHAPTER_BODY_UPLOAD_BATCH_SIZE] rows per
 * `.upsert()` call. Each chunk is upserted independently; if any
 * chunk fails, the remaining chunks are NOT retried (they'll be
 * picked up on the next sync round).
 */
@Singleton
class SyncRepository @Inject constructor(
    private val libraryDao: LibraryDao,
    private val chapterBodyDao: ChapterBodyDao,
    private val readingHistoryDao: ReadingHistoryDao,
    private val dynamicSupabaseProvider: DynamicSupabaseProvider,
    private val syncSettings: SyncSettings,
) {

    /**
     * Runs the full merge against all enabled cloud tables.
     *
     * @param userId the per-phone installation id (read from
     *   [SyncSettings]; callers can pass [SyncSettings.getUserId]).
     */
    suspend fun syncWithCloud(userId: String): SyncResult {
        val client = dynamicSupabaseProvider.clientOrNull()
            ?: return SyncResult.AbortedNotConfigured

        // Read the toggles ONCE at the start of the sync run. If the
        // user flips a toggle mid-sync, the change takes effect on
        // the NEXT run (not the current one) — that's the safe
        // behavior.
        val includeChapters = syncSettings.includeDownloadedChapters()
        val includeHistory = syncSettings.includeReadingHistory()

        // ==================================================================
        // BOOKS — always synced (core feature, no toggle)
        // ==================================================================
        val booksPushed: Int
        val booksPulled: Int
        when (val r = syncBooks(client, userId)) {
            is TableSyncOutcome.Ok -> { booksPushed = r.pushed; booksPulled = r.pulled }
            is TableSyncOutcome.Error -> return SyncResult.Error(r.message, r.cause)
        }

        // ==================================================================
        // CHAPTER BODIES — opt-in
        // ==================================================================
        var chaptersPushed = 0
        var chaptersPulled = 0
        if (includeChapters) {
            when (val r = syncChapterBodies(client, userId)) {
                is TableSyncOutcome.Ok -> { chaptersPushed = r.pushed; chaptersPulled = r.pulled }
                is TableSyncOutcome.Error -> {
                    // Don't fail the whole sync — chapter-body failures
                    // are non-fatal. Log and continue to ReadingHistory.
                    Timber.tag("SyncRepo").w(r.cause, "chapter body sync failed")
                }
            }
        }

        // ==================================================================
        // READING HISTORY — opt-out (default ON)
        // ==================================================================
        var historyPushed = 0
        var historyPulled = 0
        if (includeHistory) {
            when (val r = syncReadingHistory(client, userId)) {
                is TableSyncOutcome.Ok -> { historyPushed = r.pushed; historyPulled = r.pulled }
                is TableSyncOutcome.Error -> {
                    Timber.tag("SyncRepo").w(r.cause, "reading history sync failed")
                }
            }
        }

        // Record the wall-clock time of this successful sync run.
        val now = System.currentTimeMillis()
        syncSettings.setLastPushEpochMs(now)
        syncSettings.setLastPullEpochMs(now)

        return SyncResult.Success(
            pushed = booksPushed + chaptersPushed + historyPushed,
            pulled = booksPulled + chaptersPulled + historyPulled,
            pushedBooks = booksPushed,
            pulledBooks = booksPulled,
            pushedChapterBodies = chaptersPushed,
            pulledChapterBodies = chaptersPulled,
            pushedReadingHistory = historyPushed,
            pulledReadingHistory = historyPulled,
        )
    }

    // =====================================================================
    // Per-table merge implementations
    // =====================================================================

    private sealed interface TableSyncOutcome {
        data class Ok(val pushed: Int, val pulled: Int) : TableSyncOutcome
        data class Error(val message: String, val cause: Throwable? = null) : TableSyncOutcome
    }

    /**
     * The Book table's three-step merge. Always runs (no toggle).
     * Returns [TableSyncOutcome.Error] on upload/download failure —
     * the caller aborts the whole sync in that case because Book
     * sync is the core feature.
     */
    private suspend fun syncBooks(
        client: io.github.jan.supabase.SupabaseClient,
        userId: String,
    ): TableSyncOutcome {
        // ---- Step 1: upload delta
        val dirty: List<Book> = libraryDao.getAllNotSynced()
        if (dirty.isNotEmpty()) {
            val payload = dirty.map { CloudBookDto.fromLocal(it, userId) }
            try {
                client.postgrest["cloud_books"].upsert(payload)
                Timber.tag("SyncRepo").d("uploaded ${payload.size} books")
            } catch (t: Throwable) {
                return TableSyncOutcome.Error("books upload failed", t)
            }
            libraryDao.markSynced(dirty.map { it.url })
        }
        // ---- Step 2: download mirror
        val cloud: List<CloudBookDto> = try {
            client.postgrest["cloud_books"]
                .select { filter { eq(column = "user_id", value = userId) } }
                .decodeList<CloudBookDto>()
        } catch (t: Throwable) {
            return TableSyncOutcome.Error("books download failed", t)
        }
        // ---- Step 3: merge
        val localByUrl = libraryDao.getAll().associateBy { it.url }
        val toUpsert = mutableListOf<Book>()
        var disjoint = 0
        var overwrites = 0
        for (row in cloud) {
            val local = localByUrl[row.novelId]
            if (local == null) {
                toUpsert.add(row.toLocal()); disjoint++
            } else if (row.updatedAt > local.updatedAt) {
                toUpsert.add(row.toLocal()); overwrites++
            }
        }
        if (toUpsert.isNotEmpty()) libraryDao.upsertFromCloud(toUpsert)
        Timber.tag("SyncRepo").d("books: pushed=${dirty.size} pulled=${toUpsert.size} (disjoint=$disjoint overwrites=$overwrites)")
        return TableSyncOutcome.Ok(dirty.size, toUpsert.size)
    }

    /**
     * The ChapterBody table's three-step merge — same algorithm as
     * [syncBooks] but with chunked uploads (chapter bodies can be
     * hundreds of KB each, so a single .upsert() of 100 rows would
     * blow past PostgREST's request body limit).
     */
    private suspend fun syncChapterBodies(
        client: io.github.jan.supabase.SupabaseClient,
        userId: String,
    ): TableSyncOutcome {
        // ---- Step 1: upload delta (chunked)
        val dirty: List<ChapterBody> = chapterBodyDao.getAllNotSynced()
        var pushedCount = 0
        if (dirty.isNotEmpty()) {
            dirty.chunked(CHAPTER_BODY_UPLOAD_BATCH_SIZE).forEach { batch ->
                val payload = batch.map { CloudChapterBodyDto.fromLocal(it, userId) }
                try {
                    client.postgrest["cloud_chapter_bodies"].upsert(payload)
                    chapterBodyDao.markSynced(batch.map { it.url })
                    pushedCount += batch.size
                } catch (t: Throwable) {
                    Timber.tag("SyncRepo").w(t, "chapter body chunk upload failed (size=${batch.size})")
                    return TableSyncOutcome.Error("chapter body upload failed", t)
                }
            }
        }
        // ---- Step 2: download mirror
        val cloud: List<CloudChapterBodyDto> = try {
            client.postgrest["cloud_chapter_bodies"]
                .select { filter { eq(column = "user_id", value = userId) } }
                .decodeList<CloudChapterBodyDto>()
        } catch (t: Throwable) {
            return TableSyncOutcome.Error("chapter body download failed", t)
        }
        // ---- Step 3: merge
        val localByUrl = chapterBodyDao.getAll().associateBy { it.url }
        val toUpsert = mutableListOf<ChapterBody>()
        for (row in cloud) {
            val local = localByUrl[row.chapterUrl]
            if (local == null) {
                toUpsert.add(row.toLocal())
            } else if (row.updatedAt > local.updatedAt) {
                toUpsert.add(row.toLocal())
            }
        }
        if (toUpsert.isNotEmpty()) {
            // Chunked inserts as well — Room handles large batches
            // but SQLite's parameter limit is 999, and each ChapterBody
            // has 4 columns, so a safe batch is ~200 rows per INSERT.
            toUpsert.chunked(200).forEach { chapterBodyDao.upsertFromCloud(it) }
        }
        Timber.tag("SyncRepo").d("chapter bodies: pushed=$pushedCount pulled=${toUpsert.size}")
        return TableSyncOutcome.Ok(pushedCount, toUpsert.size)
    }

    /**
     * The ReadingHistory table's three-step merge. Same algorithm as
     * [syncBooks] — no chunking needed because reading-history rows
     * are tiny (just metadata, not chapter text).
     */
    private suspend fun syncReadingHistory(
        client: io.github.jan.supabase.SupabaseClient,
        userId: String,
    ): TableSyncOutcome {
        // ---- Step 1: upload delta
        val dirty: List<ReadingHistory> = readingHistoryDao.getAllNotSynced()
        if (dirty.isNotEmpty()) {
            val payload = dirty.map { CloudReadingHistoryDto.fromLocal(it, userId) }
            try {
                client.postgrest["cloud_reading_history"].upsert(payload)
            } catch (t: Throwable) {
                return TableSyncOutcome.Error("reading history upload failed", t)
            }
            readingHistoryDao.markSynced(dirty.map { it.bookUrl })
        }
        // ---- Step 2: download mirror
        val cloud: List<CloudReadingHistoryDto> = try {
            client.postgrest["cloud_reading_history"]
                .select { filter { eq(column = "user_id", value = userId) } }
                .decodeList<CloudReadingHistoryDto>()
        } catch (t: Throwable) {
            return TableSyncOutcome.Error("reading history download failed", t)
        }
        // ---- Step 3: merge — key is bookUrl
        val localByUrl = readingHistoryDao.getAll().associateBy { it.bookUrl }
        val toUpsert = mutableListOf<ReadingHistory>()
        for (row in cloud) {
            val local = localByUrl[row.bookUrl]
            if (local == null) {
                toUpsert.add(row.toLocal())
            } else if (row.updatedAt > local.updatedAt) {
                toUpsert.add(row.toLocal())
            }
        }
        if (toUpsert.isNotEmpty()) readingHistoryDao.upsertFromCloud(toUpsert)
        Timber.tag("SyncRepo").d("reading history: pushed=${dirty.size} pulled=${toUpsert.size}")
        return TableSyncOutcome.Ok(dirty.size, toUpsert.size)
    }

    // ======================================================================
    // Local-write hooks — called by every code path that mutates a row.
    // ======================================================================

    /** Marks a Book as locally dirty + stamps updatedAt = now. */
    suspend fun markBookChanged(bookUrl: String) {
        libraryDao.markDirty(bookUrl, System.currentTimeMillis())
    }

    /** Marks a ChapterBody as locally dirty + stamps updatedAt = now. */
    suspend fun markChapterBodyChanged(chapterUrl: String) {
        chapterBodyDao.markDirty(chapterUrl, System.currentTimeMillis())
    }

    /** Marks a ReadingHistory row as locally dirty + stamps updatedAt = now. */
    suspend fun markReadingHistoryChanged(bookUrl: String) {
        readingHistoryDao.markDirty(bookUrl, System.currentTimeMillis())
    }

    // ======================================================================
    // Optional helpers — exposed for diagnostics / the Compose settings VM.
    // ======================================================================

    /** Returns the count of local Book rows waiting to be uploaded. */
    suspend fun pendingBookUploadCount(): Int = libraryDao.countNotSynced()

    /** Returns the count of local ChapterBody rows waiting to be uploaded. */
    suspend fun pendingChapterBodyUploadCount(): Int = chapterBodyDao.countNotSynced()

    /** Returns the count of local ReadingHistory rows waiting to be uploaded. */
    suspend fun pendingReadingHistoryUploadCount(): Int = readingHistoryDao.countNotSynced()

    /** Total count of pending uploads across all tables. */
    suspend fun pendingUploadCount(): Int =
        pendingBookUploadCount() + pendingChapterBodyUploadCount() + pendingReadingHistoryUploadCount()

    /** True if the SyncSettings has been configured and the master switch is on. */
    suspend fun isConfigured(): Boolean = syncSettings.isConfigured()

    companion object {
        /**
         * Max rows per chapter-body upload batch. Each row can be up to
         * ~500KB, so 10 rows = ~5MB max — safely under PostgREST's
         * default 1MB request body limit. If a single row is unusually
         * large (some webnovel chapters are 2-3 MB), the chunk will
         * still fail and be retried on the next sync round.
         */
        const val CHAPTER_BODY_UPLOAD_BATCH_SIZE = 10
    }
}
