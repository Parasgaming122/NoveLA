package my.noveldokusha.settings.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.util.Date

/**
 * Compose settings screen for the Supabase cloud sync engine.
 *
 * === Layout (top to bottom) ===
 *  1. Section header ("Cloud Sync") with a brief explanation
 *  2. Master switch: Enable cloud sync
 *  3. Three OutlinedTextFields: Supabase URL, anon key, installation id
 *  4. Save + prominent Sync-now buttons
 *  5. Periodic sync interval radio group (15 / 30 / 60 / 120 min)
 *  6. What to sync: 2 toggles (downloaded chapters + reading history)
 *  7. Diagnostics: last push / pull / pending count
 *
 * The "Sync now" button is the answer to the user's question:
 * "do I have a button to push data before the time?" — yes, it's
 * prominent and right below the Save button. Tapping it:
 *   * persists any unsaved text-field edits,
 *   * invalidates the cached Supabase client,
 *   * enqueues a one-time sync worker immediately (no waiting for the
 *     next periodic tick).
 */
@Composable
fun SettingsSync(
    viewModel: SyncSettingsViewModel = viewModel(),
) {
    val url by viewModel.urlField.collectAsState()
    val anonKey by viewModel.anonKeyField.collectAsState()
    val userId by viewModel.userIdField.collectAsState()
    val syncEnabled by viewModel.syncEnabled.collectAsState()
    val syncIntervalMin by viewModel.syncIntervalMinutes.collectAsState()
    val includeChapters by viewModel.includeDownloadedChapters.collectAsState()
    val includeHistory by viewModel.includeReadingHistory.collectAsState()
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
        ToggleRow(
            title = "Enable cloud sync",
            subtitle = "When off, no network calls are made and the " +
                "periodic sync worker is cancelled.",
            checked = syncEnabled,
            onCheckedChange = viewModel::onSyncEnabledChange,
        )

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
        // Installation ID field
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
        // Save + prominent Sync-now buttons
        // -----------------------------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = viewModel::onSaveCredentials,
                modifier = Modifier.weight(1f),
            ) { Text("Save") }
            // The "Sync now" button is the PRIMARY action — filled button,
            // takes more weight, has the sync icon. Tapping it pushes
            // everything immediately without waiting for the next periodic
            // tick. Great for "I'm about to switch phones" scenarios.
            Button(
                onClick = viewModel::onSyncNow,
                modifier = Modifier.weight(1.4f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(Icons.Outlined.Sync, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Sync now")
            }
        }

        HorizontalDivider()

        // -----------------------------------------------------------------
        // Periodic sync interval (radio group)
        // -----------------------------------------------------------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Periodic sync interval",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = "How often the background worker pulls and pushes " +
                "data when the app is closed. WorkManager enforces a " +
                "minimum of 15 minutes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // selectableGroup is required for RadioButton semantics — it
        // ensures only one is selected at a time, and TalkBack announces
        // "selected" correctly.
        Column(modifier = Modifier.selectableGroup()) {
            viewModel.allowedIntervals.forEach { minutes ->
                val label = when (minutes) {
                    60 -> "1 hour"
                    120 -> "2 hours"
                    else -> "$minutes minutes"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = (minutes == syncIntervalMin),
                        onClick = { viewModel.onSyncIntervalChange(minutes) },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }

        HorizontalDivider()

        // -----------------------------------------------------------------
        // What to sync — toggles
        // -----------------------------------------------------------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "What to sync",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = "Choose what data gets mirrored to Supabase. " +
                "Library + reading progress always sync (core feature).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ToggleRow(
            title = "Sync downloaded chapter bodies",
            subtitle = "Mirror the actual text content of chapters you've " +
                "downloaded. Can be MBs of data per phone — leave OFF " +
                "if you don't read offline on the second phone.",
            checked = includeChapters,
            onCheckedChange = viewModel::onIncludeDownloadedChaptersChange,
        )
        ToggleRow(
            title = "Sync reading history (History tab)",
            subtitle = "Mirror the 'History' tab entries across phones. " +
                "Rows are tiny — keep ON for a consistent History view.",
            checked = includeHistory,
            onCheckedChange = viewModel::onIncludeReadingHistoryChange,
        )

        HorizontalDivider()

        // -----------------------------------------------------------------
        // Diagnostics
        // -----------------------------------------------------------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Diagnostics",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

/**
 * Reusable row: a switch + title + subtitle. Mirrors the layout
 * of the existing toggle rows in NoveLA's other settings sections.
 */
@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Formats an epoch-millis value to a human-readable date string. */
private fun formatEpoch(epochMs: Long): String {
    if (epochMs <= 0L) return "never"
    return DateFormat.getDateTimeInstance().format(Date(epochMs))
}
