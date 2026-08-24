package com.chuckfarah.streaminghistory.ui.screen.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chuckfarah.streaminghistory.domain.model.ContentType
import com.chuckfarah.streaminghistory.domain.model.EpisodeRecord
import com.chuckfarah.streaminghistory.domain.model.SeriesStats
import com.chuckfarah.streaminghistory.domain.model.ViewingResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    normalizedTitle: String,
    onBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    LaunchedEffect(normalizedTitle) {
        viewModel.loadResult(normalizedTitle)
    }

    val state by viewModel.resultState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Result") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        }
    ) { padding ->
        when (val s = state) {
            is ResultUiState.Loading -> {
                Box(
                    modifier            = Modifier.fillMaxSize().padding(padding),
                    contentAlignment    = androidx.compose.ui.Alignment.Center,
                ) { CircularProgressIndicator() }
            }
            is ResultUiState.NotWatched -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text  = "No previous viewing found",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text  = "This title was not found in your imported Netflix history.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            is ResultUiState.Success -> WatchedResult(s.result, padding)
            is ResultUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                ) {
                    Text(
                        text  = "Error: ${s.message}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text  = "A technical error occurred. This is not the same as a title not being watched.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchedResult(result: ViewingResult.Watched, padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Title and watched status
        Text(
            text  = result.displayTitle,
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text  = "Previously watched",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        HorizontalDivider()

        // Viewing stats
        LabeledValue("Viewing occurrences", result.viewingOccurrences.toString())
        LabeledValue("Most recent viewing", result.mostRecentDate)

        if (result.allDates.size > 1) {
            LabeledValue(
                label = "All viewing dates",
                value = result.allDates.joinToString(" · "),
            )
        }

        // Series breakdown (SERIES only)
        result.seriesStats?.let { stats ->
            HorizontalDivider()
            SeriesSection(stats)
        }

        // Individual episode list (SERIES only)
        if (result.episodes.isNotEmpty()) {
            HorizontalDivider()
            Text(
                text  = "Episodes watched (${result.episodes.size})",
                style = MaterialTheme.typography.titleSmall,
            )
            result.episodes.forEach { ep ->
                EpisodeRow(ep)
            }
        }

        // Content-type badge
        HorizontalDivider()
        Text(
            text  = "Content type: ${result.contentType.name}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SeriesSection(stats: SeriesStats) {
    Text(
        text  = "Series statistics",
        style = MaterialTheme.typography.titleSmall,
    )
    // These three values are always presented separately (TS §4.4 / BS BR-009)
    LabeledValue("Viewing occurrences",   stats.viewingOccurrences.toString())
    LabeledValue("Distinct episodes watched", stats.distinctEpisodes.toString())
    LabeledValue("Seasons represented",   stats.seasonsRepresented.toString())
}

@Composable
private fun EpisodeRow(ep: EpisodeRecord) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            // Show season + episode title, or raw title if no episode title
            val title = when {
                ep.episodeTitle != null && ep.seasonLabel != null ->
                    "${ep.seasonLabel}: ${ep.episodeTitle}"
                ep.episodeTitle != null -> ep.episodeTitle
                ep.seasonLabel  != null -> ep.seasonLabel
                else                    -> ep.rawTitle
            }
            Text(
                text  = title,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text  = ep.viewDate,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(
        modifier            = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text  = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
