package my.noveldokusha.scraper.domain

/**
 * Исключение когда плагин вызвал show_error(title, message).
 * Перехватывается в DownloaderRepository и конвертируется в Response.Error.
 */
class PluginShowErrorException(
    val errorTitle: String,
    override val message: String,
    val authUrl: String? = null
) : Exception(message)
