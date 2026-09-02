package com.chuckfarah.streaminghistory.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chuckfarah.streaminghistory.ui.theme.StreamingHistoryTheme

@Composable
fun HomeScreen(
    onNavigateToImportHub: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToProfileSelect: () -> Unit,
    onNavigateToCamera: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val totalRecords      by viewModel.totalRecords.collectAsState()
    val activeProfile     by viewModel.activeProfile.collectAsState()
    val availableProfiles by viewModel.availableProfiles.collectAsState()

    LaunchedEffect(Unit) { viewModel.refresh() }

    HomeContent(
        totalRecords      = totalRecords,
        activeProfile     = activeProfile,
        availableProfiles = availableProfiles,
        onImportHub       = onNavigateToImportHub,
        onSearch          = onNavigateToSearch,
        onProfileSelect   = onNavigateToProfileSelect,
        onCamera          = onNavigateToCamera,
        onSettings        = onNavigateToSettings,
    )
}

@Composable
fun HomeContent(
    totalRecords: Int,
    activeProfile: String?,
    availableProfiles: List<String>,
    onImportHub: () -> Unit,
    onSearch: () -> Unit,
    onProfileSelect: () -> Unit,
    onCamera: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A stored active profile that no longer exists in the current data is treated as none.
    val validActiveProfile = activeProfile?.takeIf { it in availableProfiles }
    val hasHistory = totalRecords > 0
    val hasProfiles = availableProfiles.isNotEmpty()

    Scaffold { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 24.dp),
        ) {
            // Title
            item {
                Text(
                    text = "Streaming History",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.semantics { heading() },
                )
            }

            item {
                Text(
                    text = "Check whether a Netflix title has been watched in your imported viewing history.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Active profile
            if (hasProfiles) {
                item {
                    ActiveProfileCard(
                        profile = validActiveProfile,
                        availableProfiles = availableProfiles,
                        onClick = onProfileSelect,
                    )
                }
            }

            // Primary actions
            item { Spacer(Modifier.height(8.dp)) }

            item {
                Button(
                    onClick = onCamera,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Scan TV Screen")
                }
            }

            item {
                OutlinedButton(
                    onClick = onSearch,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Search history")
                }
            }

            item {
                FilledTonalButton(
                    onClick = onImportHub,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Import or manage Netflix history")
                }
            }

            item {
                TextButton(
                    onClick = onSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Settings")
                }
            }

            // Empty state
            if (!hasHistory) {
                item { Spacer(Modifier.height(8.dp)) }
                item {
                    EmptyHomeState()
                }
            }
        }
    }
}

@Composable
private fun ActiveProfileCard(
    profile: String?,
    availableProfiles: List<String>,
    onClick: () -> Unit,
) {
    val displayName = profile ?: "Choose profile"
    val isSelected = profile != null
    val accessibilityLabel = if (isSelected) {
        "Active profile: $displayName. Tap to switch."
    } else {
        "No active profile selected. Tap to choose one."
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = accessibilityLabel
                selected = isSelected
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val initials = if (profile != null && profile.isNotBlank()) {
                profile
                    .split(" ")
                    .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                    .take(2)
                    .joinToString("")
                    .ifEmpty { profile.take(1).uppercase() }
            } else null

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (initials != null) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "Profile",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            TextButton(
                onClick = onClick,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
            ) {
                Text("Switch")
            }
        }
    }
}

@Composable
private fun EmptyHomeState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = "No Netflix history imported yet",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Import your NetflixViewingHistory.csv or ViewingActivity.csv to start checking titles.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@PreviewLightDark
@Composable
fun HomeContentNoHistoryPreview() {
    StreamingHistoryTheme {
        HomeContent(
            totalRecords = 0,
            activeProfile = null,
            availableProfiles = emptyList(),
            onImportHub = {},
            onSearch = {},
            onProfileSelect = {},
            onCamera = {},
            onSettings = {},
        )
    }
}

@PreviewLightDark
@Composable
fun HomeContentWithProfilePreview() {
    StreamingHistoryTheme {
        HomeContent(
            totalRecords = 42,
            activeProfile = "Alex",
            availableProfiles = listOf("Alex", "Jordan"),
            onImportHub = {},
            onSearch = {},
            onProfileSelect = {},
            onCamera = {},
            onSettings = {},
        )
    }
}
