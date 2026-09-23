package my.noveldokusha.tooling.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import my.noveldokusha.core.AppCoroutineScope
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reactively schedules and reschedules [PeriodicSyncWorker] based on
 * the user's preferences. Mirrors the pattern of the existing
 * [my.noveldokusha.tooling.application_workers.setup.PeriodicWorkersInitializer]
 * — it launches a long-running coroutine that collects on the
 * sync-enabled + interval flows and re-enrolls the worker whenever
 * either changes.
 *
 * === Hook ===
 *  Called from [my.noveldokusha.features.main.MainActivity]'s
 *  ON_RESUME lifecycle observer, right next to the existing
 *  `periodicWorkersInitializer.init()` call:
 *
 *    periodicWorkersInitializer.init()
 *    syncPeriodicInitializer.init()
 *    syncStarter.trigger()    // one-shot pull-and-push on app launch
 *
 * === Behavior ===
 *  * On init: reads the current syncEnabled + syncIntervalMinutes
 *    from [SyncSettings]. If sync is enabled AND configured (URL +
 *    anon key non-blank), enqueues the periodic worker with the
 *    current interval.
 *  * On any subsequent change to either setting, re-enrolls the
 *    worker (ExistingPeriodicWorkPolicy.UPDATE — see PeriodicSyncWorker
 *    docs for why UPDATE is the right policy here).
 *  * If sync is disabled OR credentials are missing, the periodic
 *    worker is cancelled.
 *
 * === Why the periodic worker is a separate concept from
 *    [SyncStarter.trigger] ===
 *  * `SyncStarter.trigger()` enqueues a ONE-TIME worker that fires
 *    immediately. It's used for the session-close and app-launch
 *    triggers.
 *  * `SyncPeriodicInitializer.init()` schedules a PERIODIC worker
 *    that fires every N minutes even if the app is closed. This is
 *    the "set it and forget it" background sync the user expects.
 *  * Both can coexist — the one-shot worker uses
 *    `enqueueUniqueWork(KEEP)` which is a separate WorkManager queue
 *    from the periodic one. They never conflict.
 */
@Singleton
class SyncPeriodicInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncSettings: SyncSettings,
    private val appCoroutineScope: AppCoroutineScope,
) {

    fun init() {
        appCoroutineScope.launch {
            combine(
                syncSettings.syncEnabledFlow,
                syncSettings.syncIntervalMinutesFlow,
                syncSettings.supabaseUrlFlow,
                syncSettings.supabaseAnonKeyFlow,
            ) { enabled, intervalMin, url, anonKey ->
                Quad(enabled, intervalMin, url, anonKey)
            }
                .distinctUntilChanged()
                .collect { (enabled, intervalMin, url, anonKey) ->
                    if (!enabled || url.isBlank() || anonKey.isBlank()) {
                        Timber.tag("SyncPeriodicInit").d(
                            "sync disabled or not configured — cancelling periodic worker"
                        )
                        PeriodicSyncWorker.cancel(context)
                        return@collect
                    }
                    Timber.tag("SyncPeriodicInit").d(
                        "enrolling periodic sync every $intervalMin min"
                    )
                    PeriodicSyncWorker.enqueue(context, intervalMin)
                }
        }
    }

    /** Private data holder for the 4-tuple. Data class auto-generates
     *  component1-4 for destructuring — no need for manual overrides. */
    private data class Quad(
        val enabled: Boolean,
        val intervalMin: Int,
        val url: String,
        val anonKey: String,
    )
}
