package my.noveldokusha.tooling.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Periodic WorkManager worker that runs a full sync round every
 * `sync_interval_minutes` (configurable in DataStore: 15 / 30 / 60 /
 * 120 minutes).
 *
 * === Constraints ===
 *  * NetworkType.CONNECTED — only sync when there's network.
 *
 * === Backoff ===
 *  * LINEAR backoff, 30 seconds initial delay.
 *
 * === Unique queue ===
 *  * The periodic worker is enqueued uniquely under [UNIQUE_NAME],
 *    using [ExistingPeriodicWorkPolicy.UPDATE]. This means:
 *      - If the user changes the interval in the settings screen,
 *        the existing periodic worker is CANCELLED and a new one
 *        with the new interval is enqueued. This is the desired
 *        behavior — you don't want two periodic syncs running at
 *        the old + new intervals.
 *      - The next run fires after approximately [intervalMinutes]
 *        from the enqueue call (WorkManager respects the minimum
 *        periodic interval of 15 minutes; shorter intervals are
 *        bumped to 15 minutes automatically).
 *
 * === Failure handling ===
 *  * Returns [Result.retry] on transient errors (so WorkManager will
 *    apply the backoff policy). Returns [Result.success] on
 *    [SyncResult.AbortedNotConfigured] — there's nothing to retry
 *    until the user enters credentials.
 *
 * === Why a separate worker (and not reuse UploadSyncWorker)? ===
 *  * [UploadSyncWorker] is a ONE-TIME worker that runs immediately
 *    on session-close / app-launch triggers. [PeriodicSyncWorker]
 *    is the scheduled background sync — it makes sure that even if
 *    the user doesn't open the app for a day, the library still
 *    gets pulled periodically. We separate them because WorkManager
 *    treats OneTimeWorkRequest and PeriodicWorkRequest as different
 *    scheduling categories with different constraints (a periodic
 *    worker cannot be enqueued via enqueueUniqueWork with KEEP for
 *    OneTimeWork — they go through enqueueUniquePeriodicWork).
 *
 * === Why the worker pulls the interval from DataStore at runtime? ===
 *  * PeriodicWorkRequestBuilder takes the interval as a constructor
 *    param and stores it in the WorkSpec. If the user changes the
 *    interval later, the worker must be RE-ENQUEUED with the new
 *    interval. The companion [enqueue] method takes the interval as
 *    a parameter so the caller (PeriodicWorkersInitializer /
 *    SyncSettingsViewModel) can pass the latest value from DataStore.
 */
@HiltWorker
class PeriodicSyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val syncRepository: SyncRepository,
    private val syncSettings: SyncSettings,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!syncSettings.isConfigured()) {
            Timber.tag("SyncPeriodic").d("not configured; skipping")
            return Result.success()
        }
        val userId = try {
            syncSettings.getUserId()
        } catch (t: Throwable) {
            return Result.retry()
        }
        val outcome = try {
            syncRepository.syncWithCloud(userId)
        } catch (t: Throwable) {
            Timber.tag("SyncPeriodic").w(t, "syncWithCloud threw")
            return Result.retry()
        }
        return when (outcome) {
            is SyncResult.Success -> {
                Timber.tag("SyncPeriodic").d(
                    "ok: pushed=${outcome.pushed} pulled=${outcome.pulled}"
                )
                Result.success()
            }
            SyncResult.AbortedNotConfigured -> Result.success()
            is SyncResult.Error -> {
                Timber.tag("SyncPeriodic").w(outcome.cause, "sync error: ${outcome.message}")
                Result.retry()
            }
        }
    }

    companion object {
        const val UNIQUE_NAME = "novela_sync_periodic"
        const val TAG = "NovelASyncPeriodic"

        /**
         * Enqueues (or re-enqueues with a new interval) the periodic
         * sync worker. Uses UPDATE policy so changing the interval
         * in settings immediately reschedules the worker — the user
         * doesn't have to wait for the old interval to fire one last
         * time before the new one kicks in.
         *
         * @param intervalMinutes the new interval. WorkManager enforces
         *   a minimum of 15 minutes — values below 15 are bumped.
         */
        fun enqueue(context: Context, intervalMinutes: Int) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<PeriodicSyncWorker>(
                intervalMinutes.toLong(), TimeUnit.MINUTES,
            )
                .addTag(TAG)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        /** Cancels the periodic sync — used when the user turns sync off. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
