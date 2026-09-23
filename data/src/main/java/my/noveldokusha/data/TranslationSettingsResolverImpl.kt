package my.noveldokusha.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.appPreferences.TranslationLangPair
import my.noveldokusha.core.appPreferences.TranslationSettingsResolver
import my.noveldokusha.core.appPreferences.TranslationSettingsResolver.ActiveTranslatorLevel
import my.noveldokusha.core.appPreferences.TranslationSettingsResolver.TranslationSettings
import my.noveldokusha.scraper.Scraper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация [TranslationSettingsResolver] поверх каскада [AppPreferences].
 *
 * Идентичность источника книги вычисляется через `scraper.getCompatibleSource(bookUrl)?.id`
 * на каждый вызов (без кэширования). `getCompatibleSource` ищет по списку ЗАГРУЖЕННЫХ
 * источников (`loadedSourcesList`), поэтому plugin-уровень применяется только к книгам,
 * чей источник сейчас загружен/установлен — ровно к тому множеству, которое конвейер
 * скачивания может получить и перевести (он сам ходит через `getCompatibleSourceCatalog`).
 * Это намеренно отличается от `getSourceId` (сырой `sourcesList`, включая CachedSource-
 * заглушки) — здесь он не используется. Без кэширования резолвер безопасен к перезагрузке
 * источников в рантайме.
 *
 * Когда источник не загружен (локальная книга или несовпадающий префикс), `sourceId = null`
 * и каскад сводится ровно к прежнему поведению per-book/global — plugin-уровень пропускается.
 */
