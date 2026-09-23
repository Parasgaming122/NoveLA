package my.noveldokusha.data.storage

import android.content.Context
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import my.noveldokusha.scraper.DatabaseInterface
import my.noveldokusha.scraper.SearchGenre
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersistentCacheDatabaseSearchGenresProvider @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {

    fun provide(database: DatabaseInterface): PersistentCacheDataLoader<List<SearchGenre>> {
        return PersistentCacheDataLoader(
            cacheFile = File(appContext.cacheDir, database.searchGenresCacheFileName),
            type = TypeToken.getParameterized(List::class.java, SearchGenre::class.java).type
        )
    }
}
