package my.noveldokusha.tooling.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that runs a single full sync round
 * (upload delta → download mirror → merge loop).
 *
 * === Constraints ===
 *  * NetworkType.CONNECTED — sync only when there's network. This
 *    is the only constraint that matters for a personal two-phone
 *    setup. (No need for battery-not-low or charging — if the user
 *    is reading, the device is presumably charged enough.)
 *
 * === Backoff ===
 *  * LINEAR backoff, 30 seconds initial delay. Network hiccups,
 *    Supabase rate-limit transient 429s, and DNS resolution failures
 *    will resolve within a few retries.
 *
 * === Unique queue ===
 *  * The worker is enqueued uniquely under [UNIQUE_NAME], using
 *    [ExistingWorkPolicy.KEEP]. This means:
 *      - If a sync is already in flight, a second "enqueue now" call
 *        (e.g. from the reader close AND from app launch firing in
 *        rapid succession) will NOT spawn a duplicate worker.
 *      - The worker runs at most once at a time, which is exactly
 *        what we want — the merge loop is not idempotent against
 *        concurrent execution on the same Room DB.
 *
 * === Failure handling ===
 *  * Returns [Result.retry] on transient errors (so WorkManager will
 *    apply the backoff policy). Returns [Result.success] on
 *    [SyncResult.AbortedNotConfigured] — there's nothing to retry
 *    until the user enters credentials in the settings screen.
 *  * Returns [Result.failure] only on hard errors (e.g. a 401 from
 *    Supabase, which won't resolve with a retry).
 *
 * === Why CoroutineWorker (and not ListenableWorker) ===
 *  CoroutineWorker gives us structured concurrency on the WorkManager
 *  dispatcher, which is what we want for the suspend chain inside
 *  [SyncRepository.syncWithCloud].
 *
 * === Why @HiltWorker (and not a manual WorkerFactory entry) ===
 *  The app already wires Hilt-Work via App implementing
 *  WorkConfiguration.Provider with HiltWorkerFactory. Any class
 *  annotated @HiltWorker + @AssistedInject is picked up automatically
 *  — no edits to AppWorkerFactory are needed.
 */
@HiltWorker
class UploadSyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: SyncRepository,
    private val syncSettings: SyncSettings,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Abort fast if the user hasn't configured sync yet — there's
        // nothing to retry, so report success (otherwise the worker
        // would loop forever on backoff for a config issue).
        if (!syncSettings.isConfigured()) {
            Timber.tag("SyncWorker").d("sync not configured; skipping run")
            return Result.success(workDataOf(KEY_RESULT to "not_configured"))
        }

        val userId = try {
            syncSettings.getUserId()
        } catch (t: Throwable) {
            Timber.tag("SyncWorker").w(t, "could not read userId from DataStore")
            return Result.retry()
        }

        val outcome = try {
            syncRepository.syncWithCloud(userId)
        } catch (t: Throwable) {
            // Anything that escapes the repository's own try/catch
            // is treated as a transient failure — let WorkManager retry.
            Timber.tag("SyncWorker").w(t, "syncWithCloud threw")
            return Result.retry()
        }

        return when (outcome) {
            is SyncResult.Success -> {
                Timber.tag("SyncWorker").d(
                    "sync success: pushed=${outcome.pushed} pulled=${outcome.pulled}"
                )
                Result.success(
                    workDataOf(
                        KEY_RESULT to "success",
                        KEY_PUSHED to outcome.pushed,
                        KEY_PULLED to outcome.pulled,
                    )
                )
            }
            SyncResult.AbortedNotConfigured -> {
                // Treat as success so the worker doesn't retry-flood
                // when the user has sync disabled.
                Result.success(workDataOf(KEY_RESULT to "not_configured"))
            }
            is SyncResult.Error -> {
                Timber.tag("SyncWorker").w(outcome.cause, "sync error: ${outcome.message}")
                // Retry on errors — the backoff policy applies.
                Result.retry()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Static helpers — what other parts of the app call to enqueue sync.
    // -------------------------------------------------------------------------
    // These are intentionally on the class itself (not a separate
    // SyncStarter) so the call site just types `UploadSyncWorker.startNow(ctx)`.
    // For a cleaner injection-style API in MainActivity / ReaderActivity,
    // see SyncStarter.kt (which just delegates here).
    // -------------------------------------------------------------------------

    companion object {
        const val TAG = "NovelASync"
        const val UNIQUE_NAME = "novela_sync_one_time"
        const val KEY_RESULT = "result"
        const val KEY_PUSHED = "pushed"
        const val KEY_PULLED = "pulled"

        /**
         * Build the WorkRequest with the constraints and backoff policy
         * used by every trigger point. Both call sites (session
         * termination + app launch) share the same constraints.
         */
        private fun buildRequest(): androidx.work.OneTimeWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            return OneTimeWorkRequestBuilder<UploadSyncWorker>()
                .addTag(TAG)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()
        }

        /**
         * Enqueues a unique one-time sync, KEEPing any existing
         * in-flight sync. Safe to call from any thread.
         *
         * Use this for:
         *  * Session termination trigger — when the user back-navigates
         *    out of a novel reading session view.
         *  * App lifecycle refresh — at app launch.
         */
        fun startNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.KEEP,
                buildRequest(),
            )
        }
    }
}
