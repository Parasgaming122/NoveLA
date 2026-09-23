package my.noveldokusha.settings.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
 * === Compose state design ===
 *  * Text fields (URL, anon key) are backed by `MutableStateFlow<String>`
 *    rather than `MutableState<String>` so the user's in-progress typing
 *    is preserved across recompositions triggered by the async
 *    "save to DataStore" call.
 *  * The "sync enabled" switch is backed by the [SyncSettings]
 *    DataStore Flow directly — flipping it persists immediately.
 *  * Save buttons explicitly call the suspend setters on
 *    [SyncSettings] and then invalidate the [DynamicSupabaseProvider]
 *    cache so the next sync run picks up the new credentials.
 *
 * === Why the live "last push/pull" indicators ===
 *  Helpful when you're debugging the two-phone setup — at a glance you
 *  can see whether the last sync was 5 seconds ago or 5 days ago.
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
    // Persisted state (read straight from DataStore)
    // -------------------------------------------------------------------

    val syncEnabled: StateFlow<Boolean> = syncSettings.syncEnabledFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val lastPushEpochMs: StateFlow<Long> = syncSettings.lastPushEpochMsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val lastPullEpochMs: StateFlow<Long> = syncSettings.lastPullEpochMsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val pendingUploadCount: StateFlow<Int> = MutableStateFlow(0).also { sink ->
        viewModelScope.launch {
            // Refreshed once on view-model init; refresh on demand
            // via refreshPendingCount() (called from a manual button).
            sink.value = syncRepository.pendingUploadCount()
        }
    }.asStateFlow()

    // -------------------------------------------------------------------
    // Init — hydrate the text fields from DataStore once
    // -------------------------------------------------------------------

    init {
        viewModelScope.launch {
            _urlField.value = syncSettings.getSupabaseUrl()
            _anonKeyField.value = syncSettings.getSupabaseAnonKey()
            _userIdField.value = syncSettings.getUserId()
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

    /** Manual "sync now" button — enqueues the worker immediately. */
    fun onSyncNow() {
        viewModelScope.launch {
            // Persist whatever the user is typing in case they forgot
            // to hit Save before hitting Sync now.
            onSaveCredentials()
            syncStarter.trigger()
        }
    }

    fun refreshPendingCount() {
        viewModelScope.launch {
            // Re-read the count — useful after a manual sync.
            val count = syncRepository.pendingUploadCount()
            (pendingUploadCount as? MutableStateFlow)?.value = count
        }
    }
}
