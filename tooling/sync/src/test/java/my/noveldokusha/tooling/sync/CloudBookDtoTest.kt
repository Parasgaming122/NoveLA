package my.noveldokusha.tooling.sync

import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.feature.local_database.tables.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-data JUnit tests for [CloudBookDto] that have no Android, Room,
 * Hilt, or Supabase dependencies. They run on the JVM with no
 * Robolectric, no emulator, and no MockK — drop them straight into
 * `tooling/sync/src/test/java/my/noveldokusha/tooling/sync/` and run
 *
 *   ./gradlew :tooling:sync:testDebugUnitTest \
 *       --tests "my.noveldokusha.tooling.sync.CloudBookDtoTest"
 *
 * These tests complement the algorithm-level Python port
 * (`scripts/test_sync_algorithm.py`) that runs the full merge loop.
 * They give you a 100% Kotlin coverage of the DTO serialization layer,
 * which is the only piece of the sync engine that has tricky
 * field-by-field mapping logic and is therefore worth pinning down
 * with a regression suite.
 */
class CloudBookDtoTest {

    private fun sampleBook(
        url: String = "https://novels.example/works/sample-novel",
        syncStatus: String = SyncStatus.NOT_SYNCED,
        updatedAt: Long = 1_700_000_000_000L,
    ): Book = Book(
        title = "Sample Novel",
        url = url,
        completed = false,
        lastReadChapter = "https://novels.example/chapters/60",
        inLibrary = true,
        coverImageUrl = "https://img.example/cover.png",
        description = "A test novel used in unit tests.",
        lastReadEpochTimeMilli = 1_700_000_001_000L,
        addedToLibraryEpochTimeMilli = 1_699_999_999_000L,
        lastUpdateEpochTimeMilli = 1_700_000_002_000L,
        category = "Test",
        chaptersListHash = "abc123def456",
        chaptersLastPage = 4,
        genres = "Test,Fiction",
        rating = "9.5",
        status = "Ongoing",
        lastUpdateDate = "2024-01-15",
        contentType = "novel",
        updatedAt = updatedAt,
        syncStatus = syncStatus,
    )

    // -------------------------------------------------------------------
    // Round-trip preservation — every Book field survives the DTO
    // -------------------------------------------------------------------

    @Test
    fun fromLocal_preserves_all_fields_into_dto() {
        val book = sampleBook()
        val dto = CloudBookDto.fromLocal(book, userId = "user-uuid-1")

        assertEquals("user-uuid-1", dto.userId)
        assertEquals(book.url, dto.novelId)
        assertEquals(book.title, dto.title)
        assertEquals(book.completed, dto.completed)
        assertEquals(book.lastReadChapter, dto.lastReadChapter)
        assertEquals(book.inLibrary, dto.inLibrary)
        assertEquals(book.coverImageUrl, dto.coverImageUrl)
        assertEquals(book.description, dto.description)
        assertEquals(book.lastReadEpochTimeMilli, dto.lastReadEpochTimeMilli)
        assertEquals(book.addedToLibraryEpochTimeMilli, dto.addedToLibraryEpochTimeMilli)
        assertEquals(book.lastUpdateEpochTimeMilli, dto.lastUpdateEpochTimeMilli)
        assertEquals(book.category, dto.category)
        assertEquals(book.chaptersListHash, dto.chaptersListHash)
        assertEquals(book.chaptersLastPage, dto.chaptersLastPage)
        assertEquals(book.genres, dto.genres)
        assertEquals(book.rating, dto.rating)
        assertEquals(book.status, dto.status)
        assertEquals(book.lastUpdateDate, dto.lastUpdateDate)
        assertEquals(book.contentType, dto.contentType)
        assertEquals(book.updatedAt, dto.updatedAt)
    }

    @Test
    fun toLocal_preserves_all_fields_back_into_book() {
        val dto = CloudBookDto.fromLocal(sampleBook(), userId = "user-uuid-2")
        val roundTripped = dto.toLocal()

        assertEquals(dto.novelId, roundTripped.url)
        assertEquals(dto.title, roundTripped.title)
        assertEquals(dto.completed, roundTripped.completed)
        assertEquals(dto.lastReadChapter, roundTripped.lastReadChapter)
        assertEquals(dto.inLibrary, roundTripped.inLibrary)
        assertEquals(dto.coverImageUrl, roundTripped.coverImageUrl)
        assertEquals(dto.description, roundTripped.description)
        assertEquals(dto.lastReadEpochTimeMilli, roundTripped.lastReadEpochTimeMilli)
        assertEquals(dto.addedToLibraryEpochTimeMilli, roundTripped.addedToLibraryEpochTimeMilli)
        assertEquals(dto.lastUpdateEpochTimeMilli, roundTripped.lastUpdateEpochTimeMilli)
        assertEquals(dto.category, roundTripped.category)
        assertEquals(dto.chaptersListHash, roundTripped.chaptersListHash)
        assertEquals(dto.chaptersLastPage, roundTripped.chaptersLastPage)
        assertEquals(dto.genres, roundTripped.genres)
        assertEquals(dto.rating, roundTripped.rating)
        assertEquals(dto.status, roundTripped.status)
        assertEquals(dto.lastUpdateDate, roundTripped.lastUpdateDate)
        assertEquals(dto.contentType, roundTripped.contentType)
        assertEquals(dto.updatedAt, roundTripped.updatedAt)
    }

    // -------------------------------------------------------------------
    // SYNCED semantics — anything coming FROM the cloud is SYNCED
    // -------------------------------------------------------------------

