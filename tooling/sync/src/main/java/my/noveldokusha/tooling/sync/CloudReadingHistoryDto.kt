package my.noveldokusha.tooling.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import my.noveldokusha.feature.local_database.tables.ReadingHistory
import my.noveldokusha.feature.local_database.tables.SyncStatus

/**
 * Over-the-wire representation of a [ReadingHistory] row that lives in
 * the Supabase `cloud_reading_history` table.
 *
 * Used ONLY when the user has enabled the
 * [SyncSettings.includeReadingHistoryFlow] toggle (which is ON by
 * default — ReadingHistory rows are tiny and the History tab is a
 * core navigation surface).
 *
 * === Field mapping (cloud → local) ===
 *   user_id                          (no local equivalent — installation id)
 *   book_url                          ReadingHistory.bookUrl (PK)
 *   book_title                        ReadingHistory.bookTitle
 *   book_cover_url                    ReadingHistory.bookCoverUrl
 *   last_read_chapter_url             ReadingHistory.lastReadChapterUrl
 *   last_read_chapter_title           ReadingHistory.lastReadChapterTitle
 *   last_read_epoch_time_milli        ReadingHistory.lastReadEpochTimeMilli
 *   total_chapters                    ReadingHistory.totalChapters
 *   read_chapters                     ReadingHistory.readChapters
 *   updated_at                        ReadingHistory.updatedAt
 */
@Serializable
data class CloudReadingHistoryDto(

    @SerialName("user_id")
    val userId: String,

    @SerialName("book_url")
    val bookUrl: String,

    @SerialName("book_title")
    val bookTitle: String = "",

    @SerialName("book_cover_url")
    val bookCoverUrl: String = "",

    @SerialName("last_read_chapter_url")
    val lastReadChapterUrl: String? = null,

    @SerialName("last_read_chapter_title")
    val lastReadChapterTitle: String? = null,

    @SerialName("last_read_epoch_time_milli")
    val lastReadEpochTimeMilli: Long = 0L,

    @SerialName("total_chapters")
    val totalChapters: Int = 0,

    @SerialName("read_chapters")
    val readChapters: Int = 0,

    @SerialName("updated_at")
    val updatedAt: Long = 0L,
) {
    companion object {
        fun fromLocal(row: ReadingHistory, userId: String): CloudReadingHistoryDto =
            CloudReadingHistoryDto(
                userId = userId,
                bookUrl = row.bookUrl,
                bookTitle = row.bookTitle,
                bookCoverUrl = row.bookCoverUrl,
                lastReadChapterUrl = row.lastReadChapterUrl,
                lastReadChapterTitle = row.lastReadChapterTitle,
                lastReadEpochTimeMilli = row.lastReadEpochTimeMilli,
                totalChapters = row.totalChapters,
                readChapters = row.readChapters,
                updatedAt = row.updatedAt,
            )
    }

    /**
     * Converts this DTO back to a local [ReadingHistory] entity,
     * marked SYNCED (cloud-derived rows must never be re-pushed on
     * the next upload delta batch — that would cause an infinite
     * sync loop).
     */
    fun toLocal(): ReadingHistory = ReadingHistory(
        bookUrl = bookUrl,
        bookTitle = bookTitle,
        bookCoverUrl = bookCoverUrl,
        lastReadChapterUrl = lastReadChapterUrl,
        lastReadChapterTitle = lastReadChapterTitle,
        lastReadEpochTimeMilli = lastReadEpochTimeMilli,
        totalChapters = totalChapters,
        readChapters = readChapters,
        updatedAt = updatedAt,
        syncStatus = SyncStatus.SYNCED,
    )
}
