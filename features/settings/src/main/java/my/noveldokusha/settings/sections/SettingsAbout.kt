package my.noveldokusha.settings.sections

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import my.noveldokusha.coreui.components.SlimListItem
import my.noveldokusha.coreui.theme.colorAccent
import my.noveldokusha.coreui.theme.textPadding
import my.noveldokusha.settings.R

private const val REPO_URL = "https://github.com/HnDK0/NoveLA"
private const val ISSUES_URL = "https://github.com/HnDK0/NoveLA/issues"

@Composable
internal fun SettingsAbout(
    appVersion: String,
) {
    val context = LocalContext.current

    Text(
        text = stringResource(R.string.about),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.textPadding(),
        color = colorAccent()
    )

    SlimListItem(
        headlineContent = {
            Text(text = stringResource(R.string.version_format, appVersion))
        },
        leadingContent = {
            Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    )

    SlimListItem(
        headlineContent = {
            Text(text = stringResource(R.string.github_repository))
        },
        leadingContent = {
            Icon(Icons.Outlined.Code, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        modifier = Modifier.clickable {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)))
        }
    )

    SlimListItem(
        headlineContent = {
            Text(text = stringResource(R.string.report_issue))
        },
        supportingContent = {
            Text(text = stringResource(R.string.report_issue_description))
        },
        leadingContent = {
            Icon(Icons.Outlined.NewReleases, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        modifier = Modifier.clickable {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ISSUES_URL)))
        }
    )
}
