package my.noveldokusha.coreui.components.editor

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor

@Composable
fun CodeEditorField(
    text: String,
    onTextChange: (String) -> Unit,
    fontSize: Int,
    showLineNumbers: Boolean,
    wordWrap: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSyntaxColors.current
    val language = remember { LuaEditorLanguage() }
    val scheme = remember(colors) { colors.toEditorColorScheme() }

    AndroidView(
        factory = { ctx ->
            val editor = CodeEditor(ctx)
            editor.setEditorLanguage(language)
            editor.colorScheme = scheme
            editor.isLineNumberEnabled = showLineNumbers
            editor.isWordwrap = wordWrap
            editor.setTextSize(fontSize.toFloat())
            editor.typefaceText = Typeface.MONOSPACE
            editor.setCursorAnimationEnabled(false)

            editor.setText(text)
            editor.subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
                if (event.action != ContentChangeEvent.ACTION_SET_NEW_TEXT) {
                    onTextChange(editor.text.toString())
                }
            }
            editor
        },
        update = { editor ->
            editor.isLineNumberEnabled = showLineNumbers
            editor.isWordwrap = wordWrap
            editor.setTextSize(fontSize.toFloat())
            editor.colorScheme = scheme
            if (editor.text.toString() != text) {
                editor.setText(text)
            }
        },
        modifier = modifier,
    )
}
