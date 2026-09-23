package my.noveldokusha.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import my.noveldokusha.core.atomicWrite
import my.noveldokusha.core.isCoverValid
import my.noveldokusha.core.isHttpsUrl
import my.noveldokusha.core.isImage
import my.noveldokusha.core.utils.refererFor
import my.noveldokusha.network.NetworkClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoverRepository @Inject constructor(
    private val networkClient: NetworkClient,
) {
    private val fileLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun ensureCover(
        coverFile: File,
        remoteUrl: String?,
    ): Boolean = withContext(Dispatchers.IO) {
        if (remoteUrl.isNullOrBlank() || !remoteUrl.isHttpsUrl) return@withContext false
        if (isCoverValid(coverFile)) return@withContext true

        val lock = fileLocks.getOrPut(coverFile.absolutePath) { Mutex() }
        lock.withLock {
            if (isCoverValid(coverFile)) return@withLock true

            val maxAttempts = 3
            for (attempt in 1..maxAttempts) {
                val bytes = try {
                    // Пустой Referer недопустим: isHttpsUrl — лишь префиксная проверка,
                    // поэтому для некорректного URL (например "https://") refererFor вернёт "".
                    val headers = refererFor(remoteUrl).takeIf { it.isNotEmpty() }
                        ?.let { mapOf("Referer" to it) } ?: emptyMap()
                    networkClient.getWithHeaders(remoteUrl, headers).use { response ->
                        if (!response.isSuccessful) {
                            if (attempt < maxAttempts) {
                                delay(500L * attempt) // exponential backoff: 500ms, 1000ms
                                continue
                            }
                            return@withLock false
                        }
                        response.body?.bytes()
                    }
                } catch (_: Exception) {
                    if (attempt < maxAttempts) {
                        delay(500L * attempt)
                        continue
                    }
                    return@withLock false
                }
                if (bytes == null || bytes.isEmpty() || !isImage(bytes)) {
                    if (attempt < maxAttempts) {
                        delay(500L * attempt)
                        continue
                    }
                    return@withLock false
                }

                try {
                    atomicWrite(coverFile, bytes)
                    return@withLock true
                } catch (_: Exception) {
                    if (attempt < maxAttempts) {
                        delay(500L * attempt)
                        continue
                    }
                    return@withLock false
                }
            }
            false // unreachable, but satisfies compiler
        }
    }

}
