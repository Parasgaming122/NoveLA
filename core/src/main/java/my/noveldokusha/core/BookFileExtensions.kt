package my.noveldokusha.core

fun String.isFb2File(): Boolean {
    val lower = lowercase()
    return lower.endsWith(".fb2") || lower.endsWith(".fb2.zip")
}

fun String.isEpubFile(): Boolean = lowercase().endsWith(".epub")

fun String.isCbzFile(): Boolean = lowercase().endsWith(".cbz")

fun String.isBookFile(): Boolean = isEpubFile() || isFb2File() || isCbzFile()
