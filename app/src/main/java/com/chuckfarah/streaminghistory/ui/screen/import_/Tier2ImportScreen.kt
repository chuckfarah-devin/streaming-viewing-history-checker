package com.chuckfarah.streaminghistory.ui.screen.import_

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Import screen for Netflix Tier 2 (full data export) — TS §3.2.
 *
 * After a successful import with multiple profiles, [onProfileSelectionNeeded]
 * is called so the navigation layer can route to the profile picker (TS §7.2).
 * For a single profile the import is complete and the user returns to Home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Tier2ImportScreen(
    onBack: () -> Unit,
    onProfileSelectionNeeded: (profiles: List<String>) -> Unit,
    viewModel: Tier2ImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.importFile(it) }
    }

    // When a successful import reveals multiple profiles, navigate to the picker.
    LaunchedEffect(state) {
        val s = state
        if (s is Tier2ImportUiState.Success && s.profiles.size > 1) {
            onProfileSelectionNeeded(s.profiles)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Full Netflix Export") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text  = "Select your Netflix data export",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text  = "Download from: Netflix → Account → Privacy → " +
                        "Download your personal information\n\n" +
                        "Select the ZIP file or the ViewingActivity.csv " +
                        "inside CONTENT_INTERACTION/.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (val s = state) {
                is Tier2ImportUiState.Idle -> {
                    Button(onClick = { filePicker.launch("*/*") }) {
                        Text("Choose ZIP or CSV file")
                    }
                }

                is Tier2ImportUiState.Loading -> {
                    CircularProgressIndicator()
                    Text("Importing — this may take a moment…")
                }

                is Tier2ImportUiState.Success -> {
                    Text(
                        text  = "Import complete",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text("${s.recordsUpgraded} records upgraded from Tier 1")
                    Text("${s.recordsInserted} new records added")
                    if (s.rowsSkipped > 0) {
                        Text(
                            text  = "${s.rowsSkipped} rows skipped",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (s.profiles.size == 1) {
                        Text(
                            text  = "Profile: ${s.profiles.first()}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    // Multiple-profile case is handled by LaunchedEffect → navigation
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onBack) { Text("Done") }
                }

                is Tier2ImportUiState.AlreadyImported -> {
                    Text(
                        text  = "This file has already been imported.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text  = "To import newer data, download a fresh export from Netflix.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { viewModel.reset() }) { Text("Try another file") }
                    OutlinedButton(onClick = onBack) { Text("Done") }
                }

                is Tier2ImportUiState.Failure -> {
                    Text(
                        text  = "Import failed",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text  = s.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = { viewModel.reset() }) { Text("Try again") }
                }
            }
        }
    }
}
