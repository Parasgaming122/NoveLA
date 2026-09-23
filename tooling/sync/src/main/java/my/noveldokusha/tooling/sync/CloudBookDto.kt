package my.noveldokusha.tooling.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.feature.local_database.tables.SyncStatus

/**
 * Over-the-wire representation of a Book row that lives in the
 * Supabase `cloud_books` table.
 *
 * === Why a separate DTO (and not the [Book] entity directly) ===
 *  1. The cloud table has an extra `user_id` column (per-phone
 *     installation id) that the local Room row does NOT carry.
 *  2. The cloud column names are snake_case (Postgres convention);
 *     the local entity uses camelCase (Kotlin convention). Mapping
 *     via [SerialName] keeps both sides idiomatic.
 *  3. The cloud table does NOT have the `syncStatus` column —
 *     that's a purely local concern. Stripping it from the DTO
 *     avoids leaking internal bookkeeping into the cloud schema.
 *  4. Some local Book columns may be null in the entity but are
 *     represented as non-null defaults in the cloud (and vice-versa);
 *     the DTO gives us one canonical wire format.
 *
 * === Field name mapping (cloud → local) ===
 *   user_id                        (no local equivalent — installation id)
 *   novel_id                       Book.url
 *   title                          Book.title
 *   completed                       Book.completed
 *   last_read_chapter              Book.lastReadChapter
 *   in_library                      Book.inLibrary
 *   cover_image_url                Book.coverImageUrl
 *   description                     Book.description
 *   last_read_epoch_time_milli      Book.lastReadEpochTimeMilli
 *   added_to_library_epoch_time_milli Book.addedToLibraryEpochTimeMilli
 *   last_update_epoch_time_milli    Book.lastUpdateEpochTimeMilli
 *   category                        Book.category
 *   chapters_list_hash              Book.chaptersListHash
 *   chapters_last_page              Book.chaptersLastPage
 *   genres                          Book.genres
 *   rating                          Book.rating
 *   status                          Book.status
 *   last_update_date                Book.lastUpdateDate
 *   content_type                    Book.contentType
 *   updated_at                      Book.updatedAt
 *
 * All numeric defaults are 0 and string defaults are "" — this lets the
 * Supabase upsert call omit null fields entirely (Supabase treats absent
 * fields as NULL on insert, which would fail our NOT NULL constraints
 * on the cloud table). Always send a full row.
 */
@Serializable
data class CloudBookDto(

    @SerialName("user_id")
    val userId: String,

    @SerialName("novel_id")
    val novelId: String,

    @SerialName("title")
    val title: String = "",

    @SerialName("completed")
    val completed: Boolean = false,

    @SerialName("last_read_chapter")
    val lastReadChapter: String? = null,

    @SerialName("in_library")
    val inLibrary: Boolean = false,

    @SerialName("cover_image_url")
    val coverImageUrl: String = "",

    @SerialName("description")
    val description: String = "",

    @SerialName("last_read_epoch_time_milli")
    val lastReadEpochTimeMilli: Long = 0L,

    @SerialName("added_to_library_epoch_time_milli")
    val addedToLibraryEpochTimeMilli: Long = 0L,

    @SerialName("last_update_epoch_time_milli")
    val lastUpdateEpochTimeMilli: Long = 0L,

    @SerialName("category")
    val category: String = "",

    @SerialName("chapters_list_hash")
    val chaptersListHash: String? = null,

    @SerialName("chapters_last_page")
    val chaptersLastPage: Int? = null,

    @SerialName("genres")
    val genres: String = "",

    @SerialName("rating")
    val rating: String = "",

    @SerialName("status")
    val status: String = "",

    @SerialName("last_update_date")
    val lastUpdateDate: String = "",

    @SerialName("content_type")
    val contentType: String = "",

    @SerialName("updated_at")
    val updatedAt: Long = 0L,
) {
    companion object {
        /**
         * Builds a [CloudBookDto] from a local [Book] row plus the
         * per-phone installation id. The resulting DTO is ready to be
         * sent as part of an `.upsert()` payload to Supabase.
         *
         * Note that [Book.syncStatus] is intentionally dropped — it
         * is purely a local bookkeeping field.
         */
        fun fromLocal(book: Book, userId: String): CloudBookDto = CloudBookDto(
            userId = userId,
            novelId = book.url,
            title = book.title,
            completed = book.completed,
            lastReadChapter = book.lastReadChapter,
            inLibrary = book.inLibrary,
            coverImageUrl = book.coverImageUrl,
            description = book.description,
            lastReadEpochTimeMilli = book.lastReadEpochTimeMilli,
            addedToLibraryEpochTimeMilli = book.addedToLibraryEpochTimeMilli,
            lastUpdateEpochTimeMilli = book.lastUpdateEpochTimeMilli,
            category = book.category,
            chaptersListHash = book.chaptersListHash,
            chaptersLastPage = book.chaptersLastPage,
            genres = book.genres,
            rating = book.rating,
            status = book.status,
            lastUpdateDate = book.lastUpdateDate,
            contentType = book.contentType,
            updatedAt = book.updatedAt,
        )
    }

    /**
     * Converts this DTO back to a local [Book] entity. The returned
     * Book is marked SYNCED — anything coming FROM the cloud has
     * obviously just been synced, so there's no reason to immediately
     * push it back.
     */
    fun toLocal(): Book = Book(
        title = title,
        url = novelId,
        completed = completed,
        lastReadChapter = lastReadChapter,
        inLibrary = inLibrary,
        coverImageUrl = coverImageUrl,
        description = description,
        lastReadEpochTimeMilli = lastReadEpochTimeMilli,
        addedToLibraryEpochTimeMilli = addedToLibraryEpochTimeMilli,
        lastUpdateEpochTimeMilli = lastUpdateEpochTimeMilli,
        category = category,
        chaptersListHash = chaptersListHash,
        chaptersLastPage = chaptersLastPage,
        genres = genres,
        rating = rating,
        status = status,
        lastUpdateDate = lastUpdateDate,
        contentType = contentType,
        updatedAt = updatedAt,
        syncStatus = SyncStatus.SYNCED,
    )
}