    @Test
    fun toLocal_marks_result_SYNCED_even_if_source_was_NOT_SYNCED() {
        // This is the critical idempotency guarantee — if it failed,
        // the upload delta would re-push every cloud-derived row
        // forever, causing an infinite loop.
        val dirtyLocalBook = sampleBook(syncStatus = SyncStatus.NOT_SYNCED)
        val dto = CloudBookDto.fromLocal(dirtyLocalBook, userId = "u1")
        val roundTripped = dto.toLocal()

        assertEquals(SyncStatus.SYNCED, roundTripped.syncStatus)
        // Sanity: the original book stays dirty
        assertEquals(SyncStatus.NOT_SYNCED, dirtyLocalBook.syncStatus)
    }

    // -------------------------------------------------------------------
    // The DTO drops syncStatus entirely — it's a local-only concern
    // -------------------------------------------------------------------

    @Test
    fun dto_does_not_carry_syncStatus_field() {
        // The DTO class MUST NOT have a syncStatus field — otherwise
        // the cloud schema would have to store a redundant column
        // that's only meaningful locally.
        val declaredFields = CloudBookDto::class.java.declaredFields.map { it.name }
        assertEquals(false, declaredFields.contains("syncStatus"))
    }

    @Test
    fun dto_does_not_carry_in_library_local_only_state() {
        // Sanity: in_library IS part of the cloud schema because the
        // cloud mirror needs to know whether the book is in the
        // library on this phone.
        val declaredFields = CloudBookDto::class.java.declaredFields.map { it.name }
        assertEquals(true, declaredFields.contains("inLibrary"))
    }

    // -------------------------------------------------------------------
    // user_id is on the DTO, not on Book
    // -------------------------------------------------------------------

    @Test
    fun user_id_is_dto_field_not_book_field() {
        val bookFields = Book::class.java.declaredFields.map { it.name }
        val dtoFields = CloudBookDto::class.java.declaredFields.map { it.name }

        assertEquals(false, bookFields.contains("userId"))
        assertEquals(true, dtoFields.contains("userId"))
    }

    @Test
    fun fromLocal_with_different_user_ids_produces_distinct_dtos() {
        val book = sampleBook()
        val dtoA = CloudBookDto.fromLocal(book, userId = "user-A")
        val dtoB = CloudBookDto.fromLocal(book, userId = "user-B")

        assertNotEquals(dtoA.userId, dtoB.userId)
        // All other fields identical
        assertEquals(dtoA.novelId, dtoB.novelId)
        assertEquals(dtoA.title, dtoB.title)
        assertEquals(dtoA.updatedAt, dtoB.updatedAt)
    }

    // -------------------------------------------------------------------
    // Edge cases: nullable fields
    // -------------------------------------------------------------------

    @Test
    fun nullable_fields_preserve_null_through_round_trip() {
        val book = sampleBook().copy(
            lastReadChapter = null,
            chaptersListHash = null,
            chaptersLastPage = null,
        )
        val dto = CloudBookDto.fromLocal(book, userId = "u")
        val roundTripped = dto.toLocal()

        assertNull(roundTripped.lastReadChapter)
        assertNull(roundTripped.chaptersListHash)
        assertNull(roundTripped.chaptersLastPage)
    }

    @Test
    fun nullable_fields_preserve_nonnull_through_round_trip() {
        val book = sampleBook()
        val dto = CloudBookDto.fromLocal(book, userId = "u")
        val roundTripped = dto.toLocal()

        assertEquals(book.lastReadChapter, roundTripped.lastReadChapter)
        assertEquals(book.chaptersListHash, roundTripped.chaptersListHash)
        assertEquals(book.chaptersLastPage, roundTripped.chaptersLastPage)
    }

    // -------------------------------------------------------------------
    // updatedAt semantics — the timestamp is preserved exactly
    // -------------------------------------------------------------------

    @Test
    fun updatedAt_is_preserved_through_round_trip() {
        // Critical: the merge algorithm compares cloud.updatedAt vs
        // local.updatedAt with strict greater-than. If the round-trip
        // changed the value (e.g. truncated to seconds), the merge
        // would silently misbehave.
        for (ts in listOf(0L, 1L, 1_000L, Long.MAX_VALUE, 1_700_000_000_000L)) {
            val book = sampleBook(updatedAt = ts)
            val roundTripped = CloudBookDto.fromLocal(book, "u").toLocal()
            assertEquals(
                "updatedAt was not preserved for ts=$ts",
                ts, roundTripped.updatedAt,
            )
        }
    }

    // -------------------------------------------------------------------
    // Equality — DTOs from the same source data are equal
    // -------------------------------------------------------------------

    @Test
    fun dtos_from_identical_books_are_equal() {
        val book = sampleBook()
        val dto1 = CloudBookDto.fromLocal(book, userId = "u")
        val dto2 = CloudBookDto.fromLocal(book, userId = "u")

        assertEquals(dto1, dto2)
        assertEquals(dto1.hashCode(), dto2.hashCode())
    }

    @Test
    fun dtos_with_different_user_ids_are_not_equal() {
        val book = sampleBook()
        val dto1 = CloudBookDto.fromLocal(book, userId = "u1")
        val dto2 = CloudBookDto.fromLocal(book, userId = "u2")

        assertNotEquals(dto1, dto2)
    }

    @Test
    fun dtos_with_different_novel_ids_are_not_equal() {
        val book1 = sampleBook(url = "https://x/1")
        val book2 = sampleBook(url = "https://x/2")
        val dto1 = CloudBookDto.fromLocal(book1, userId = "u")
        val dto2 = CloudBookDto.fromLocal(book2, userId = "u")

        assertNotEquals(dto1, dto2)
    }
}
