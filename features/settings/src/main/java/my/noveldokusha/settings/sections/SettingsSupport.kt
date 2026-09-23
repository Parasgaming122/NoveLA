package my.noveldokusha.settings.sections

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import my.noveldokusha.coreui.components.SlimListItem
import my.noveldokusha.coreui.theme.colorAccent
import my.noveldokusha.coreui.theme.textPadding
import my.noveldokusha.settings.R

@Composable
internal fun SettingsSupport(
    onExportLogs: () -> Unit,
) {
    Text(
        text = stringResource(R.string.support),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.textPadding(),
        color = colorAccent()
    )
    SlimListItem(
        headlineContent = {
            Text(text = stringResource(R.string.export_logs))
        },
        supportingContent = {
            Text(text = stringResource(R.string.export_logs_description))
        },
        leadingContent = {
            Icon(Icons.Outlined.BugReport, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        modifier = Modifier.clickable { onExportLogs() }
    )
}
