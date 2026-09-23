package my.noveldokusha.tooling.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import my.noveldokusha.feature.local_database.tables.ChapterBody
import my.noveldokusha.feature.local_database.tables.SyncStatus

/**
 * Over-the-wire representation of a [ChapterBody] row that lives in
 * the Supabase `cloud_chapter_bodies` table.
 *
 * Used ONLY when the user has enabled the
 * [SyncSettings.includeDownloadedChaptersFlow] toggle — chapter bodies
 * can be MBs of data per phone, so this is opt-in.
 *
 * === Field mapping (cloud → local) ===
 *   user_id        (no local equivalent — installation id)
 *   chapter_url    ChapterBody.url
 *   body            ChapterBody.body
 *   updated_at      ChapterBody.updatedAt
 *
 * === Why no `syncStatus` on the DTO ===
 * Same as [CloudBookDto] — `syncStatus` is a purely local concern
 * for tracking upload delta batches. Stripped from the wire format.
 */
@Serializable
data class CloudChapterBodyDto(

    @SerialName("user_id")
    val userId: String,

    @SerialName("chapter_url")
    val chapterUrl: String,

    @SerialName("body")
    val body: String = "",

    @SerialName("updated_at")
    val updatedAt: Long = 0L,
) {
    companion object {
        fun fromLocal(chapterBody: ChapterBody, userId: String): CloudChapterBodyDto =
            CloudChapterBodyDto(
                userId = userId,
                chapterUrl = chapterBody.url,
                body = chapterBody.body,
                updatedAt = chapterBody.updatedAt,
            )
    }

    /**
     * Converts this DTO back to a local [ChapterBody] entity. The
     * returned row is marked SYNCED — anything coming FROM the cloud
     * has obviously just been synced, so there's no reason to
     * immediately push it back.
     */
    fun toLocal(): ChapterBody = ChapterBody(
        url = chapterUrl,
        body = body,
        updatedAt = updatedAt,
        syncStatus = SyncStatus.SYNCED,
    )
}
