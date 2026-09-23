package my.noveldokusha.feature.local_database.tables

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ReadingHistory(
    @PrimaryKey val bookUrl: String,
    val bookTitle: String,
    val bookCoverUrl: String,
    val lastReadChapterUrl: String?,
    val lastReadChapterTitle: String?,
    val lastReadEpochTimeMilli: Long,
    val totalChapters: Int = 0,
    val readChapters: Int = 0,
    // === Cloud-sync bookkeeping (added in DB v35) ===
    // Same pattern as Book — updatedAt drives the merge loop,
    // syncStatus tracks dirty rows for the upload delta batch.
    val updatedAt: Long = 0L,
    val syncStatus: String = SyncStatus.NOT_SYNCED,
)
