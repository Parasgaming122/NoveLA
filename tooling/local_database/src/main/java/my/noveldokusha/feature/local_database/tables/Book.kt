package my.noveldokusha.feature.local_database.tables

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(
    indices = [
        Index(value = ["inLibrary"])
    ]
)
data class Book(
    val title: String,
    @PrimaryKey val url: String,
    val completed: Boolean = false,
    val lastReadChapter: String? = null,
    val inLibrary: Boolean = false,
    val coverImageUrl: String = "",
    val description: String = "",
    val lastReadEpochTimeMilli: Long = 0,
    val addedToLibraryEpochTimeMilli: Long = 0,
    val lastUpdateEpochTimeMilli: Long = 0,
    val category: String = "",
    val chaptersListHash: String? = null,
    // Последняя известная страница списка глав (для parsePage-плагинов).
    // null → плагин не поддерживает parsePage, используется старый getChapterList.
    val chaptersLastPage: Int? = null,
    // Жанры книги, разделённые запятой. Нормализованы: без дублей, лишних пробелов, мусора.
    // Пример: "Fantasy,Action,Romance"
    val genres: String = "",
    // Рейтинг/ранг книги — «сырая» строка из источника, сохраняется как есть.
    // Нормализация к шкале 0-5 выполняется при отображении (parseBookRating в coreui),
    // на уровне хранения формат не унифицируется. Строка, потому что источники
    // отдают гетерогенные форматы: "4.5", "4.8/5", "RANK 216", "#12" и т.п.
    // Пустая строка → рейтинг неизвестен, UI не рисует бейдж.
    val rating: String = "",
    // Статус книги (Ongoing/Completed/...), парсится с сайта источником.
    // «Сырая» строка, сохраняется как есть, без нормализации/перевода.
    // Пустая строка → статус неизвестен, UI не рисует чип.
    val status: String = "",
    // Дата последнего обновления книги, парсится с сайта источником.
    // «Сырая» строка в формате источника, отображается как есть.
    // Пустая строка → дата неизвестна, UI не рисует лейбл.
    val lastUpdateDate: String = "",
    // Тип контента книги: "NOVEL", "MANGA", "COMIC" и т.п. Пустая строка = NOVEL
    // (дефолт для всех источников, не заполняющих contentType).
    val contentType: String = "",
    // === Cloud-sync bookkeeping (added in DB v34) ===
    // updatedAt — epoch-millis of the last LOCAL write to this row.
    // Used by SyncRepository's two-way symmetric merge to decide whether
    // the cloud version is strictly newer and should overwrite local.
    val updatedAt: Long = 0L,
    // syncStatus — "SYNCED" or "NOT_SYNCED". Set to NOT_SYNCED by every
    // local write; flipped back to SYNCED by the upload delta batch in
    // SyncRepository after a successful .upsert() call.
    val syncStatus: String = SyncStatus.NOT_SYNCED,
) : Parcelable

/**
 * Possible values for [Book.syncStatus]. Kept as a string constant set
 * (rather than an enum) so that Room's TEXT column stays trivially
 * migratable and SQL-queryable.
 */
object SyncStatus {
    const val SYNCED = "SYNCED"
    const val NOT_SYNCED = "NOT_SYNCED"
}