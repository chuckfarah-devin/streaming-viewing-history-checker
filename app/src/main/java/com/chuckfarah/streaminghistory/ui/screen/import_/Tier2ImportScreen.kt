package com.chuckfarah.streaminghistory.ui.screen.import_

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.chuckfarah.streaminghistory.ui.theme.LocalExtendedColorScheme
import com.chuckfarah.streaminghistory.ui.theme.StreamingHistoryTheme

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import detailed viewing activity") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = "Back" },
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
        Tier2ImportContent(
            state = state,
            onChooseFile = { filePicker.launch("*/*") },
            onReset = viewModel::reset,
            onBack = onBack,
            onProfileSelectionNeeded = onProfileSelectionNeeded,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
        )
    }
}

@Composable
fun Tier2ImportContent(
    state: Tier2ImportUiState,
    onChooseFile: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
    onProfileSelectionNeeded: (profiles: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))

        Text(
            text = "Detailed viewing activity",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )

        Text(
            text = "Choose the ViewingActivity.csv file. This adds family profiles, duration, stopping position, and episode details.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = "Download it from Netflix: Account → Privacy → Download your personal information.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        when (val s = state) {
            is Tier2ImportUiState.Idle -> {
                Button(
                    onClick = onChooseFile,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Choose ViewingActivity.csv")
                }
            }

            is Tier2ImportUiState.Loading -> {
                CircularProgressIndicator()
                Text(
                    text = "Importing your detailed activity…",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            is Tier2ImportUiState.Success -> {
                StatusIcon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Import complete",
                    tint = LocalExtendedColorScheme.current.success,
                )
                Text(
                    text = "Import complete",
                    style = MaterialTheme.typography.titleLarge,
                    color = LocalExtendedColorScheme.current.success,
                )
                Text(
                    text = "${s.recordsUpgraded} records matched and upgraded",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${s.recordsInserted} new records added",
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (s.rowsSkipped > 0) {
                    StatusRow(
                        imageVector = Icons.Default.Warning,
                        text = "${s.rowsSkipped} rows were skipped",
                        tint = LocalExtendedColorScheme.current.warning,
                    )
                }

                Spacer(Modifier.height(8.dp))

                if (s.profiles.size > 1) {
                    Text(
                        text = "Multiple Netflix profiles were found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { onProfileSelectionNeeded(s.profiles) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("Choose profile")
                    }
                } else if (s.profiles.size == 1) {
                    Text(
                        text = "Profile: ${s.profiles.first()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (s.recordsUpgraded > 0) {
                        Text(
                            text = "If you add more profiles later, you can switch between them in Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Done")
                    }
                }

                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onReset(); onChooseFile() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Import another file")
                }
            }

            is Tier2ImportUiState.AlreadyImported -> {
                StatusIcon(
                    imageVector = Icons.Filled.Help,
                    contentDescription = "Already imported",
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "This file has already been imported",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "To import newer data, download a fresh export from Netflix.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { onReset(); onChooseFile() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Try another file")
                }
                TextButton(onClick = onBack) { Text("Done") }
            }

            is Tier2ImportUiState.Failure -> {
                StatusIcon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Import failed",
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = "Import failed",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = s.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Try again")
                }
            }
        }

        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun StatusIcon(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: androidx.compose.ui.graphics.Color,
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier.size(64.dp),
    )
}

@Composable
private fun StatusRow(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: androidx.compose.ui.graphics.Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@PreviewLightDark
@Composable
fun Tier2ImportContentSuccessPreview() {
    StreamingHistoryTheme {
        Tier2ImportContent(
            state = Tier2ImportUiState.Success(
                recordsUpgraded = 120,
                recordsInserted = 15,
                rowsSkipped = 0,
                profiles = listOf("Alex", "Jordan"),
            ),
            onChooseFile = {},
            onReset = {},
            onBack = {},
            onProfileSelectionNeeded = {},
        )
    }
}
