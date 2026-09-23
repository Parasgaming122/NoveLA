package my.noveldokusha.tooling.local_source

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddToPhotos
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.noveldokusha.coreui.components.SlimListItem
import my.noveldokusha.coreui.theme.textPadding
import my.noveldokusha.core.AppFileResolver
import my.noveldokusha.core.PagedList
import my.noveldokusha.core.Response
import my.noveldokusha.core.asSequence
import my.noveldokusha.core.fileImporter
import my.noveldokusha.core.getOrNull
import my.noveldokusha.core.isCbzFile
import my.noveldokusha.data.LocalBookImporterRepository
import my.noveldokusha.epub_tooling.cbzCoverParser
import my.noveldokusha.epub_tooling.cbzMetadataParser
import my.noveldokusha.data.CbzFolderChapter
import my.noveldokusha.network.tryConnect
import my.noveldokusha.scraper.R
import my.noveldokusha.scraper.domain.BookResult
import my.noveldokusha.scraper.domain.ChapterResult
import my.noveldokusha.scraper.sources.LocalSource
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLocalMangaSource @Inject constructor(
    @ApplicationContext private val appContext: Context,
    localSourcesDirectoriesFactory: LocalSourcesDirectories.Factory,
    private val appFileResolver: AppFileResolver,
) : LocalSource {
    private val localSourcesDirectories = localSourcesDirectoriesFactory.create("manga")
    override val id = "local_manga_source"
    override val nameStrId = R.string.source_name_local_manga
    override val baseUrl = "local-manga://"
    override val catalogUrl = "local-manga://"
    override val isLocalSource = true
    override val language = null
    override val iconUrl: String? = null
    override val iconResId: Int = R.drawable.ic_cbz
    override val contentType = "manga"

    override suspend fun getChapterList(bookUrl: String): Response<List<ChapterResult>> {
        return withContext(Dispatchers.IO) {
            tryConnect {
                val bookTitle = bookUrl.removePrefix("local://")

                val file = findBookFileInCatalogs(bookTitle)
                    ?: throw UnsupportedOperationException("Book not found: $bookTitle")

                val docFile = DocumentFile.fromSingleUri(appContext, file)
                    ?: throw UnsupportedOperationException("Cannot open: $file")

                if (docFile.isDirectory) {
                    docFile.listFiles()
                        .filter { it.name?.endsWith(".cbz", true) == true }
                        .mapIndexed { i, cbz ->
                            ChapterResult(
                                title = cbz.name?.substringBeforeLast('.') ?: "Chapter ${i + 1}",
                                url = appFileResolver.getLocalBookChapterPath(bookTitle, "ch_${cbz.name?.substringBeforeLast('.')}")
                            )
                        }
                } else {
                    listOf(
                        ChapterResult(
                            title = bookTitle,
                            url = appFileResolver.getLocalBookChapterPath(bookTitle, "chapter")
                        )
                    )
                }
            }
        }
    }

    private fun findBookFileInCatalogs(storageFolderName: String): Uri? {
        return localSourcesDirectories
            .list
            .asSequence()
            .flatMap { dirUri ->
                DocumentsContract.buildChildDocumentsUriUsingTree(
                    dirUri, DocumentsContract.getTreeDocumentId(dirUri)
                ).collectMangaBooks()
            }
            .firstOrNull { book ->
                val fileTitle = book.title.substringBeforeLast('.')
                fileTitle == storageFolderName || book.title == storageFolderName
            }
            ?.url?.toUri()
    }

    private val validMIMES = setOf(
        "application/vnd.comicbook+zip",
        "application/zip",
        DocumentsContract.Document.MIME_TYPE_DIR
    )

    private fun String.isBookFile(): Boolean {
        return this.lowercase().endsWith(".cbz")
    }

    private fun Uri.collectMangaBooks(): Sequence<BookResult> {
        val rootURI = this
        return appContext.contentResolver.query(
            rootURI,
            arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null, null, null,
        )?.asSequence()?.flatMap { cursor ->
            val fileName = cursor.getString(0)
            val docId = cursor.getString(1)
            val mime = cursor.getString(2)
            val entryUri = DocumentsContract.buildDocumentUriUsingTree(rootURI, docId)
            when {
                fileName.isBookFile() -> sequenceOf(
                    BookResult(title = fileName.substringBeforeLast('.'), url = entryUri.toString(), contentType = "manga")
                )
                mime == DocumentsContract.Document.MIME_TYPE_DIR -> {
                    val dirChildren = DocumentsContract.buildChildDocumentsUriUsingTree(
                        rootURI, docId
                    )
                    val cbzChildren = appContext.contentResolver.query(
                        dirChildren,
                        arrayOf(
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_MIME_TYPE,
                        ),
                        null, null, null,
                    )?.use { childCursor ->
                        val names = mutableListOf<String>()
                        while (childCursor.moveToNext()) {
                            val childName = childCursor.getString(0)
                            if (childName.isBookFile()) {
                                names.add(childName)
                            }
                        }
                        names
                    } ?: emptyList()
                    if (cbzChildren.isNotEmpty()) {
                        sequenceOf(
                            BookResult(title = fileName, url = entryUri.toString(), contentType = "manga")
                        )
                    } else {
                        emptySequence()
                    }
                }
                else -> emptySequence()
            }
        } ?: emptySequence()
    }

    private fun Uri.collectMangaBooksWithSearch(text: String): Sequence<BookResult> {
        val rootURI = this
        return appContext.contentResolver.query(
            rootURI,
            arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null, null, null,
        )?.asSequence()?.flatMap { cursor ->
            val fileName = cursor.getString(0)
            val docId = cursor.getString(1)
            val mime = cursor.getString(2)
            val entryUri = DocumentsContract.buildDocumentUriUsingTree(rootURI, docId)
            when {
                fileName.isBookFile() && fileName.contains(text, ignoreCase = true) -> sequenceOf(
                    BookResult(title = fileName.substringBeforeLast('.'), url = entryUri.toString(), contentType = "manga")
                )
                mime == DocumentsContract.Document.MIME_TYPE_DIR && fileName.contains(text, ignoreCase = true) -> {
                    val dirChildren = DocumentsContract.buildChildDocumentsUriUsingTree(
                        rootURI, docId
                    )
                    val hasCbz = appContext.contentResolver.query(
                        dirChildren,
                        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                        null, null, null,
                    )?.use { childCursor ->
                        while (childCursor.moveToNext()) {
                            if (childCursor.getString(0).isBookFile()) return@use true
                        }
                        false
                    } ?: false
                    if (hasCbz) sequenceOf(BookResult(title = fileName, url = entryUri.toString(), contentType = "manga"))
                    else emptySequence()
                }
                else -> emptySequence()
            }
        } ?: emptySequence()
    }

    override suspend fun getCatalogList(
        index: Int
    ): Response<PagedList<BookResult>> = withContext(Dispatchers.IO) {
        tryConnect {
            val files = localSourcesDirectories
                .list
                .asSequence()
                .flatMap {
                    DocumentsContract.buildChildDocumentsUriUsingTree(
                        it, DocumentsContract.getTreeDocumentId(it)
                    ).collectMangaBooks()
                }
                .map { async { tryConnect { addCover(it) }.getOrNull() } }
                .toList()
                .awaitAll()
                .filterNotNull()

            PagedList(
                list = files,
                index = 0,
                isLastPage = true
            )
        }
    }

    private suspend fun addCover(
        bookResult: BookResult
    ): BookResult = withContext(Dispatchers.IO) {
        val coverFile = appFileResolver.getStorageBookCoverImageFile(bookResult.title)
        if (!coverFile.exists()) {
            val uri = bookResult.url.toUri()
            val inputStream = findFirstCbzInputStream(uri)
                ?: return@withContext bookResult
            val coverImage = inputStream.use { stream ->
                cbzCoverParser(inputStream = stream)
            } ?: return@withContext bookResult
            fileImporter(
                targetFile = coverFile,
                imageData = coverImage.image,
            )
        }
        bookResult.copy(
            coverImageUrl = coverFile.canonicalFile.absolutePath
        )
    }

    private fun findFirstCbzInputStream(uri: android.net.Uri): java.io.InputStream? {
        val mime = appContext.contentResolver.query(
            uri, arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE),
            null, null, null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }

        if (mime != DocumentsContract.Document.MIME_TYPE_DIR) {
            return appContext.contentResolver.openInputStream(uri)
        }

        // Document URI built via buildDocumentUriUsingTree has path: /tree/{treeId}/document/{docId}
        // Extract tree document ID from path[1], NOT from getDocumentId() which returns the doc ID.
        val pathSegments = uri.pathSegments
        if (pathSegments.size < 4) return null
        val authority = uri.authority ?: return null
        val treeDocumentId = pathSegments[1]
        val treeUri = Uri.Builder().scheme("content").authority(authority)
            .appendPath("tree").appendPath(treeDocumentId).build()
        val docId = DocumentsContract.getDocumentId(uri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

        return appContext.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            ),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(0).isBookFile()) {
                    val cbzUri = DocumentsContract.buildDocumentUriUsingTree(childrenUri, cursor.getString(1))
                    return@use appContext.contentResolver.openInputStream(cbzUri)
                }
            }
            null
        }
    }

    override suspend fun getCatalogSearch(
        index: Int,
        input: String
    ): Response<PagedList<BookResult>> = withContext(Dispatchers.IO) {
        if (index > 0) {
            return@withContext Response.Success(PagedList.createEmpty(index))
        }
        tryConnect {
            val files = localSourcesDirectories
                .list
                .asSequence()
                .flatMap {
                    DocumentsContract.buildChildDocumentsUriUsingTree(
                        it, DocumentsContract.getTreeDocumentId(it)
                    ).collectMangaBooksWithSearch(input)
                }
                .map { async { tryConnect { addCover(it) }.getOrNull() } }
                .toList()
                .awaitAll()
                .filterNotNull()

            PagedList(
                list = files,
                index = 0,
                isLastPage = true
            )
        }
    }

    private var isImporting by mutableStateOf(false)
    private var importProgress by mutableStateOf("")

    private suspend fun importAllBooksFromDirectory(dirUri: Uri) {
        withContext(Dispatchers.IO) {
            isImporting = true
            try {
                val treeUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    dirUri, DocumentsContract.getTreeDocumentId(dirUri)
                )
                val entries = mutableListOf<Pair<String, Uri>>()
                appContext.contentResolver.query(
                    treeUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                    ),
                    null, null, null,
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val fileName = cursor.getString(0)
                        val docId = cursor.getString(1)
                        val mime = cursor.getString(2)
                        if (fileName.isBookFile() || mime in validMIMES) {
                            val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            entries.add(fileName to fileUri)
                        }
                    }
                }

                var imported = 0
                val epubImporterRepository = EntryPointAccessors.fromApplication<LocalMangaBookImporterEntryPoint>(appContext).epubImporterRepository()
                for ((name, uri) in entries) {
                    importProgress = appContext.getString(
                        R.string.importing_books_progress,
                        imported + 1,
                        entries.size
                    )
                    try {
                        val isDir = appContext.contentResolver.query(
                            uri,
                            arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE),
                            null, null, null,
                        )?.use { it.moveToFirst() && it.getString(0) == DocumentsContract.Document.MIME_TYPE_DIR } ?: false

                        if (isDir) {
                            importDirectoryAsManga(name, uri, epubImporterRepository)
                        } else if (name.isBookFile()) {
                            val inputStream = appContext.contentResolver.openInputStream(uri)
                                ?: continue
                            val bookData = inputStream.use { stream -> cbzMetadataParser(inputStream = stream) }
                            epubImporterRepository.cbzImporter(
                                storageFolderName = name.substringBeforeLast('.'),
                                metadata = bookData,
                                addToLibrary = true,
                                contentUri = uri.toString()
                            )
                        }
                        imported++
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to import: $name")
                    }
                }
                importProgress = appContext.getString(R.string.imported_books_count, imported)
            } finally {
                isImporting = false
            }
        }
    }

    private suspend fun importDirectoryAsManga(
        dirName: String,
        dirUri: Uri,
        epubImporterRepository: my.noveldokusha.data.LocalBookImporterRepository
    ) {
        val dirChildren = DocumentsContract.buildChildDocumentsUriUsingTree(
            dirUri, DocumentsContract.getTreeDocumentId(dirUri)
        )
        val cbzFiles = mutableListOf<Pair<String, Uri>>()
        appContext.contentResolver.query(
            dirChildren,
            arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            ),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val fileName = cursor.getString(0)
                if (fileName.isBookFile()) {
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(dirChildren, cursor.getString(1))
                    cbzFiles.add(fileName to fileUri)
                }
            }
        }
        if (cbzFiles.isEmpty()) return

        val naturalSort = Comparator<Pair<String, Uri>> { a, b ->
            a.first.lowercase().replace(Regex("\\d+")) { it.value.padStart(12, '0') }
                .compareTo(b.first.lowercase().replace(Regex("\\d+")) { it.value.padStart(12, '0') })
        }
        val sortedCbz = cbzFiles.sortedWith(naturalSort)

        val chapters = sortedCbz.mapNotNull { (fileName, fileUri) ->
            try {
                val inputStream = appContext.contentResolver.openInputStream(fileUri) ?: return@mapNotNull null
                val metadata = inputStream.use { stream -> cbzMetadataParser(inputStream = stream) }
                CbzFolderChapter(
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

        epubImporterRepository.cbzFolderImporter(
            storageFolderName = dirName,
            chapters = chapters,
            addToLibrary = true,
        )
    }

    @Composable
    override fun ScreenConfig() {
        val context by rememberUpdatedState(LocalContext.current)
        val scope = rememberCoroutineScope()
        Column(Modifier.fillMaxWidth()) {
            FilledTonalButton(
                onClick = onDoAddLocalSourceDirectory(
                    onResult = { localSourcesDirectories.add(it) }
                ),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.CreateNewFolder, null)
                    Text(text = stringResource(R.string.add_local_directory))
                }
            }
            val list by localSourcesDirectories.listState.collectAsStateWithLifecycle()
            if (list.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_directories_added_please_add_them_to_see_them_in_the_source_catalog_list),
                    Modifier.textPadding()
                )
            }
            if (isImporting) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 4.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = importProgress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            for (dirUri in list) {
                val item = remember(dirUri.toString()) { DocumentFile.fromTreeUri(context, dirUri) }
                SlimListItem(
                    headlineContent = {
                        Text(text = item?.name ?: "** Access denied **")
                    },
                    leadingContent = {
                        Icon(Icons.Filled.Folder, null)
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        importAllBooksFromDirectory(dirUri)
                                    }
                                },
                                enabled = !isImporting
                            ) {
                                Icon(
                                    Icons.Filled.AddToPhotos,
                                    stringResource(id = R.string.import_all_books)
                                )
                            }
                            IconButton(onClick = { localSourcesDirectories.remove(dirUri) }) {
                                Icon(Icons.Filled.Delete, stringResource(id = R.string.delete))
                            }
                        }
                    }
                )
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LocalMangaBookImporterEntryPoint {
    fun epubImporterRepository(): my.noveldokusha.data.LocalBookImporterRepository
}
