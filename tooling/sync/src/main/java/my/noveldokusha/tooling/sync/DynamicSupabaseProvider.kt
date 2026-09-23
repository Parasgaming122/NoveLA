package my.noveldokusha.tooling.sync

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thread-safe, dynamically-initialized provider of a Supabase client.
 *
 * The Supabase URL and anon key are NOT hardcoded — they are read live
 * from [SyncSettings] (Preferences DataStore) every time [clientOrNull]
 * is called. The cached client is rebuilt on demand whenever the cached
 * credentials no longer match the latest stored credentials, so changing
 * the URL or key in the settings screen immediately takes effect on the
 * next sync round.
 *
 * Why no Hilt `@Provides` for the Supabase client itself?
 *  * Because the client's lifecycle is tied to user input rather than
 *    the app lifecycle, a normal Hilt `@Singleton @Provides` would
 *    bake in the credentials read at app start (when they may still be
 *    blank). We need late-binding, hence this factory pattern.
 *  * [SyncRepository] and [UploadSyncWorker] both call [clientOrNull]
 *    every time they need a client. If credentials are missing or
 *    invalid, they get `null` and safely abort the operation.
 *
 * The OkHttp engine shares the same network stack already used
 * elsewhere in the app — no new transitive dependencies are pulled in
 * beyond the ones declared in libs.versions.toml.
 */
@Singleton
class DynamicSupabaseProvider @Inject constructor(
    private val syncSettings: SyncSettings,
) {

    /**
     * Guards the (re)build of [cached]. The mutex ensures that two
     * concurrent callers from different coroutines (e.g. the periodic
     * worker and a manual pull-from-Settings click) don't both rebuild
     * the client at the same time.
     */
    private val buildMutex = Mutex()

    @Volatile private var cached: SupabaseClientHandle? = null

    /**
     * Returns a fully-configured [SupabaseClient], or `null` if the
     * user has not yet entered the URL/anon key, or has disabled sync.
     *
     * The returned instance is reused across calls as long as the
     * underlying credentials haven't changed.
     */
    suspend fun clientOrNull(): SupabaseClient? {
        // Cheap path: if we have a cached handle and its signature
        // matches the current stored credentials, return it without
        // touching the mutex.
        val url = syncSettings.getSupabaseUrl()
        val anonKey = syncSettings.getSupabaseAnonKey()
        if (!syncSettings.isSyncEnabled() || url.isBlank() || anonKey.isBlank()) {
            // Fast-fail path: no credentials, no client.
            return null
        }
        cached?.let { handle ->
            if (handle.signature == "$url|$anonKey") return handle.client
        }

        // Slow path: rebuild under a mutex.
        return buildMutex.withLock {
            // Re-check inside the mutex — another coroutine may have
            // just rebuilt while we were waiting.
            cached?.let { handle ->
                if (handle.signature == "$url|$anonKey") return handle.client
            }
            val client = buildClient(url, anonKey)
            cached = SupabaseClientHandle(client, "$url|$anonKey")
            client
        }
    }

    /**
     * Clears the cached client. Useful when the user updates the
     * credentials in the settings screen — call this so the next
     * [clientOrNull] rebuilds from scratch.
     */
    fun invalidate() {
        cached = null
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private fun buildClient(supabaseUrl: String, anonKey: String): SupabaseClient {
        // Reuse a dedicated OkHttp instance — keep it private to the
        // Supabase client so that long timeouts and headers it sets do
        // not leak into the rest of the app's HTTP traffic.
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        // The Supabase Kotlin SDK 3.x takes an HttpClient with an engine.
        // We hand it an OkHttp engine instance; the SDK wires its own
        // auth/plugins around it.
        val httpClient = HttpClient(OkHttp) {
            engine { preconfigured = okHttpClient }
        }

        return createSupabaseClient(
            supabaseUrl = supabaseUrl,
            supabaseKey = anonKey,
        ) {
            install(Postgrest)
            this.httpClient = httpClient
        }
    }

    // Lightweight aliases to keep imports out of the call sites.
    /** Type alias to the actual SupabaseClient class. */
    typealias SupabaseClient = io.github.jan.supabase.SupabaseClient

    private data class SupabaseClientHandle(
        val client: SupabaseClient,
        val signature: String,
    )
}

/**
 * Top-level alias so call sites can simply write
 * `DynamicSupabaseProvider.SupabaseClient`. Kept here (and not in a
 * separate file) because the type is purely an indirection for tests.
 */
typealias SupabaseClient = io.github.jan.supabase.SupabaseClient
