package my.noveldokusha.features.reader.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import my.noveldokusha.coreui.theme.AppTheme
import my.noveldokusha.coreui.theme.DarkMode
import my.noveldokusha.features.reader.ui.settingDialogs.MoreSettingDialog
import my.noveldokusha.features.reader.ui.settingDialogs.StyleSettingDialog
import my.noveldokusha.features.reader.ui.settingDialogs.TranslatorSettingDialog
import my.noveldokusha.features.reader.ui.settingDialogs.VoiceReaderSettingDialog
import my.noveldokusha.settings.RegexCleanupSettingsScreen
import my.noveldokusha.settings.RegexCleanupSettingsViewModel

@Composable
internal fun ReaderScreenBottomBarDialogs(
    settings: ReaderScreenState.Settings,
    regexCleanupViewModel: RegexCleanupSettingsViewModel?,
    onTextFontChanged: (String) -> Unit,
    onTextColorChanged: (String) -> Unit,
    onBackgroundChanged: (String) -> Unit,
    onTextSizeChanged: (Float) -> Unit,
    onLineHeightChanged: (Float) -> Unit,
    onParagraphSpacingChanged: (Float) -> Unit,
    onLetterSpacingChanged: (Float) -> Unit,
    onTextJustifyChange: (Boolean) -> Unit,
    onTextHyphenationChange: (Boolean) -> Unit,
    onParagraphIndentChange: (Boolean) -> Unit,
    onParagraphFirstLetterBoldChange: (Boolean) -> Unit,
    onSentenceSplittingChange: (Boolean) -> Unit,
    onTextBoldChange: (Boolean) -> Unit,
    onTextItalicChange: (Boolean) -> Unit,
    onTextUnderlineChange: (Boolean) -> Unit,
    onTextShadowChange: (Boolean) -> Unit,
    onTextSmoothChange: (Boolean) -> Unit,
    onMarginLeftChange: (Float) -> Unit,
    onMarginRightChange: (Float) -> Unit,
    onMarginTopChange: (Float) -> Unit,
    onMarginBottomChange: (Float) -> Unit,
    onSelectableTextChange: (Boolean) -> Unit,
    onDarkModeSelected: (DarkMode) -> Unit,
    onAppThemeSelected: (AppTheme) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onFullScreen: (Boolean) -> Unit,
    onSingleTapToOpenSettingsChange: (Boolean) -> Unit,
    onTtsHighlightEnabledChange: (Boolean) -> Unit,
    onTtsHighlightColorChange: (String) -> Unit,
    onManualHighlightEnabledChange: (Boolean) -> Unit = {},
    onTextDefaultsReset: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        val maxDialogHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).roundToInt().dp
        Box(
            Modifier
                .padding(horizontal = 24.dp)
                .heightIn(max = maxDialogHeight)
        ) {
            AnimatedContent(targetState = settings.selectedSetting.value, label = "") { target ->
                when (target) {
                    ReaderScreenState.Settings.Type.LiveTranslation -> TranslatorSettingDialog(
                        state = settings.liveTranslation
                    )
                    ReaderScreenState.Settings.Type.TextToSpeech -> VoiceReaderSettingDialog(
                        state = settings.textToSpeech,
                        floatingTtsState = settings.floatingTts,
                        parallelEnabled = settings.liveTranslation.parallelEnabled,
                    )
                    ReaderScreenState.Settings.Type.Style -> {
                        StyleSettingDialog(
                            state = settings.style,
                            onDarkModeChange = onDarkModeSelected,
                            onAppThemeChange = onAppThemeSelected,
                            onTextFontChange = onTextFontChanged,
                            onTextColorChanged = onTextColorChanged,
                            onBackgroundChanged = onBackgroundChanged,
                            onTextSizeChange = onTextSizeChanged,
                            onLineHeightChange = onLineHeightChanged,
                            onParagraphSpacingChange = onParagraphSpacingChanged,
                            onLetterSpacingChange = onLetterSpacingChanged,
                            onTextJustifyChange = onTextJustifyChange,
                            onTextHyphenationChange = onTextHyphenationChange,
                            onParagraphIndentChange = onParagraphIndentChange,
                            onParagraphFirstLetterBoldChange = onParagraphFirstLetterBoldChange,
                            onSentenceSplittingChange = onSentenceSplittingChange,
                            onTextBoldChange = onTextBoldChange,
                            onTextItalicChange = onTextItalicChange,
                            onTextUnderlineChange = onTextUnderlineChange,
                            onTextShadowChange = onTextShadowChange,
                            onTextSmoothChange = onTextSmoothChange,
                            onMarginLeftChange = onMarginLeftChange,
                            onMarginRightChange = onMarginRightChange,
                            onMarginTopChange = onMarginTopChange,
                            onMarginBottomChange = onMarginBottomChange,
                            onTextDefaultsReset = onTextDefaultsReset,
                        )
                    }
                    ReaderScreenState.Settings.Type.More -> MoreSettingDialog(
                        ttsHighlightEnabled = settings.ttsHighlight.isEnabled.value,
                        onTtsHighlightEnabledChange = onTtsHighlightEnabledChange,
                        ttsHighlightColor = settings.ttsHighlight.highlightColor.value,
                        onTtsHighlightColorChange = onTtsHighlightColorChange,
                        manualHighlightEnabled = settings.manualHighlightEnabled.value,
                        onManualHighlightEnabledChange = onManualHighlightEnabledChange,
                    )
                    ReaderScreenState.Settings.Type.None -> Unit
                    ReaderScreenState.Settings.Type.RegexRules -> {
                        val viewModel = regexCleanupViewModel
                        if (viewModel != null) {
                            val panelHeight =
                                (LocalConfiguration.current.screenHeightDp * 0.6f).roundToInt().dp
                            RegexCleanupSettingsScreen(
                                viewModel = viewModel,
                                onNavigateBack = {
                                    settings.selectedSetting.value =
                                        ReaderScreenState.Settings.Type.None
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(panelHeight),
                                applyStatusBarPadding = false,
                                compactHeader = true,
                            )
                        }
                    }
                }
            }
        }
    }
}