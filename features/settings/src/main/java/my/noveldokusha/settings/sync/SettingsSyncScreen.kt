package my.noveldokusha.settings.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.util.Date

/**
 * Compose settings screen for the Supabase cloud sync engine.
 *
 * === Layout ===
 *  Section header ("Cloud Sync")
 *  → master enable Switch
 *  → three OutlinedTextFields: Supabase URL, anon key, installation id
 *  → Save + Sync now buttons
 *  → diagnostics: last push/pull time, pending upload count
 *
 * === Integration ===
 *  This composable is intended to be dropped into the existing
 *  SettingsScreenBody.kt between two HorizontalDivider()s, e.g.
 *  right after SettingsBackup and before SettingsNetwork. See
 *  IntegrationNotes.md for the exact call-site patch.
 *
 * === ViewModel ===
 *  [SyncSettingsViewModel] is @HiltViewModel — `viewModel()` from
 *  androidx.lifecycle.viewmodel.compose.viewModel resolves it via
 *  Hilt using the Activity's @AndroidEntryPoint (the Hilt Gradle
 *  plugin installs HiltViewModelFactory as the default factory).
 */
@Composable
fun SettingsSync(
    viewModel: SyncSettingsViewModel = viewModel(),
) {
    val url by viewModel.urlField.collectAsState()
    val anonKey by viewModel.anonKeyField.collectAsState()
    val userId by viewModel.userIdField.collectAsState()
    val syncEnabled by viewModel.syncEnabled.collectAsState()
    val lastPush by viewModel.lastPushEpochMs.collectAsState()
    val lastPull by viewModel.lastPullEpochMs.collectAsState()
    val pending by viewModel.pendingUploadCount.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // -----------------------------------------------------------------
        // Section header
        // -----------------------------------------------------------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.CloudSync,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Cloud Sync",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = "Personal Supabase instance — syncs your library " +
                "and reading progress across your phones. Zero-budget: " +
                "uses your Supabase project's anon key (RLS-protected).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider()

        // -----------------------------------------------------------------
        // Master switch
        // -----------------------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Enable cloud sync", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "When off, no network calls are made.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = syncEnabled, onCheckedChange = viewModel::onSyncEnabledChange)
        }

        HorizontalDivider()

        // -----------------------------------------------------------------
        // Supabase URL field
        // -----------------------------------------------------------------
        OutlinedTextField(
            value = url,
            onValueChange = viewModel::onUrlChange,
            label = { Text("Supabase URL") },
            placeholder = { Text("https://xyzcompany.supabase.co") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )

        // -----------------------------------------------------------------
        // Supabase anon key field
        // -----------------------------------------------------------------
        OutlinedTextField(
            value = anonKey,
            onValueChange = viewModel::onAnonKeyChange,
            label = { Text("Supabase anon key") },
            placeholder = { Text("eyJhbGciOi...paste your anon key here") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.VpnKey, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        // -----------------------------------------------------------------
        // Installation ID (user bucket)
        // -----------------------------------------------------------------
        OutlinedTextField(
            value = userId,
            onValueChange = viewModel::onUserIdChange,
            label = { Text("Installation ID (user bucket)") },
            placeholder = { Text("auto-generated UUID — paste the SAME id on both phones") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth(),
        )

        // -----------------------------------------------------------------
        // Save + Sync now buttons
        // -----------------------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = viewModel::onSaveCredentials,
                modifier = Modifier.weight(1f),
            ) { Text("Save") }
            OutlinedButton(
                onClick = viewModel::onSyncNow,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.Sync, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Sync now")
            }
        }

        HorizontalDivider()

        // -----------------------------------------------------------------
        // Diagnostics block
        // -----------------------------------------------------------------
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Diagnostics", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "Last push: ${formatEpoch(lastPush)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Last pull: ${formatEpoch(lastPull)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Pending uploads: $pending",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = viewModel::refreshPendingCount,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Refresh pending count") }
        }
    }
}

/** Formats an epoch-millis value to a human-readable date string. */
private fun formatEpoch(epochMs: Long): String {
    if (epochMs <= 0L) return "never"
    return DateFormat.getDateTimeInstance().format(Date(epochMs))
}
