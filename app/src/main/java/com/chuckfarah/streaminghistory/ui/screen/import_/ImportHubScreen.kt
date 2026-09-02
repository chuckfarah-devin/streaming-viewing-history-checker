package com.chuckfarah.streaminghistory.ui.screen.import_

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.chuckfarah.streaminghistory.ui.theme.StreamingHistoryTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportHubScreen(
    onBack: () -> Unit,
    onNavigateToTier1: () -> Unit,
    onNavigateToTier2: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Netflix history") },
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
        ImportHubContent(
            onNavigateToTier1 = onNavigateToTier1,
            onNavigateToTier2 = onNavigateToTier2,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
        )
    }
}

@Composable
fun ImportHubContent(
    onNavigateToTier1: () -> Unit,
    onNavigateToTier2: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        Text(
            text = "Choose the Netflix file to import",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )

        Text(
            text = "Both files come from your Netflix account export. Pick the one that matches what you downloaded.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ImportOptionCard(
            icon = { Icon(Icons.Filled.InsertDriveFile, contentDescription = null) },
            title = "Quick viewing history",
            tierLabel = "Tier 1",
            description = "The simple Netflix Viewing Activity download — title and date only. " +
                    "Download NetflixViewingHistory.csv from your Netflix account under " +
                    "Account → Viewing Activity → Download All.",
            actionLabel = "Choose NetflixViewingHistory.csv",
            onAction = onNavigateToTier1,
            cardContentDescription = "Quick viewing history, Tier 1, choose NetflixViewingHistory.csv",
        )

        ImportOptionCard(
            icon = { Icon(Icons.Default.People, contentDescription = null) },
            title = "Detailed viewing activity",
            tierLabel = "Tier 2",
            description = "The full Netflix personal-data export, which may include session duration, " +
                    "stopping position, and family-profile information. " +
                    "Not every record may reconcile to a Tier 1 entry. " +
                    "Download ViewingActivity.csv from Account → Privacy → Download your personal information.",
            actionLabel = "Choose ViewingActivity.csv",
            onAction = onNavigateToTier2,
            cardContentDescription = "Detailed viewing activity, Tier 2, choose ViewingActivity.csv",
        )

        Spacer(Modifier.weight(1f))

        TextButton(
            onClick = { /* no-op */ },
            enabled = false,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(
                text = "ZIP import is not supported in this version",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ImportOptionCard(
    icon: @Composable () -> Unit,
    title: String,
    tierLabel: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
    cardContentDescription: String,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = cardContentDescription
            },
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
                icon()
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = tierLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.FileOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(actionLabel)
            }
        }
    }
}


@PreviewLightDark
@Composable
fun ImportHubContentPreview() {
    StreamingHistoryTheme {
        ImportHubContent(
            onNavigateToTier1 = {},
            onNavigateToTier2 = {},
            modifier = Modifier,
        )
    }
}
