package my.noveldokusha.data.storage

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import my.noveldokusha.core.Response
import my.noveldokusha.core.asNotNull
import my.noveldokusha.core.flatMapError
import my.noveldokusha.core.tryAsResponse
import timber.log.Timber
import java.io.File
import java.lang.reflect.Type

class PersistentCacheDataLoader<T>(
    private val cacheFile: File,
    private val type: Type,
) {
    private val gson = Gson()

    private suspend fun hasFile(): Boolean = withContext(Dispatchers.IO) { cacheFile.exists() }
    private suspend fun getFileContent(): Response<T> = tryAsResponse {
        withContext(Dispatchers.IO) {
            val content: T = gson.fromJson(cacheFile.readText(), type)
            content
        }
    }.asNotNull()

    private suspend fun set(value: T) = tryAsResponse {
        withContext(Dispatchers.IO) {
            cacheFile.writeText(gson.toJson(value, type))
        }
    }

    private suspend fun cache(fn: suspend () -> Response<T>): Response<T> {
        return when {
            hasFile() -> getFileContent()
                .onError { Timber.e(it.exception, it.message) }
                .flatMapError { fn() }
            else -> fn()
        }.onSuccess { set(it) }
    }

    suspend fun fetch(
        tryCache: Boolean = true,
        getRemote: suspend PersistentCacheDataLoader<T>.() -> Response<T>
    ): Response<T> = if (tryCache) cache { getRemote() } else getRemote()
}

