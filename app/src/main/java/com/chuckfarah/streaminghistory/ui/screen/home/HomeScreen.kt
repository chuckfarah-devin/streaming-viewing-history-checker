package com.chuckfarah.streaminghistory.ui.screen.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun HomeScreen(
    onNavigateToImport: () -> Unit,
    onNavigateToSearch: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val totalRecords  by viewModel.totalRecords.collectAsState()
    val recentEntries by viewModel.recentEntries.collectAsState()

    // Refresh when the screen becomes active
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold { padding ->
        LazyColumn(
            modifier            = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding      = PaddingValues(vertical = 24.dp),
        ) {
            // ── Header ────────────────────────────────────────────────────────
            item {
                Text(
                    text  = "Streaming History",
                    style = MaterialTheme.typography.headlineMedium,
                )
            }

            item {
                Text(
                    text  = "Check your Netflix viewing history",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Action buttons ────────────────────────────────────────────────
            item { Spacer(Modifier.height(16.dp)) }

            item {
                Button(
                    onClick  = onNavigateToImport,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Import Netflix History (CSV)") }
            }

            item {
                OutlinedButton(
                    onClick  = onNavigateToSearch,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Search History") }
            }

            // ── Recent titles ─────────────────────────────────────────────────
            if (recentEntries.isNotEmpty()) {
                item { Spacer(Modifier.height(8.dp)) }

                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier          = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text  = "Recently watched",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (totalRecords > 0) {
                            Text(
                                text  = "$totalRecords total",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                items(recentEntries) { entry ->
                    RecentEntryRow(entry)
                }
            } else if (totalRecords == 0) {
                // No data yet — prompt the user to import
                item {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text  = "No history imported yet. Tap \"Import Netflix History\" to get started.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentEntryRow(entry: RecentEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                text     = entry.displayTitle,
                style    = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text  = entry.viewDate,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
