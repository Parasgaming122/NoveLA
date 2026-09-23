package my.noveldokusha.extensions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import my.noveldokusha.coreui.components.editor.CodeEditorField

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LuaEditorDialog(
    title: String,
    code: String,
    error: String?,
    onCodeChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val normalizedCode = remember(code) { code.replace("\r\n", "\n") }

    var wordWrap by remember { mutableStateOf(false) }
    var editorFontSize by remember { mutableStateOf(11) }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                textAlign = TextAlign.Center
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val compactBtn = Modifier.height(24.dp)
                val compactPadding = PaddingValues(horizontal = 4.dp)
                FilledTonalButton(
                    onClick = { clipboardManager.setText(AnnotatedString(normalizedCode)) },
                    modifier = compactBtn,
                    contentPadding = compactPadding,
                ) {
                    Text(stringResource(my.noveldokusha.strings.R.string.copy), fontSize = 9.sp)
                }
                FilledTonalButton(
                    onClick = { clipboardManager.getText()?.text?.let(onCodeChange) },
                    modifier = compactBtn,
                    contentPadding = compactPadding,
                ) {
                    Text(stringResource(my.noveldokusha.strings.R.string.paste), fontSize = 9.sp)
                }
                FilledTonalButton(
                    onClick = { onCodeChange("") },
                    modifier = compactBtn,
                    contentPadding = compactPadding,
                ) {
                    Text(stringResource(my.noveldokusha.strings.R.string.clear), fontSize = 9.sp)
                }
                FilledTonalButton(
                    onClick = { wordWrap = !wordWrap },
                    modifier = compactBtn,
                    contentPadding = compactPadding,
                ) {
                    Text(if (wordWrap) "No Wrap" else "Wrap", fontSize = 9.sp)
                }
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                        )
                        .clickable { editorFontSize = when (editorFontSize) {
                            6 -> 7; 7 -> 8; 8 -> 9; 9 -> 10; 10 -> 11; else -> 6
                        } },
                    contentAlignment = Alignment.Center
                ) {
                    Text("${editorFontSize}sp", fontSize = 8.sp, color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.padding(horizontal = 4.dp))
                }
                FilledTonalButton(
                    onClick = onDismiss,
                    modifier = compactBtn,
                    contentPadding = compactPadding,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text(stringResource(my.noveldokusha.strings.R.string.cancel), fontSize = 9.sp)
                }
                FilledTonalButton(
                    onClick = onSave,
                    modifier = compactBtn,
                    contentPadding = compactPadding,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text(stringResource(my.noveldokusha.strings.R.string.save), fontSize = 9.sp)
                }
            }

            if (!error.isNullOrBlank()) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(
                        color = Color(0xFF1E1E1E),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(4.dp)
            ) {
                CodeEditorField(
                    text = normalizedCode,
                    onTextChange = onCodeChange,
                    fontSize = editorFontSize,
                    showLineNumbers = true,
                    wordWrap = wordWrap,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
