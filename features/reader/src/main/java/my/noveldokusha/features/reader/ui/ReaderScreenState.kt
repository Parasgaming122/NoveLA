package my.noveldokusha.features.reader.ui
import androidx.compose.runtime.Immutable

import androidx.compose.runtime.MutableState

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import my.noveldokusha.coreui.theme.AppTheme
import my.noveldokusha.coreui.theme.DarkMode
import my.noveldokusha.features.reader.features.LiveTranslationSettingData
import my.noveldokusha.features.reader.features.ManualHighlightSettingData
import my.noveldokusha.features.reader.features.TextToSpeechSettingData

@Stable
internal data class ReaderScreenState(
    val showReaderInfo: MutableState<Boolean>,
    val readerInfo: CurrentInfo,
    val settings: Settings,
    val showInvalidChapterDialog: MutableState<Boolean>
) {
    @Stable
    data class CurrentInfo(
        val chapterTitle: State<String>,
        val chapterCurrentNumber: State<Int>,
        val chapterPercentageProgress: State<Float>,
        val chaptersCount: State<Int>,
        val chapterUrl: State<String>
    )

    @Stable
    data class Settings(
        val isTextSelectable: State<Boolean>,
        val keepScreenOn: State<Boolean>,
        val fullScreen: State<Boolean>,
        val isSingleTapToOpenSettings: State<Boolean>,
        val textToSpeech: TextToSpeechSettingData,
        val liveTranslation: LiveTranslationSettingData,
        val style: StyleSettingsData,
        val selectedSetting: MutableState<Type>,
        val floatingTts: FloatingTtsSettingsData,
        val ttsHighlight: TtsHighlightSettingsData,
        val manualHighlight: ManualHighlightSettingData,
        val manualHighlightEnabled: State<Boolean>,
    ) {
        @Stable
        data class StyleSettingsData(
            val currentDarkMode: State<DarkMode>,
            val currentAppTheme: State<AppTheme>,
            // Пользовательский цвет текста читалки; пустая строка = «Авто» (цвет темы)
            val textColor: State<String>,
            // Пользовательский фон читалки; пустая строка = «Авто» (цвет темы)
            val readerBackground: State<String>,
            val textFont: State<String>,
            val textSize: State<Float>,
            val lineHeight: State<Float>,
            val paragraphSpacing: State<Float>,
            val letterSpacing: State<Float>,
            val textJustify: State<Boolean>,
            val textHyphenation: State<Boolean>,
            val textBold: State<Boolean>,
            val textItalic: State<Boolean>,
            val textUnderline: State<Boolean>,
            val textShadow: State<Boolean>,
            val textSmooth: State<Boolean>,
            val marginLeft: State<Float>,
            val marginRight: State<Float>,
            val marginTop: State<Float>,
            val marginBottom: State<Float>,
            val paragraphIndent: State<Boolean> = mutableStateOf(false),
            val paragraphFirstLetterBold: State<Boolean> = mutableStateOf(false),
            val sentenceSplitting: State<Boolean> = mutableStateOf(false),
        )

        @Stable
        data class FloatingTtsSettingsData(
            val isEnabled: MutableState<Boolean>,
            val showOutsideApp: MutableState<Boolean>,
            val opacity: MutableState<Float>,
        )

        @Stable
        data class TtsHighlightSettingsData(
            val isEnabled: MutableState<Boolean>,
            val highlightColor: MutableState<String>,
        )

        @Immutable
        enum class Type {
            None, LiveTranslation, TextToSpeech, Style, More, RegexRules
        }
    }
}