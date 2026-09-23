package my.noveldokusha.feature.local_database.DAOs

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import my.noveldokusha.feature.local_database.tables.ReadingHistory
import my.noveldokusha.feature.local_database.tables.SyncStatus

@Dao
interface ReadingHistoryDao {

    @Upsert
    suspend fun upsert(history: ReadingHistory)

    @Query("SELECT * FROM ReadingHistory ORDER BY lastReadEpochTimeMilli DESC")
    fun getAllFlow(): Flow<List<ReadingHistory>>

    @Query("DELETE FROM ReadingHistory WHERE bookUrl = :bookUrl")
    suspend fun delete(bookUrl: String)

    @Query("DELETE FROM ReadingHistory")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM ReadingHistory")
    suspend fun count(): Int

    // ========================================================================
    // === CLOUD-SYNC ADDITIONS (DB v35) ===
    // ========================================================================

    /** Returns every ReadingHistory row whose [ReadingHistory.syncStatus] is NOT_SYNCED. */
    @Query("SELECT * FROM ReadingHistory WHERE syncStatus = :notSynced")
    suspend fun getAllNotSynced(notSynced: String = SyncStatus.NOT_SYNCED): List<ReadingHistory>

    /** Bulk-flips rows from NOT_SYNCED → SYNCED by bookUrl list. */
    @Query("UPDATE ReadingHistory SET syncStatus = :synced WHERE bookUrl IN (:bookUrls)")
    suspend fun markSynced(bookUrls: List<String>, synced: String = SyncStatus.SYNCED)

    /** Marks a single reading-history row as dirty (NOT_SYNCED) and stamps updatedAt. */
    @Query("UPDATE ReadingHistory SET syncStatus = :notSynced, updatedAt = :epochMs WHERE bookUrl = :bookUrl")
    suspend fun markDirty(bookUrl: String, epochMs: Long, notSynced: String = SyncStatus.NOT_SYNCED)

    /** Insert-or-replace a batch of reading-history rows coming from the cloud. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFromCloud(rows: List<ReadingHistory>)

    /** Diagnostic: count of pending uploads. */
    @Query("SELECT COUNT(*) FROM ReadingHistory WHERE syncStatus = :notSynced")
    suspend fun countNotSynced(notSynced: String = SyncStatus.NOT_SYNCED): Int

    /** Returns the full ReadingHistory table for the merge loop's local map. */
    @Query("SELECT * FROM ReadingHistory")
    suspend fun getAll(): List<ReadingHistory>
}
