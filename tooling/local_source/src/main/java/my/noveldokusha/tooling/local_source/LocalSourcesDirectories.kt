package my.noveldokusha.tooling.local_source

import timber.log.Timber

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import my.noveldokusha.core.AppCoroutineScope

class LocalSourcesDirectories @AssistedInject constructor(
    @ApplicationContext private val appContext: Context,
    private val appCoroutineScope: AppCoroutineScope,
    @Assisted private val sourceType: String,
) {

    @AssistedFactory
    interface Factory {
        fun create(sourceType: String): LocalSourcesDirectories
    }

    private val prefs get() =
        appContext.getSharedPreferences("local_dirs_$sourceType", Context.MODE_PRIVATE)

    val list: List<Uri>
        get() = prefs.getStringSet("dirs", emptySet())
            ?.map { Uri.parse(it) }
            ?: emptyList()

    private val _listState = MutableStateFlow(list)
    val listState = _listState.asStateFlow()

    fun add(uri: Uri) {
        appContext.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        val current = prefs.getStringSet("dirs", mutableSetOf())!!.toMutableSet()
        current.add(uri.toString())
        prefs.edit().putStringSet("dirs", current).apply()
        updateState()
    }

    fun remove(uri: Uri) {
        val current = prefs.getStringSet("dirs", mutableSetOf())!!.toMutableSet()
        current.remove(uri.toString())
        prefs.edit().putStringSet("dirs", current).apply()
        val permission = appContext.contentResolver.persistedUriPermissions
            .firstOrNull { it.uri == uri }
        val flags = if (permission != null) {
            var f = Intent.FLAG_GRANT_READ_URI_PERMISSION
            if (permission.isWritePermission) f = f or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            f
        } else {
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        try {
            appContext.contentResolver.releasePersistableUriPermission(uri, flags)
        } catch (e: Exception) {
            Timber.e(e, "Failed to release URI permission")
        }
        updateState()
    }

    private fun updateState() {
        appCoroutineScope.launch(Dispatchers.Default) {
            _listState.update { list }
        }
    }
}
