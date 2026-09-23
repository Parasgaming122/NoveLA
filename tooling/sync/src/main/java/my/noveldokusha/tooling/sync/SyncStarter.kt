package my.noveldokusha.tooling.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin injectable facade around [UploadSyncWorker.startNow].
 *
 * Activities and ViewModels inject this rather than calling
 * `UploadSyncWorker.startNow(context)` directly — this keeps the
 * `WorkManager` import out of the activity layer and makes the call
 * mockable in tests (in case you ever add a unit test for the
 * reading-session-close hook).
 *
 * Both trigger points — the reader close and the app launch — call
 * [trigger]. This single method is the ONLY public hook.
 */
@Singleton
class SyncStarter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Enqueue a unique one-time sync run, KEEPing any in-flight sync. */
    fun trigger() {
        UploadSyncWorker.startNow(context)
    }
}
