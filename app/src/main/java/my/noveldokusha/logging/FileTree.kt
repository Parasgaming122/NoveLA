package my.noveldokusha.logging

import android.util.Log
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Timber.Tree that appends logs to a file.
 * Only logs at or above [minPriority] are written.
 * File is truncated when it exceeds [maxFileSizeBytes].
 */
class FileTree(
    private val file: File,
    private val minPriority: Int = Log.WARN,
    private val maxFileSizeBytes: Long = MAX_FILE_SIZE
) : Timber.Tree() {

    init {
        file.parentFile?.mkdirs()
        if (file.exists() && file.length() > maxFileSizeBytes) {
            file.delete()
        }
    }

    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= minPriority

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val level = when (priority) {
            Log.ERROR -> "E"
            Log.WARN -> "W"
            Log.INFO -> "I"
            Log.DEBUG -> "D"
            else -> "V"
        }
        synchronized(lock) {
            try {
                // Rotate if needed (check inside lock for thread safety)
                if (file.exists() && file.length() > maxFileSizeBytes) {
                    file.delete()
                }
                val timestamp = DATE_FORMAT.format(Date())
                val line = "$timestamp $level/${tag ?: "unknown"}: $message\n"
                file.appendText(line)
            } catch (_: Exception) {
                // Swallow — logging should never crash the app
            }
        }
    }

    companion object {
        private val lock = Any()
        // ponytail: SimpleDateFormat не потокобезопасен, но вызывается только внутри lock
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        private const val MAX_FILE_SIZE = 5L * 1024 * 1024 // 5 MB
    }
}
