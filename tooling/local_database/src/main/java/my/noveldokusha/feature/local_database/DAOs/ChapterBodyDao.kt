package my.noveldokusha.feature.local_database.DAOs

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import my.noveldokusha.feature.local_database.tables.ChapterBody
import my.noveldokusha.feature.local_database.tables.SyncStatus

@Dao
interface ChapterBodyDao {
    @Query("SELECT * FROM ChapterBody")
    suspend fun getAll(): List<ChapterBody>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReplace(chapterBody: ChapterBody)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReplace(chapterBody: List<ChapterBody>)

    @Query("SELECT * FROM ChapterBody WHERE url = :url")
    suspend fun get(url: String): ChapterBody?

    @Query("SELECT * FROM ChapterBody WHERE url IN (:urls)")
    suspend fun getBodiesByUrls(urls: List<String>): List<ChapterBody>

    @Query("DELETE FROM ChapterBody WHERE ChapterBody.url NOT IN (SELECT Chapter.url FROM Chapter)")
    suspend fun removeAllNonChapterRows()

    @Query("DELETE FROM ChapterBody WHERE ChapterBody.url IN (:chaptersUrl)")
    suspend fun removeChapterRows(chaptersUrl: List<String>)

    @Query("""
        DELETE FROM ChapterBody 
        WHERE EXISTS (
            SELECT 1 FROM Chapter 
            WHERE Chapter.url = ChapterBody.url 
            AND Chapter.bookUrl IN (:bookUrls)
        )
    """)
    suspend fun removeChapterBodiesByBookUrls(bookUrls: List<String>)

    @Query("SELECT COUNT(*) FROM ChapterBody")
    suspend fun count(): Int

    @Query("SELECT * FROM ChapterBody LIMIT :limit OFFSET :offset")
    suspend fun getChunk(limit: Int, offset: Int): List<ChapterBody>

    @Query("SELECT COALESCE(SUM(LENGTH(body)), 0) FROM ChapterBody")
    suspend fun getCacheSizeBytes(): Long

    @Query("DELETE FROM ChapterBody")
    suspend fun deleteAll(): Int

    @Query("""
        SELECT ChapterBody.url FROM ChapterBody
        INNER JOIN Chapter ON Chapter.url = ChapterBody.url
        WHERE Chapter.bookUrl = :bookUrl
    """)
    fun getDownloadedUrlsFlow(bookUrl: String): Flow<List<String>>

    @Query("""
        SELECT COUNT(*) FROM ChapterBody
        INNER JOIN Chapter ON Chapter.url = ChapterBody.url
        WHERE Chapter.bookUrl = :bookUrl
    """)
    suspend fun countDownloadedBodies(bookUrl: String): Int

    // ========================================================================
    // === CLOUD-SYNC ADDITIONS (DB v35) ===
    // ========================================================================

    /** Returns every ChapterBody row whose [ChapterBody.syncStatus] is NOT_SYNCED. */
    @Query("SELECT * FROM ChapterBody WHERE syncStatus = :notSynced")
    suspend fun getAllNotSynced(notSynced: String = SyncStatus.NOT_SYNCED): List<ChapterBody>

    /** Bulk-flips rows from NOT_SYNCED → SYNCED by url list. */
    @Query("UPDATE ChapterBody SET syncStatus = :synced WHERE url IN (:urls)")
    suspend fun markSynced(urls: List<String>, synced: String = SyncStatus.SYNCED)

    /** Marks a single chapter body as dirty (NOT_SYNCED) and stamps updatedAt. */
    @Query("UPDATE ChapterBody SET syncStatus = :notSynced, updatedAt = :epochMs WHERE url = :chapterUrl")
    suspend fun markDirty(chapterUrl: String, epochMs: Long, notSynced: String = SyncStatus.NOT_SYNCED)

    /** Insert-or-replace a batch of chapter bodies coming from the cloud. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFromCloud(books: List<ChapterBody>)

    /** Diagnostic: count of pending uploads. */
    @Query("SELECT COUNT(*) FROM ChapterBody WHERE syncStatus = :notSynced")
    suspend fun countNotSynced(notSynced: String = SyncStatus.NOT_SYNCED): Int

    data class UrlSize(val url: String, val sizeBytes: Long)

    @Query("""
        SELECT ChapterBody.url AS url, LENGTH(ChapterBody.body) AS sizeBytes
        FROM ChapterBody
        INNER JOIN Chapter ON Chapter.url = ChapterBody.url
        WHERE Chapter.bookUrl IN (:bookUrls)
    """)
    fun getSizesByBookUrls(bookUrls: List<String>): Flow<List<UrlSize>>
}
