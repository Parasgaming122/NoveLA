package my.noveldokusha.epub_tooling

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.StringReader
import java.util.zip.ZipFile

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")

// ponytail: дублирует limits из EpubParser — тот файл package-private, менять visibility ради CBZ не стоит
private const val MAX_CBZ_ENTRY_SIZE = 32L * 1024 * 1024

private fun isImageEntry(name: String): Boolean {
    if (name.endsWith("/")) return false
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext in IMAGE_EXTENSIONS
}

private fun naturalSortKey(name: String): String =
    name.replace(Regex("\\d+")) { it.value.padStart(12, '0') }

private fun sortedImageEntries(zip: ZipFile): List<java.util.zip.ZipEntry> =
    zip.entries().asSequence()
        .filter { isImageEntry(it.name) }
        .sortedBy { naturalSortKey(it.name) }
        .toList()

private fun findCoverEntry(entries: List<java.util.zip.ZipEntry>): java.util.zip.ZipEntry? =
    entries.firstOrNull {
        !it.name.contains('/') &&
            it.name.substringAfterLast('/').substringBefore('.').lowercase() == "cover"
    } ?: entries.firstOrNull()

data class CbzMetadata(
    val entryNames: List<String>,
    val coverImage: EpubBook.Image?,
    val summary: String = "",
    val genre: String = "",
)

/**
 * Легковесный парсинг: список имён entries + обложка + ComicInfo.xml метаданные.
 * Не читает все картинки в память — только имена и обложку.
 */
@Throws(Exception::class)
suspend fun cbzMetadataParser(
    inputStream: InputStream
): CbzMetadata = withContext(Dispatchers.Default) {
    val tempFile = copyToTempFile(inputStream)
    try {
        ZipFile(tempFile).use { zip ->
            val entries = sortedImageEntries(zip)
            val names = entries.map { it.name }
            val coverEntry = findCoverEntry(entries)
            val coverImage = coverEntry?.let { entry ->
                if (entry.size > MAX_CBZ_ENTRY_SIZE) return@let null
                zip.getInputStream(entry).use { stream ->
                    EpubBook.Image(absPath = entry.name, image = stream.readBytes())
                }
            }
            val comicInfo = parseComicInfo(zip)
            CbzMetadata(
                entryNames = names,
                coverImage = coverImage,
                summary = comicInfo.summary,
                genre = comicInfo.genre,
            )
        }
    } finally {
        tempFile.delete()
    }
}

@Throws(Exception::class)
suspend fun cbzCoverParser(
    inputStream: InputStream
): EpubBook.Image? = withContext(Dispatchers.Default) {
    val tempFile = copyToTempFile(inputStream)
    try {
        ZipFile(tempFile).use { zip ->
            val entries = sortedImageEntries(zip)
            val coverEntry = findCoverEntry(entries) ?: return@use null
            if (coverEntry.size > MAX_CBZ_ENTRY_SIZE) return@use null
            zip.getInputStream(coverEntry).use { stream ->
                EpubBook.Image(absPath = coverEntry.name, image = stream.readBytes())
            }
        }
    } finally {
        tempFile.delete()
    }
}

private data class ComicInfoData(val summary: String = "", val genre: String = "")

private fun parseComicInfo(zip: ZipFile): ComicInfoData {
    val entry = zip.getEntry("ComicInfo.xml") ?: return ComicInfoData()
    val xml = zip.getInputStream(entry).use { it.bufferedReader().readText() }

    val factory = XmlPullParserFactory.newInstance()
    factory.isNamespaceAware = true
    val parser = factory.newPullParser()
    parser.setInput(StringReader(xml))

    var summary = ""
    var genre = ""
    var currentTag = ""

    while (parser.eventType != XmlPullParser.END_DOCUMENT) {
        when (parser.eventType) {
            XmlPullParser.START_TAG -> currentTag = parser.name
            XmlPullParser.TEXT -> {
                val text = parser.text?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    when (currentTag) {
                        "Summary" -> summary = text
                        "Genre" -> genre = text
                    }
                }
            }
        }
        parser.next()
    }
    return ComicInfoData(summary = summary, genre = genre)
}
