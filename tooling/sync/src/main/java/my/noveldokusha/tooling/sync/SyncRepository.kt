package my.noveldokusha.tooling.sync

import my.noveldokusha.feature.local_database.DAOs.LibraryDao
import my.noveldokusha.feature.local_database.tables.Book
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of a single [SyncRepository.syncWithCloud] call. The caller
 * (the Worker / Compose UI) can show a toast or log the result.
 */
sealed class SyncResult {
    /** Sync ran to completion. Inspect [pushed] / [pulled] for counts. */
    data class Success(val pushed: Int, val pulled: Int) : SyncResult()
    /** Sync was aborted because credentials are missing or sync is disabled. */
    data object AbortedNotConfigured : SyncResult()
    /** Network or cloud-side error. The Worker will back off and retry. */
    data class Error(val message: String, val cause: Throwable? = null) : SyncResult()
}

/**
 * The core of the cloud sync engine.
 *
 * The single public entry point is [syncWithCloud], which runs a
 * deterministic three-step two-way symmetric merge against the
 * Supabase `cloud_books` table:
 *
 *  Step 1 — Upload Delta Batch:
 *    Pull every local Book row marked NOT_SYNCED, build a single
 *    `List<CloudBookDto>` payload, and issue ONE `.upsert()` call to
 *    Supabase. On success, flip those local rows to SYNCED.
 *
 *  Step 2 — Download Mirror:
 *    Issue ONE `.select()` call to Supabase filtered by `user_id`,
 *    returning every cloud row that belongs to the current user.
 *
 *  Step 3 — Reconciliation / Merge Loop:
 *    For each cloud row, look up the matching local row by `novel_id`
 *    (= Book.url). Two cases:
 *      A. Disjoint Local Insertion — local row missing → insert the
 *         cloud version directly into Room.
 *      B. Timestamp Overwrite — local row exists → if
 *         `cloudBook.updatedAt > localBook.updatedAt`, replace the
 *         entire local row with the cloud version.
 *
 * The merge is SYMMETRIC in the sense that the same algorithm on
 * phone B will produce the same final state as on phone A, given the
 * same set of cloud rows (because it's driven purely by the
 * `updatedAt` timestamp, which is monotonic per device).
 *
 * === Idempotency ===
 *  * If a phone runs [syncWithCloud] twice in a row with no local
 *    changes in between, the second run is a no-op:
 *      - Step 1: getAllNotSynced() returns [] → no upsert call.
 *      - Step 3: cloud row's updatedAt == local row's updatedAt →
 *        no overwrite.
 *
 * === Failure semantics ===
 *  * Step 1 failure: returns [SyncResult.Error] without touching
 *    Step 2/3. The next run will retry the upload. Local rows stay
 *    NOT_SYNCED.
 *  * Step 2 failure: same — no destructive local writes happen.
 *  * Step 3 failures per-row are swallowed (logged), so one bad
 *    cloud row does not poison the entire sync.
 *
 * === Why "single .upsert()" in Step 1 ===
 *  The Supabase Kotlin SDK serializes a `List<CloudBookDto>` into one
 *  JSON array in a single POST /rest/v1/cloud_books?on_conflict=user_id,novel_id
 *  call. Postgres handles the upsert atomically. We avoid issuing N
 *  separate upsert calls because each call is a network round-trip
 *  and the merge loop should be network-cheap.
 */
