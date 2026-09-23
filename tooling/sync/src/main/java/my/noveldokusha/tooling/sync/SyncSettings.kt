package my.noveldokusha.tooling.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preferences DataStore-backed store for the dynamic Supabase
 * credentials and the per-installation user id used to scope rows in
 * the `cloud_books` table.
 *
 * Why a separate DataStore (and not the existing [AppPreferences]
 * SharedPreferences-based store)?
 *  * The user explicitly requested Preferences DataStore for these
 *    credentials, so credentials live in their own DataStore file
 *    (sync_settings.preferences_pb), independent of the main prefs.
 *  * DataStore gives typed suspend-safe reads/writes with no
 *    SharedPreferences commit gotchas.
 *
 * All methods are safe to call from any thread; the underlying DataStore
 * serializes writes internally.
 */
@Singleton
class SyncSettings @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore: DataStore<Preferences> = context.syncSettingsDataStore

    // -------------------------------------------------------------------------
    // Keys
    // -------------------------------------------------------------------------
    // NOTE: keys are file-private — there's no reason to leak them outside
    // this class. Every accessor below is type-safe.
    // -------------------------------------------------------------------------

    private val KEY_SUPABASE_URL = stringPreferencesKey("supabase_url")
    private val KEY_SUPABASE_ANON_KEY = stringPreferencesKey("supabase_anon_key")
    private val KEY_USER_ID = stringPreferencesKey("installation_id")
    private val KEY_SYNC_ENABLED = booleanPreferencesKey("sync_enabled")
    private val KEY_LAST_PUSH_EPOCH_MS = longPreferencesKey("last_push_epoch_ms")
    private val KEY_LAST_PULL_EPOCH_MS = longPreferencesKey("last_pull_epoch_ms")

    // === v2 "flexible sync" preferences ===
    // Periodic sync interval in minutes. One of 15 / 30 / 60 / 120.
    // Default 60 minutes — reasonable middle ground.
    private val KEY_SYNC_INTERVAL_MINUTES = intPreferencesKey("sync_interval_minutes")
    // Toggle: should the sync engine also mirror downloaded chapter
    // bodies (the actual text content of chapters) to the cloud? This
    // can be MBs of data per phone, so default OFF.
    private val KEY_INCLUDE_DOWNLOADED_CHAPTERS = booleanPreferencesKey("include_downloaded_chapters")
    // Toggle: should the sync engine also mirror the ReadingHistory
    // entries (the "History" tab in NoveLA)? Default ON — these rows
    // are tiny and the History tab is a core navigation experience.
    private val KEY_INCLUDE_READING_HISTORY = booleanPreferencesKey("include_reading_history")

    // -------------------------------------------------------------------------
    // Reactive reads — for the Compose settings screen
    // -------------------------------------------------------------------------

    /** Reactive Supabase URL. Empty by default. */
    val supabaseUrlFlow: Flow<String> = dataStore.data.map { it[KEY_SUPABASE_URL].orEmpty() }

    /** Reactive Supabase anon key. Empty by default. */
    val supabaseAnonKeyFlow: Flow<String> = dataStore.data.map { it[KEY_SUPABASE_ANON_KEY].orEmpty() }

    /** Reactive installation id. Lazily generated on first access. */
    val userIdFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_USER_ID]?.takeIf { it.isNotBlank() } ?: run {
            val fresh = UUID.randomUUID().toString()
            // Persist the freshly generated id so subsequent reads are stable.
            dataStore.edit { it[KEY_USER_ID] = fresh }
            fresh
        }
    }

    /** Reactive master switch for the whole sync engine. */
    val syncEnabledFlow: Flow<Boolean> = dataStore.data.map { it[KEY_SYNC_ENABLED] ?: false }

    val lastPushEpochMsFlow: Flow<Long> = dataStore.data.map { it[KEY_LAST_PUSH_EPOCH_MS] ?: 0L }
    val lastPullEpochMsFlow: Flow<Long> = dataStore.data.map { it[KEY_LAST_PULL_EPOCH_MS] ?: 0L }

    // === v2 reactive reads ===
    /** Reactive periodic-sync interval in minutes. Default 60. */
    val syncIntervalMinutesFlow: Flow<Int> = dataStore.data.map {
        it[KEY_SYNC_INTERVAL_MINUTES] ?: DEFAULT_SYNC_INTERVAL_MINUTES
    }

    /** Reactive flag: should downloaded chapter bodies be mirrored? */
    val includeDownloadedChaptersFlow: Flow<Boolean> = dataStore.data.map {
        it[KEY_INCLUDE_DOWNLOADED_CHAPTERS] ?: false
    }

    /** Reactive flag: should ReadingHistory rows be mirrored? */
    val includeReadingHistoryFlow: Flow<Boolean> = dataStore.data.map {
        it[KEY_INCLUDE_READING_HISTORY] ?: true
    }

    // -------------------------------------------------------------------------
    // Suspend reads — for the sync engine itself (one-shot)
    // -------------------------------------------------------------------------

    suspend fun getSupabaseUrl(): String = dataStore.data.first()[KEY_SUPABASE_URL].orEmpty()

    suspend fun getSupabaseAnonKey(): String = dataStore.data.first()[KEY_SUPABASE_ANON_KEY].orEmpty()

    /**
     * Returns the per-installation user id, generating and persisting one
     * on first call. Always non-blank.
     *
     * IMPORTANT: this id is the per-phone bucket used to tag rows in the
     * `cloud_books` table. For your two-phone personal setup, you can:
     *   1. Let each phone auto-generate its own id (default behavior here)
     *      → both phones can read each other's rows because the SQL RLS
     *      policy allows anon read across all user_ids.
     *   2. OR paste the SAME id into both phones in the settings screen
     *      → both phones write into the same user_id bucket, so the merge
     *      loop will literally match rows 1:1 by (user_id, novel_id).
     * Option 2 is what you want — it makes the two phones a true mirror.
     * Option 1 is the safe default until you set things up.
     */
    suspend fun getUserId(): String = userIdFlow.first()

    suspend fun isSyncEnabled(): Boolean = syncEnabledFlow.first()

    // === v2 suspend reads ===

    suspend fun getSyncIntervalMinutes(): Int = syncIntervalMinutesFlow.first()

    suspend fun includeDownloadedChapters(): Boolean = includeDownloadedChaptersFlow.first()

    suspend fun includeReadingHistory(): Boolean = includeReadingHistoryFlow.first()

    // -------------------------------------------------------------------------
    // Suspend writes — called from the Compose settings screen VM
    // -------------------------------------------------------------------------

    suspend fun setSupabaseUrl(value: String) = dataStore.edit { it[KEY_SUPABASE_URL] = value.trim() }
    suspend fun setSupabaseAnonKey(value: String) = dataStore.edit { it[KEY_SUPABASE_ANON_KEY] = value.trim() }
    suspend fun setUserId(value: String) = dataStore.edit { it[KEY_USER_ID] = value.trim() }
    suspend fun setSyncEnabled(value: Boolean) = dataStore.edit { it[KEY_SYNC_ENABLED] = value }
    suspend fun setLastPushEpochMs(value: Long) = dataStore.edit { it[KEY_LAST_PUSH_EPOCH_MS] = value }
    suspend fun setLastPullEpochMs(value: Long) = dataStore.edit { it[KEY_LAST_PULL_EPOCH_MS] = value }

    // === v2 suspend writes ===
    suspend fun setSyncIntervalMinutes(minutes: Int) = dataStore.edit {
        it[KEY_SYNC_INTERVAL_MINUTES] = minutes.coerceIn(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)
    }
    suspend fun setIncludeDownloadedChapters(value: Boolean) = dataStore.edit {
        it[KEY_INCLUDE_DOWNLOADED_CHAPTERS] = value
    }
    suspend fun setIncludeReadingHistory(value: Boolean) = dataStore.edit {
        it[KEY_INCLUDE_READING_HISTORY] = value
    }

    /**
     * Convenience accessor: returns true iff BOTH the URL and anon key
     * are non-blank AND the master switch is on.
     *
     * Used by [DynamicSupabaseProvider] to decide whether to even
     * attempt a sync.
     */
    suspend fun isConfigured(): Boolean {
        val url = getSupabaseUrl()
        val key = getSupabaseAnonKey()
        return url.startsWith("http") && key.isNotBlank() && isSyncEnabled()
    }

    companion object {
        // Allowed values for the periodic-sync interval. The settings
        // UI uses this set to render the radio-button choices.
        val ALLOWED_INTERVALS_MINUTES = listOf(15, 30, 60, 120)
        const val DEFAULT_SYNC_INTERVAL_MINUTES = 60
        const val MIN_INTERVAL_MINUTES = 15
        const val MAX_INTERVAL_MINUTES = 120
    }
}

// Top-level DataStore delegate — one file per app, in the app's files dir.
private val Context.syncSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sync_settings"
)
