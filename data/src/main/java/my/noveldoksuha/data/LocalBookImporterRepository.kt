package my.noveldokusha.data

import android.content.Context
import android.content.Intent
import android.provider.OpenableColumns
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import my.noveldokusha.core.AppFileResolver
import my.noveldokusha.core.fileImporter
import my.noveldokusha.core.isCbzFile
import my.noveldokusha.core.isFb2File
import my.noveldokusha.core.tryAsResponse
import my.noveldokusha.core.utils.encodePages
import my.noveldokusha.epub_tooling.CbzMetadata
import my.noveldokusha.epub_tooling.cbzMetadataParser
import my.noveldokusha.epub_tooling.epubParser
import my.noveldokusha.epub_tooling.EpubBook
import my.noveldokusha.epub_tooling.fb2Parser
import my.noveldokusha.feature.local_database.AppDatabase
import my.noveldokusha.feature.local_database.tables.Book
import my.noveldokusha.feature.local_database.tables.Chapter
import my.noveldokusha.feature.local_database.tables.ChapterBody
import my.noveldokusha.feature.local_database.tables.ChapterPages
import my.noveldokusha.feature.local_database.tables.DownloadedPageChapter
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalBookImporterRepository @Inject constructor(
    private val libraryBooks: LibraryBooksRepository,
    private val bookChapters: BookChaptersRepository,
    private val chapterBody: ChapterBodyRepository,
    private val appFileResolver: AppFileResolver,
    private val appDatabase: AppDatabase,
    @ApplicationContext private val context: Context,
) {
    suspend fun importFromContentUri(
        contentUri: String,
        bookTitle: String,
        addToLibrary: Boolean = false
    ) = tryAsResponse {
        val uri = contentUri.toUri()

        val mime = context.contentResolver.query(
            uri, arrayOf(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE),
            null, null, null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }

        if (mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) {
            importDirectoryFromContentUri(uri, bookTitle, addToLibrary)
            return@tryAsResponse
        }

        try {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}

        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Failed to open input stream")
        val fileName = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null, null, null
        )?.use { if (it.moveToFirst()) it.getString(0) else null } ?: bookTitle

        if (fileName.isCbzFile()) {
            val bookData = cbzMetadataParser(inputStream)
            Timber.d("LocalImport: CBZ file=$fileName pages=${bookData.entryNames.size}")
            cbzImporter(
                storageFolderName = bookTitle,
                metadata = bookData,
                addToLibrary = addToLibrary,
                contentUri = contentUri
            )
        } else {
            val bookData = inputStream.use { stream ->
                if (fileName.isFb2File()) fb2Parser(stream) else epubParser(stream)
            }
            Timber.d("LocalImport: file=$fileName isFb2=${fileName.isFb2File()} chapters=${bookData.chapters.size}")
            epubImporter(
                storageFolderName = bookTitle,
                epub = bookData,
                addToLibrary = addToLibrary
            )
        }
    }

    private suspend fun importDirectoryFromContentUri(
        dirUri: android.net.Uri,
        bookTitle: String,
        addToLibrary: Boolean
    ) {
        val pathSegments = dirUri.pathSegments
        if (pathSegments.size < 2) throw Exception("Invalid URI: $dirUri")
        val treeDocumentId = pathSegments[1]
        val authority = dirUri.authority ?: throw Exception("No authority in URI")
        val treeUri = android.net.Uri.Builder().scheme("content").authority(authority)
            .appendPath("tree").appendPath(treeDocumentId).build()
        val docId = android.provider.DocumentsContract.getDocumentId(dirUri)
        val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

        val cbzFiles = mutableListOf<Pair<String, android.net.Uri>>()
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            ),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val fileName = cursor.getString(0)
                if (fileName.isCbzFile()) {
                    val fileUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(childrenUri, cursor.getString(1))
                    cbzFiles.add(fileName to fileUri)
                }
            }
        }

        val sortedCbz = cbzFiles.sortedWith(compareBy { it.first.lowercase().replace(Regex("\\d+")) { m -> m.value.padStart(12, '0') } })
        val chapters = sortedCbz.mapNotNull { (fileName, fileUri) ->
            try {
                val inputStream = context.contentResolver.openInputStream(fileUri) ?: return@mapNotNull null
                val metadata = inputStream.use { stream -> cbzMetadataParser(inputStream = stream) }
                my.noveldokusha.data.CbzFolderChapter(
                    title = fileName.substringBeforeLast('.'),
                    contentUri = fileUri.toString(),
                    metadata = metadata,
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse CBZ metadata: $fileName")
                null
            }
        }
        if (chapters.isEmpty()) return

        cbzFolderImporter(
            storageFolderName = bookTitle,
            chapters = chapters,
            addToLibrary = addToLibrary
        )
    }

    suspend fun epubImporter(
        storageFolderName: String,
        epub: EpubBook,
        addToLibrary: Boolean
    ): Unit = withContext(Dispatchers.IO) {
        val localBookUrl = appFileResolver.getLocalBookPath(storageFolderName)

        appDatabase.transaction {
            Timber.d("LocalImport: start url=$localBookUrl chapters=${epub.chapters.size} images=${epub.images.size}")
            if (epub.chapters.isEmpty()) {
                Timber.w("LocalImport: EMPTY chapters for $localBookUrl — книга не откроется")
            }
            // First clean any previous entries from the book
            bookChapters.chapters(localBookUrl)
                .map { it.url }
                .let { chapterBody.removeRows(it) }
            bookChapters.removeAllFromBook(localBookUrl)
            libraryBooks.remove(localBookUrl)

            val coverImage = epub.coverImage
            if (coverImage != null) {
                fileImporter(
                    targetFile = appFileResolver.getStorageBookCoverImageFile(storageFolderName),
                    imageData = coverImage.image
                )
            }

            // Insert new book data
            Book(
                title = storageFolderName,
                url = localBookUrl,
                coverImageUrl = appFileResolver.getLocalBookCoverPath(),
                inLibrary = addToLibrary
            ).let { libraryBooks.insert(it) }

            epub.chapters.mapIndexed { i, chapter ->
                Chapter(
                    title = chapter.title,
                    url = appFileResolver.getLocalBookChapterPath(storageFolderName, chapter.absPath),
                    bookUrl = localBookUrl,
                    position = i
                )
            }.let { bookChapters.insert(it) }

            epub.chapters.map { chapter ->
                ChapterBody(
                    url = appFileResolver.getLocalBookChapterPath(storageFolderName, chapter.absPath),
                    body = chapter.body
                )
            }.let { chapterBody.insertReplace(it) }
            Timber.d("LocalImport: done url=$localBookUrl inserted chapters=${epub.chapters.size} bodies=${epub.chapters.size}")
        }

        epub.images.map {
            async {
                fileImporter(
                    targetFile = appFileResolver.getStorageBookImageFile(
                        storageFolderName,
                        it.absPath
                    ),
                    imageData = it.image
                )
            }
        }.awaitAll()
    }

    /**
     * Импорт CBZ — манга/манхва. Страницы читаются из ZIP напрямую
     * (cbz:// URL), без распаковки на диск. Book.contentType = "manga"
     * → ридер открывает MangaReaderActivity.
     */
    suspend fun cbzImporter(
        storageFolderName: String,
        metadata: CbzMetadata,
        addToLibrary: Boolean,
        contentUri: String? = null
    ): Unit = withContext(Dispatchers.IO) {
        val localBookUrl = appFileResolver.getLocalBookPath(storageFolderName)
        val chapterUrl = appFileResolver.getLocalBookChapterPath(storageFolderName, "chapter")

        val pageUrls = metadata.entryNames.map { "cbz://${contentUri ?: ""}#$it" }

        appDatabase.transaction {
            Timber.d("LocalCbzImport: start url=$localBookUrl pages=${metadata.entryNames.size}")

            bookChapters.chapters(localBookUrl)
                .map { it.url }
                .let { chapterBody.removeRows(it) }
            bookChapters.removeAllFromBook(localBookUrl)
            libraryBooks.remove(localBookUrl)

            metadata.coverImage?.let { coverImage ->
                fileImporter(
                    targetFile = appFileResolver.getStorageBookCoverImageFile(storageFolderName),
                    imageData = coverImage.image
                )
            }

            // contentType = "manga" → ReaderActivity открывает MangaReaderActivity
            Book(
                title = storageFolderName,
                url = localBookUrl,
                coverImageUrl = appFileResolver.getLocalBookCoverPath(),
                inLibrary = addToLibrary,
                contentType = "manga",
                description = metadata.summary,
                genres = metadata.genre,
            ).let { libraryBooks.insert(it) }

            listOf(
                Chapter(
                    title = storageFolderName,
                    url = chapterUrl,
                    bookUrl = localBookUrl,
                    position = 0
                )
            ).let { bookChapters.insert(it) }

            appDatabase.chapterPagesDao().insertReplace(
                ChapterPages(url = chapterUrl, pages = encodePages(pageUrls))
            )

            Timber.d("LocalCbzImport: done url=$localBookUrl pages=${metadata.entryNames.size}")
        }
    }

    /**
     * Импорт папки с несколькими .cbz как одной манги (Komga/Mihon стиль).
     * Каждый .cbz = отдельная глава. Обложка берётся из первого .cbz.
     */
    suspend fun cbzFolderImporter(
        storageFolderName: String,
        chapters: List<CbzFolderChapter>,
        addToLibrary: Boolean
    ): Unit = withContext(Dispatchers.IO) {
        if (chapters.isEmpty()) return@withContext
        val localBookUrl = appFileResolver.getLocalBookPath(storageFolderName)

        appDatabase.transaction {
            Timber.d("LocalCbzFolderImport: start url=$localBookUrl chapters=${chapters.size}")

            bookChapters.chapters(localBookUrl)
                .map { it.url }
                .let { chapterBody.removeRows(it) }
            bookChapters.removeAllFromBook(localBookUrl)
            libraryBooks.remove(localBookUrl)

            val coverImage = chapters.firstOrNull()?.metadata?.coverImage
            coverImage?.let {
                fileImporter(
                    targetFile = appFileResolver.getStorageBookCoverImageFile(storageFolderName),
                    imageData = it.image
                )
            }

            val firstMeta = chapters.firstOrNull()?.metadata

            Book(
                title = storageFolderName,
                url = localBookUrl,
                coverImageUrl = appFileResolver.getLocalBookCoverPath(),
                inLibrary = addToLibrary,
                contentType = "manga",
                description = firstMeta?.summary.orEmpty(),
                genres = firstMeta?.genre.orEmpty(),
            ).let { libraryBooks.insert(it) }

            chapters.forEachIndexed { i, chapter ->
                val chapterUrl = appFileResolver.getLocalBookChapterPath(
                    storageFolderName, "ch_${chapter.title}"
                )
                val pageUrls = chapter.metadata.entryNames.map {
                    "cbz://${chapter.contentUri}#$it"
                }

                bookChapters.insert(
                    listOf(
                        Chapter(
                            title = chapter.title,
                            url = chapterUrl,
                            bookUrl = localBookUrl,
                            position = i
                        )
                    )
                )

                appDatabase.chapterPagesDao().insertReplace(
                    ChapterPages(url = chapterUrl, pages = encodePages(pageUrls))
                )
            }

            Timber.d("LocalCbzFolderImport: done url=$localBookUrl chapters=${chapters.size}")
        }
    }
}

data class CbzFolderChapter(
    val title: String,
    val contentUri: String,
    val metadata: CbzMetadata,
)