@Singleton
class TranslationSettingsResolverImpl @Inject constructor(
    private val appPreferences: AppPreferences,
    private val scraper: Scraper,
) : TranslationSettingsResolver {

    // Идентичность источника — только загруженные источники (см. KDoc класса).
    // ponytail: sourceIdFor вынесен в публичный resolveSourceId чтобы вызывающий код
    // мог вычислить sourceId один раз и передать в остальные методы (6 вызовов → 1).
    override fun resolveSourceId(bookUrl: String): String? =
        scraper.getCompatibleSource(bookUrl)?.id

    private fun sourceIdFor(bookUrl: String, sourceId: String?): String? =
        sourceId ?: scraper.getCompatibleSource(bookUrl)?.id

    override fun translationEnabledForBook(bookUrl: String, sourceId: String?): Boolean {
        val resolvedSourceId = sourceIdFor(bookUrl, sourceId)
        return appPreferences.translationEnabledForBook(
            bookUrl = bookUrl,
            sourceId = resolvedSourceId,
        )
    }

    override fun translationPairForBook(bookUrl: String, sourceId: String?): TranslationLangPair {
        val resolvedSourceId = sourceIdFor(bookUrl, sourceId)
        return appPreferences.translationPairForBook(
            bookUrl = bookUrl,
            sourceId = resolvedSourceId,
        )
    }

    override fun translationTargetForBook(bookUrl: String, sourceId: String?): String =
        translationPairForBook(bookUrl, sourceId).target

    override fun translationScopeForBook(bookUrl: String, sourceId: String?): String {
        val resolvedSourceId = sourceIdFor(bookUrl, sourceId)
        return appPreferences.translationScopeForBook(
            bookUrl = bookUrl,
            sourceId = resolvedSourceId,
        )
    }

    override fun translationProviderForBook(bookUrl: String, sourceId: String?): String? {
        val resolvedSourceId = sourceIdFor(bookUrl, sourceId)
        return appPreferences.translationProviderForBook(
            bookUrl = bookUrl,
            sourceId = resolvedSourceId,
        )
    }

    override fun translationPromptForBook(bookUrl: String, sourceId: String?): String? {
        val resolvedSourceId = sourceIdFor(bookUrl, sourceId)
        // Каскад: per-novel → per-plugin → global (null)
        val novelPrompt = appPreferences.TRANSLATION_NOVEL_PROMPTS.value[bookUrl]?.prompt
            ?.takeIf { it.isNotBlank() }
        if (novelPrompt != null) return novelPrompt
        val pluginPrompt = resolvedSourceId?.let { appPreferences.TRANSLATION_PLUGIN_PROMPTS.value[it] }
            ?.takeIf { it.isNotBlank() }
        return pluginPrompt
    }

    // Активный переводчик книги — первый по приоритету (пер-новел > плагин > глобал)
    // уровень, который реально переводит: его флаг enable включён И пара полная.
    // Уровень определяется НЕ по наличию сохранённой пары (карты enable и pair
    // независимы), а по состоянию перевода — иначе полоска «Активный переводчик XX»
    // врала бы, показывая пер-новел при выключенном пер-новел и включённом глобале.
    override fun activeTranslatorLevelForBook(bookUrl: String): ActiveTranslatorLevel {
        val sourceId = sourceIdFor(bookUrl, null)

        // Пер-новел: включён И пара книги полная (source/target непустые).
        val bookPair = appPreferences.TRANSLATION_BOOK_LANG_PAIR.value[bookUrl]
        if (appPreferences.TRANSLATION_BOOK_ENABLED_MAP.value[bookUrl] == true &&
            bookPair != null && bookPair.source.isNotBlank() && bookPair.target.isNotBlank()
        ) return ActiveTranslatorLevel.PER_NOVEL

        // Плагин: включён И пара плагина полная (только для загруженного источника).
        if (sourceId != null && appPreferences.TRANSLATION_PLUGIN_ENABLED_MAP.value[sourceId] == true) {
            val pluginPair = appPreferences.TRANSLATION_PLUGIN_LANG_PAIR.value[sourceId]
            if (pluginPair != null && pluginPair.source.isNotBlank() && pluginPair.target.isNotBlank())
                return ActiveTranslatorLevel.PLUGIN
        }

        // Глобал: активен глобальный режим, глобальный перевод включён И пара полная.
        if (appPreferences.TRANSLATION_GLOBAL_MODE.value &&
            appPreferences.GLOBAL_TRANSLATION_ENABLED.value
        ) {
            val globalSource = appPreferences.GLOBAL_TRANSLATION_PREFERRED_SOURCE.value
            val globalTarget = appPreferences.GLOBAL_TRANSLATION_PREFERRED_TARGET.value
            if (globalSource.isNotBlank() && globalTarget.isNotBlank())
                return ActiveTranslatorLevel.GLOBAL
        }

        return ActiveTranslatorLevel.NONE
    }

    override fun pluginDisplayNameForBook(bookUrl: String): String? =
        scraper.getCompatibleSource(bookUrl)?.name?.takeIf { it.isNotBlank() }

    /**
     * Реактивный Flow: combine на все 13 преф-флоёв (per-book + per-plugin + global + hide).
     *
     * При изменении ЛЮБОГО из них — перерезолвит настройки через существующие
     * translationEnabledForBook / translationPairForBook / translationScopeForBook /
     * translationProviderForBook. Используется ViewModel для реактивного обновления UI.
     *
     * ponytail: 9 флоёв через nested combine (coroutines combine max 5 typed params),
     * лямбда не использует значения — читает .value напрямую из преференций.
     */
    override fun settingsChangeSignal(bookUrl: String): Flow<TranslationSettings> =
        combine(
            combine(
                appPreferences.TRANSLATION_BOOK_ENABLED_MAP.flow(),
                appPreferences.TRANSLATION_BOOK_LANG_PAIR.flow(),
                appPreferences.GLOBAL_TRANSLATION_ENABLED.flow(),
                appPreferences.GLOBAL_TRANSLATION_PREFERRED_TARGET.flow(),
                appPreferences.TRANSLATION_GLOBAL_MODE.flow(),
            ) { _, _, _, _, _ -> },
            combine(
                appPreferences.TRANSLATION_PLUGIN_ENABLED_MAP.flow(),
                appPreferences.TRANSLATION_PLUGIN_LANG_PAIR.flow(),
                appPreferences.TRANSLATION_PLUGIN_SCOPE.flow(),
                appPreferences.TRANSLATION_PLUGIN_PROVIDER.flow(),
            ) { _, _, _, _ -> },
            combine(
                appPreferences.TRANSLATION_PLUGIN_HIDE_LIBRARY.flow(),
                appPreferences.TRANSLATION_PLUGIN_HIDE_HISTORY.flow(),
                appPreferences.TRANSLATION_PLUGIN_HIDE_CATALOG.flow(),
                appPreferences.TRANSLATION_PLUGIN_HIDE_SEARCH.flow(),
            ) { _, _, _, _ -> }
        ) { _, _, _ ->
            val sourceId = resolveSourceId(bookUrl)
            val pair = translationPairForBook(bookUrl, sourceId)
            TranslationSettings(
                enabled = translationEnabledForBook(bookUrl, sourceId),
                source = pair.source,
                target = pair.target,
                scope = translationScopeForBook(bookUrl, sourceId),
                provider = translationProviderForBook(bookUrl, sourceId),
            )
        }.distinctUntilChanged()

    /**
     * Сигнал: эмитит [Unit] при изменении любого из 13 преф-флоёв.
     * Используется для триггера пересчёта в тех ViewModel, где нет привязки
     * к конкретной книге (например, библиотека).
     */
    override fun translationSettingsChangeSignal(): Flow<Unit> =
        combine(
            combine(
                appPreferences.TRANSLATION_BOOK_ENABLED_MAP.flow(),
                appPreferences.TRANSLATION_BOOK_LANG_PAIR.flow(),
                appPreferences.GLOBAL_TRANSLATION_ENABLED.flow(),
                appPreferences.GLOBAL_TRANSLATION_PREFERRED_TARGET.flow(),
                appPreferences.TRANSLATION_GLOBAL_MODE.flow(),
            ) { _, _, _, _, _ -> },
            combine(
                appPreferences.TRANSLATION_PLUGIN_ENABLED_MAP.flow(),
                appPreferences.TRANSLATION_PLUGIN_LANG_PAIR.flow(),
                appPreferences.TRANSLATION_PLUGIN_SCOPE.flow(),
                appPreferences.TRANSLATION_PLUGIN_PROVIDER.flow(),
            ) { _, _, _, _ -> },
            combine(
                appPreferences.TRANSLATION_PLUGIN_HIDE_LIBRARY.flow(),
                appPreferences.TRANSLATION_PLUGIN_HIDE_HISTORY.flow(),
                appPreferences.TRANSLATION_PLUGIN_HIDE_CATALOG.flow(),
                appPreferences.TRANSLATION_PLUGIN_HIDE_SEARCH.flow(),
            ) { _, _, _, _ -> }
        ) { _, _, _ -> }
}
