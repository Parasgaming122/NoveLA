package my.noveldokusha.tooling.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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

    // -------------------------------------------------------------------------
    // Suspend writes — called from the Compose settings screen VM
    // -------------------------------------------------------------------------

    suspend fun setSupabaseUrl(value: String) = dataStore.edit { it[KEY_SUPABASE_URL] = value.trim() }
    suspend fun setSupabaseAnonKey(value: String) = dataStore.edit { it[KEY_SUPABASE_ANON_KEY] = value.trim() }
    suspend fun setUserId(value: String) = dataStore.edit { it[KEY_USER_ID] = value.trim() }
    suspend fun setSyncEnabled(value: Boolean) = dataStore.edit { it[KEY_SYNC_ENABLED] = value }
    suspend fun setLastPushEpochMs(value: Long) = dataStore.edit { it[KEY_LAST_PUSH_EPOCH_MS] = value }
    suspend fun setLastPullEpochMs(value: Long) = dataStore.edit { it[KEY_LAST_PULL_EPOCH_MS] = value }

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
}

// Top-level DataStore delegate — one file per app, in the app's files dir.
private val Context.syncSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sync_settings"
)
