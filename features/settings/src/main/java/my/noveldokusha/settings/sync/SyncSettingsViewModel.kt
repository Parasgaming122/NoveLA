package my.noveldokusha.settings.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import my.noveldokusha.tooling.sync.DynamicSupabaseProvider
import my.noveldokusha.tooling.sync.SyncRepository
import my.noveldokusha.tooling.sync.SyncSettings
import my.noveldokusha.tooling.sync.SyncStarter
import javax.inject.Inject

/**
 * Hilt-injected state holder for the "Cloud Sync" settings section.
 *
 * Exposes reactive state for every setting the user can change in the
 * Compose settings screen:
 *  * Supabase URL + anon key (text fields, persisted on Save)
 *  * Installation ID (text field, persisted on Save; for two-phone
 *    mirror mode, paste the SAME UUID on both phones)
 *  * Sync enabled (master switch — gates everything)
 *  * Periodic sync interval (15 / 30 / 60 / 120 minutes — radio)
 *  * Include downloaded chapters (toggle, default OFF — can be MBs)
 *  * Include reading history (toggle, default ON — rows are tiny)
 *  * Last push / pull timestamps (read-only diagnostics)
 *  * Pending upload count (read-only diagnostic, refresh button)
 *
 * The "Sync now" button calls [onSyncNow] which persists any unsaved
 * text-field edits, invalidates the cached Supabase client, then
 * enqueues a one-time worker via [SyncStarter.trigger].
 *
 * === Why the interval-change handler re-enrolls the periodic worker ===
 *  Changing the interval in DataStore emits on [SyncSettings.syncIntervalMinutesFlow].
 *  [my.noveldokusha.tooling.sync.SyncPeriodicInitializer] has a long-running
 *  coroutine that collects on that flow and calls
 *  [my.noveldokusha.tooling.sync.PeriodicSyncWorker.enqueue] with the new
 *  interval. The re-enrollment happens automatically — the ViewModel just
 *  persists the new value; the periodic initializer picks it up.
 */
@HiltViewModel
class SyncSettingsViewModel @Inject constructor(
    private val syncSettings: SyncSettings,
    private val dynamicSupabaseProvider: DynamicSupabaseProvider,
    private val syncStarter: SyncStarter,
    private val syncRepository: SyncRepository,
) : ViewModel() {

    // -------------------------------------------------------------------
    // Editable text fields (local UI state — not persisted until Save)
    // -------------------------------------------------------------------

    private val _urlField = MutableStateFlow("")
    val urlField: StateFlow<String> = _urlField.asStateFlow()

    private val _anonKeyField = MutableStateFlow("")
    val anonKeyField: StateFlow<String> = _anonKeyField.asStateFlow()

    private val _userIdField = MutableStateFlow("")
    val userIdField: StateFlow<String> = _userIdField.asStateFlow()

    // -------------------------------------------------------------------
    // Persisted reactive state (read straight from DataStore)
    // -------------------------------------------------------------------

    val syncEnabled: StateFlow<Boolean> = syncSettings.syncEnabledFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val syncIntervalMinutes: StateFlow<Int> = syncSettings.syncIntervalMinutesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, SyncSettings.DEFAULT_SYNC_INTERVAL_MINUTES)

    val includeDownloadedChapters: StateFlow<Boolean> = syncSettings.includeDownloadedChaptersFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val includeReadingHistory: StateFlow<Boolean> = syncSettings.includeReadingHistoryFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val lastPushEpochMs: StateFlow<Long> = syncSettings.lastPushEpochMsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val lastPullEpochMs: StateFlow<Long> = syncSettings.lastPullEpochMsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    /** Allowed interval choices — the Compose UI uses this to render radios. */
    val allowedIntervals: List<Int> = SyncSettings.ALLOWED_INTERVALS_MINUTES

    private val _pendingUploadCount = MutableStateFlow(0)
    val pendingUploadCount: StateFlow<Int> = _pendingUploadCount.asStateFlow()

    // -------------------------------------------------------------------
    // Init — hydrate the text fields from DataStore once
    // -------------------------------------------------------------------

    init {
        viewModelScope.launch {
            _urlField.value = syncSettings.getSupabaseUrl()
            _anonKeyField.value = syncSettings.getSupabaseAnonKey()
            _userIdField.value = syncSettings.getUserId()
            _pendingUploadCount.value = syncRepository.pendingUploadCount()
        }
    }

    // -------------------------------------------------------------------
    // Event handlers — called by the Compose UI
    // -------------------------------------------------------------------

    fun onUrlChange(value: String) { _urlField.value = value }
    fun onAnonKeyChange(value: String) { _anonKeyField.value = value }
    fun onUserIdChange(value: String) { _userIdField.value = value }

    /** Persists all three text fields and invalidates the cached client. */
    fun onSaveCredentials() {
        viewModelScope.launch {
            syncSettings.setSupabaseUrl(_urlField.value)
            syncSettings.setSupabaseAnonKey(_anonKeyField.value)
            syncSettings.setUserId(_userIdField.value)
            // Force the provider to rebuild on next sync — otherwise it
            // would still serve the cached client built from the OLD
            // credentials.
            dynamicSupabaseProvider.invalidate()
        }
    }

    fun onSyncEnabledChange(value: Boolean) {
        viewModelScope.launch {
            syncSettings.setSyncEnabled(value)
            dynamicSupabaseProvider.invalidate()
        }
    }

    fun onSyncIntervalChange(minutes: Int) {
        viewModelScope.launch {
            syncSettings.setSyncIntervalMinutes(minutes)
            // The periodic initializer's flow collector will pick up the
            // new interval and re-enroll the worker automatically.
        }
    }

    fun onIncludeDownloadedChaptersChange(value: Boolean) {
        viewModelScope.launch {
            syncSettings.setIncludeDownloadedChapters(value)
        }
    }

    fun onIncludeReadingHistoryChange(value: Boolean) {
        viewModelScope.launch {
            syncSettings.setIncludeReadingHistory(value)
        }
    }

    /**
     * Manual "Sync now" button — persists any unsaved text-field edits
     * (in case the user forgot to hit Save before tapping Sync now),
     * invalidates the cached Supabase client, then enqueues the
     * one-time sync worker immediately.
     */
    fun onSyncNow() {
        viewModelScope.launch {
            onSaveCredentials()
            syncStarter.trigger()
            // Refresh the pending count after a small delay so the UI
            // shows the actual count post-sync (WorkManager schedules
            // async; we won't see the result instantly).
        }
    }

    fun refreshPendingCount() {
        viewModelScope.launch {
            _pendingUploadCount.value = syncRepository.pendingUploadCount()
        }
    }
}
