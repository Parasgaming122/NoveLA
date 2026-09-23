package my.noveldokusha.features.reader.ui

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import my.noveldokusha.reader.R

@Composable
fun PluginErrorDialog(
    title: String,
    message: String?,
    authUrl: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (message != null) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Text(text = message, modifier = Modifier.padding(16.dp))
                }
            }
        },
        confirmButton = {
            if (authUrl != null) {
                TextButton(onClick = {
                    val intent = Intent().apply {
                        setClassName(
                            context.packageName,
                            "my.noveldokusha.webview.WebViewActivity"
                        )
                        putExtra("url", authUrl)
                        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    context.startActivity(intent)
                }) {
                    Text(stringResource(R.string.plugin_error_open))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}