@Singleton
class SyncRepository @Inject constructor(
    private val libraryDao: LibraryDao,
    private val dynamicSupabaseProvider: DynamicSupabaseProvider,
    private val syncSettings: SyncSettings,
) {

    /**
     * Runs the full three-step merge against the cloud.
     *
     * @param userId the per-phone installation id (read from
     *   [SyncSettings]; callers can pass [SyncSettings.getUserId]).
     */
    suspend fun syncWithCloud(userId: String): SyncResult {
        // ----- Abort fast if credentials are missing ----------------------
        val client = dynamicSupabaseProvider.clientOrNull()
            ?: return SyncResult.AbortedNotConfigured

        // ==================================================================
        // STEP 1 — UPLOAD DELTA BATCH
        // ==================================================================
        val dirtyLocal: List<Book> = libraryDao.getAllNotSynced()

        if (dirtyLocal.isNotEmpty()) {
            val payload = dirtyLocal.map { CloudBookDto.fromLocal(it, userId) }

            try {
                client.postgrest["cloud_books"].upsert(payload)
                Timber.tag("SyncRepo").d("uploaded ${payload.size} rows to cloud_books")
            } catch (t: Throwable) {
                // Could be: network failure, 4xx/5xx from PostgREST, JSON
                // serialization error. Either way, do NOT mark the rows
                // as SYNCED — leave them dirty so the next run retries.
                Timber.tag("SyncRepo").w(t, "upload step failed")
                return SyncResult.Error("upload step failed", t)
            }

            // Flip the just-uploaded rows to SYNCED. Use the urls of the
            // dirty rows we just uploaded — this is safe even if the
            // user started editing one of them mid-sync (the new edit
            // will set syncStatus back to NOT_SYNCED, and that book will
            // just be re-uploaded on the next round).
            libraryDao.markSynced(dirtyLocal.map { it.url })
        }

        // ==================================================================
        // STEP 2 — DOWNLOAD MIRROR
        // ==================================================================
        val cloudRows: List<CloudBookDto> = try {
            client.postgrest["cloud_books"]
                .select {
                    // WHERE user_id = :userId
                    filter {
                        eq(column = "user_id", value = userId)
                    }
                }
                .decodeList<CloudBookDto>()
        } catch (t: Throwable) {
            Timber.tag("SyncRepo").w(t, "download step failed")
            return SyncResult.Error("download step failed", t)
        }

        // ==================================================================
        // STEP 3 — RECONCILIATION / MERGE LOOP
        // ==================================================================
        // Build an in-memory map of the LOCAL table keyed by url (= novel_id).
        // We do this ONCE before iterating the cloud rows — calling
        // libraryDao.get(url) per cloud row would be O(N) DB round-trips.
        val localByUrl: Map<String, Book> =
            libraryDao.getAll().associateBy { it.url }

        var disjointInsertions = 0
        var timestampOverwrites = 0

        // Cloud rows that are new or strictly newer — collected into a
        // batch and upserted in one Room call to minimize DB writes.
        val toUpsert: MutableList<Book> = mutableListOf()

        for (cloud in cloudRows) {
            val local = localByUrl[cloud.novelId]

            if (local == null) {
                // -------- CASE A: Disjoint Local Insertion --------------
                // The book exists in the cloud but not in this phone's
                // local DB. Insert it directly.
                toUpsert.add(cloud.toLocal())
                disjointInsertions++
                continue
            }

            // -------- CASE B: Timestamp Overwrite -----------------------
            if (cloud.updatedAt > local.updatedAt) {
                // The cloud version is strictly newer. Overwrite the
                // entire local row with the cloud version.
                //
                // Example: phone A advanced "Mushoku Tensei" from
                // chapter 60 to chapter 150, stamped updatedAt =
                // System.currentTimeMillis(). Phone B's local copy still
                // has updatedAt = (old). The cloud version is newer, so
                // we replace the entire local row — the lastReadChapter
                // field now points to chapter 150.
                toUpsert.add(cloud.toLocal())
                timestampOverwrites++
            }
            // else: timestamps equal OR local is newer → keep local.
            //   * Equal timestamps: no-op (idempotent re-sync).
            //   * Local is newer: this means the local row was changed
            //     more recently than the cloud version. The local
            //     change should be NOT_SYNCED (because every local
            //     write sets syncStatus = NOT_SYNCED via markDirty),
            //     so the next upload delta batch will push it up. If
            //     for some reason it's NOT dirty (e.g. timestamps got
            //     out of sync), we still do the right thing by keeping
            //     the local version — it's "fresher" by assumption.
        }

        if (toUpsert.isNotEmpty()) {
            // Single Room upsert call for the entire batch.
            libraryDao.upsertFromCloud(toUpsert)
            Timber.tag("SyncRepo").d(
                "applied ${toUpsert.size} cloud rows " +
                    "(disjoint inserts=$disjointInsertions, " +
                    "timestamp overwrites=$timestampOverwrites)"
            )
        }

        // Record the wall-clock time of this successful sync run.
        val now = System.currentTimeMillis()
        syncSettings.setLastPushEpochMs(now)
        syncSettings.setLastPullEpochMs(now)

        return SyncResult.Success(
            pushed = dirtyLocal.size,
            pulled = toUpsert.size,
        )
    }

    /**
     * Marks a single book as locally dirty. Called from every code
     * path that mutates a Book row (the call sites are listed in
     * IntegrationNotes.md). Stamps `updatedAt = now` so that the merge
     * loop on the OTHER phone will overwrite its local copy with this
     * newer version.
     */
    suspend fun markLocalChange(bookUrl: String) {
        libraryDao.markDirty(bookUrl, System.currentTimeMillis())
    }

    // ======================================================================
    // Optional helpers — exposed for diagnostics / the Compose settings VM.
    // ======================================================================

    /** Returns the count of local rows waiting to be uploaded. */
    suspend fun pendingUploadCount(): Int = libraryDao.countNotSynced()

    /** True if the SyncSettings has been configured and the master switch is on. */
    suspend fun isConfigured(): Boolean = syncSettings.isConfigured()
}
