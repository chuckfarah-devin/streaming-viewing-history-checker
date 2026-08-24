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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onBack: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.importFile(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Netflix History") },
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
                text  = "Select your Netflix Tier 1 CSV",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text  = "Download from: Netflix → Account → Viewing Activity → Download All\n" +
                        "File name: NetflixViewingHistory.csv",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (val s = state) {
                is ImportUiState.Idle -> {
                    Button(onClick = { filePicker.launch("*/*") }) {
                        Text("Choose CSV file")
                    }
                }
                is ImportUiState.Loading -> {
                    CircularProgressIndicator()
                    Text("Importing…")
                }
                is ImportUiState.Success -> {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack, // placeholder
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text  = "Import complete",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text("${s.recordsImported} records imported")
                    if (s.rowsSkipped > 0) {
                        Text(
                            text  = "${s.rowsSkipped} rows skipped (malformed)",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { viewModel.reset(); filePicker.launch("*/*") }) {
                        Text("Import another file")
                    }
                    OutlinedButton(onClick = onBack) { Text("Done") }
                }
                is ImportUiState.AlreadyImported -> {
                    Text(
                        text  = "This file has already been imported.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text  = "To import an updated export, download a new CSV from Netflix and select it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { viewModel.reset() }) { Text("Try another file") }
                    OutlinedButton(onClick = onBack) { Text("Done") }
                }
                is ImportUiState.Failure -> {
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
