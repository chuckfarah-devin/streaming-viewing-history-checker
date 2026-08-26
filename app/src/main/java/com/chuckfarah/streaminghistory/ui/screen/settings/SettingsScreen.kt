@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.chuckfarah.streaminghistory.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chuckfarah.streaminghistory.ui.theme.StreamingHistoryTheme

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onImportHub: () -> Unit,
    onDeletedToHome: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val activeProfile by viewModel.activeProfile.collectAsState()
    val availableProfiles by viewModel.availableProfiles.collectAsState()
    val deleteState by viewModel.deleteState.collectAsState()

    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(deleteState) {
        if (deleteState is DeleteHistoryState.Deleted) {
            onDeletedToHome()
        }
    }

    DeleteConfirmationDialog(
        state = deleteState,
        onConfirm = viewModel::confirmDelete,
        onDismiss = viewModel::dismissDelete,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics {
                            contentDescription = "Back"
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { padding ->
        SettingsContent(
            activeProfile = activeProfile,
            availableProfiles = availableProfiles,
            onImportHub = onImportHub,
            onDeleteHistory = viewModel::requestDelete,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
        )
    }
}

@Composable
fun SettingsContent(
    activeProfile: String?,
    availableProfiles: List<String>,
    onImportHub: () -> Unit,
    onDeleteHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val validActiveProfile = activeProfile?.takeIf { it in availableProfiles }
    val hasProfiles = availableProfiles.isNotEmpty()

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )

        if (hasProfiles) {
            ListItem(
                headlineContent = { Text("Active profile") },
                supportingContent = { Text(validActiveProfile ?: "Choose profile") },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                    )
                },
            )
            HorizontalDivider()
        }

        ListItem(
            headlineContent = { Text("Import or manage Netflix history") },
            supportingContent = { Text("Add or replace the CSV files from your Netflix account") },
            leadingContent = {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                )
            },
            modifier = Modifier.clickable(onClick = onImportHub),
        )
        HorizontalDivider()

        ListItem(
            headlineContent = {
                Text(
                    text = "Delete all imported history",
                    color = MaterialTheme.colorScheme.error,
                )
            },
            supportingContent = { Text("Remove every imported record from this device") },
            leadingContent = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            modifier = Modifier.clickable(onClick = onDeleteHistory),
        )
        HorizontalDivider()

        Spacer(Modifier.height(8.dp))

        AboutCard()
    }
}

@Composable
private fun AboutCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Text(
                text = "Results use only imported Netflix history stored locally on this device.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Camera recognition uses on-device processing only.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "This is an unofficial, independent app and is not affiliated with Netflix.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(
    state: DeleteHistoryState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val show = state is DeleteHistoryState.Confirming || state is DeleteHistoryState.Deleting

    if (show) {
        AlertDialog(
            onDismissRequest = { if (state !is DeleteHistoryState.Deleting) onDismiss() },
            title = { Text("Delete imported history?") },
            text = {
                Column {
                    Text(
                        text = "This removes all imported Netflix history from this device. " +
                                "Your original Netflix files are not affected.",
                    )
                    if (state is DeleteHistoryState.Deleting) {
                        Spacer(Modifier.height(16.dp))
                        CircularProgressIndicator()
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirm,
                    enabled = state !is DeleteHistoryState.Deleting,
                ) {
                    Text("Delete imported history")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    enabled = state !is DeleteHistoryState.Deleting,
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@PreviewLightDark
@Composable
fun SettingsContentPreview() {
    StreamingHistoryTheme {
        SettingsContent(
            activeProfile = "Alex",
            availableProfiles = listOf("Alex", "Jordan"),
            onImportHub = {},
            onDeleteHistory = {},
        )
    }
}

@Preview
@Composable
fun SettingsDeleteDialogPreview() {
    StreamingHistoryTheme {
        DeleteConfirmationDialog(
            state = DeleteHistoryState.Confirming,
            onConfirm = {},
            onDismiss = {},
        )
    }
}
