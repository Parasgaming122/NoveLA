package my.noveldokusha.tooling.application_workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import androidx.work.BackoffPolicy
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.interactor.LibraryUpdatesInteractions
import my.noveldokusha.core.Response
import my.noveldokusha.core.domain.LibraryCategory
import my.noveldokusha.core.tryAsResponse
import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.tooling.application_workers.notifications.LibraryUpdateNotification
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException
import java.util.concurrent.TimeUnit

@HiltWorker
internal class LibraryUpdatesWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParameters: WorkerParameters,
    private val appPreferences: AppPreferences,
    private val libraryUpdateNotification: LibraryUpdateNotification,
    private val libraryUpdatesInteractions: LibraryUpdatesInteractions,
    private val luaSourceProvider: my.noveldokusha.scraper.LuaSourceProvider,
) : CoroutineWorker(context, workerParameters) {

    companion object {

        const val TAG = "LibraryUpdates"
        const val TAG_MANUAL = "LibraryUpdatesManual"

        private const val DATA_UPDATE_CATEGORY = "updateCategory"

        fun createPeriodicRequest(
            updateCategory: LibraryCategory,
            repeatIntervalHours: Int,
        ): PeriodicWorkRequest {
            val builder = PeriodicWorkRequestBuilder<LibraryUpdatesWorker>(
                repeatInterval = repeatIntervalHours.toLong(),
                repeatIntervalTimeUnit = TimeUnit.HOURS,
            )

            val constrains = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            return builder
                .addTag(TAG)
                .setConstraints(constrains)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .setInputData(createInputData(updateCategory))
                .build()
        }

        fun createManualRequest(
            updateCategory: LibraryCategory
        ): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<LibraryUpdatesWorker>()
                .addTag(TAG_MANUAL)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(createInputData(updateCategory))
                .build()
        }

        private fun createInputData(
            updateCategory: LibraryCategory
        ) = Data.Builder()
            .putString(DATA_UPDATE_CATEGORY, updateCategory.name)
            .build()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        ForegroundInfo(
            "Library update".hashCode(),
            libraryUpdateNotification.createForegroundNotification()
        )

    override suspend fun doWork(): Result {
        val updateCategory: LibraryCategory =
            inputData.getString(DATA_UPDATE_CATEGORY)?.let(LibraryCategory::valueOf)
                ?: LibraryCategory.DEFAULT

        Timber.d("LibraryUpdatesWorker: starting $updateCategory")

        val lastTimestamp = appPreferences.GLOBAL_APP_AUTOMATIC_LIBRARY_UPDATES_LAST_TIMESTAMP.value
        val intervalMs = appPreferences.GLOBAL_APP_AUTOMATIC_LIBRARY_UPDATES_INTERVAL_HOURS.value * 60 * 60 * 1000L
        if (lastTimestamp > 0 && (System.currentTimeMillis() - lastTimestamp) < intervalMs) {
            Timber.d("LibraryUpdatesWorker: skipped — last update was too recent")
            return Result.success()
        }

        // Ждём загрузки реальных Lua-скриптов (CachedSource заглушки не подходят для данных)
        try {
            withTimeout(30_000L) {
                luaSourceProvider.awaitLoaded()
            }
        } catch (e: Exception) {
            Timber.e(e, "LibraryUpdatesWorker: timed out waiting for Lua sources to load")
            return Result.retry()
        }

        return try {
            val result = updateLibrary(updateCategory = updateCategory)
            result.onError { Timber.e(it.exception) }
            if (result is Response.Success) {
                appPreferences.GLOBAL_APP_AUTOMATIC_LIBRARY_UPDATES_LAST_TIMESTAMP.value = System.currentTimeMillis()
            }
            when (result) {
                is Response.Error -> Result.failure()
                is Response.Success -> Result.success()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "LibraryUpdatesWorker: unexpected error")
            Result.failure()
        }
    }

    /**
     * Library update function. Will update non completed books or completed books.
     * This function will also show a status notificaton of the update progress.
     */
    private suspend fun updateLibrary(
        updateCategory: LibraryCategory
    ) = tryAsResponse {
        libraryUpdateNotification.createEmptyUpdatingNotification()
        try {
            val countingUpdating =
                MutableStateFlow<LibraryUpdatesInteractions.CountingUpdating?>(null)
            val currentUpdating = MutableStateFlow<Set<Book>>(setOf())
            val newUpdates = MutableStateFlow<Set<LibraryUpdatesInteractions.NewUpdate>>(setOf())
            val failedUpdates = MutableStateFlow<Set<Book>>(setOf())

            coroutineScope {
                val currentUpdatingNotifyJob = launch(Dispatchers.Main) {
                    combine(
                        flow = countingUpdating,
                        flow2 = currentUpdating,
                    ) { counting, current ->
                        libraryUpdateNotification.updateUpdatingNotification(
                            countingUpdated = counting?.updated ?: 0,
                            countingTotal = counting?.total ?: 0,
                            books = current
                        )
                    }.collect()
                }

                libraryUpdatesInteractions.updateLibraryBooks(
                    completedOnes = updateCategory == LibraryCategory.COMPLETED,
                    countingUpdating = countingUpdating,
                    currentUpdating = currentUpdating,
                    newUpdates = newUpdates,
                    failedUpdates = failedUpdates,
                )

                val updates = newUpdates.value.toList()
                for (index in updates.indices) {
                    val newUpdate = updates[index]
                    libraryUpdateNotification.showNewChaptersNotification(
                        book = newUpdate.book,
                        newChapters = newUpdate.newChapters,
                        silent = index == 0
                    )
                }

                if (failedUpdates.value.isNotEmpty()) {
                    libraryUpdateNotification.showFailedNotification(
                        books = failedUpdates.value
                    )
                }

                currentUpdatingNotifyJob.cancel()
            }
        } finally {
            libraryUpdateNotification.closeNotification()
        }
    }

}