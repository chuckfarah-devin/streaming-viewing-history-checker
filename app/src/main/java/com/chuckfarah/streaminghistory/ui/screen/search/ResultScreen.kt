package com.chuckfarah.streaminghistory.ui.screen.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chuckfarah.streaminghistory.domain.model.ContentType
import com.chuckfarah.streaminghistory.domain.model.EpisodeRecord
import com.chuckfarah.streaminghistory.domain.model.SeriesStats
import com.chuckfarah.streaminghistory.domain.model.ViewingResult
import com.chuckfarah.streaminghistory.domain.model.ViewingSession
import com.chuckfarah.streaminghistory.ui.formatter.distinctEpisodes
import com.chuckfarah.streaminghistory.ui.formatter.formatDate
import com.chuckfarah.streaminghistory.ui.formatter.formatDuration
import com.chuckfarah.streaminghistory.ui.formatter.formatReached
import com.chuckfarah.streaminghistory.ui.formatter.repeatBadge
import com.chuckfarah.streaminghistory.ui.formatter.seasons
import com.chuckfarah.streaminghistory.ui.formatter.viewingRecords
import com.chuckfarah.streaminghistory.ui.theme.StreamingHistoryTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    normalizedTitle: String,
    onBack: () -> Unit,
    onSearchManual: () -> Unit,
    onScanAgain: () -> Unit,
    viewModel: ResultViewModel = hiltViewModel(),
) {
    val state by viewModel.resultState.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val availableProfiles by viewModel.availableProfiles.collectAsState()

    LaunchedEffect(normalizedTitle, activeProfile) {
        viewModel.loadResult()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History result") },
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
        ResultContent(
            state = state,
            activeProfile = activeProfile,
            availableProfiles = availableProfiles,
            onSelectProfile = { viewModel.selectProfile(it) },
            onRetry = { viewModel.loadResult() },
            onBack = onBack,
            onSearchManual = onSearchManual,
            onScanAgain = onScanAgain,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
        )
    }
}

@Composable
fun ResultContent(
    state: ResultUiState,
    activeProfile: String?,
    availableProfiles: List<String>,
    onSelectProfile: (String?) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onSearchManual: () -> Unit,
    onScanAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        when (val s = state) {
            is ResultUiState.Loading -> LoadingView()
            is ResultUiState.Success -> WatchedResult(
                result = s.result,
                activeProfile = activeProfile,
                availableProfiles = availableProfiles,
                onSelectProfile = onSelectProfile,
                onSearchManual = onSearchManual,
                onScanAgain = onScanAgain,
            )
            is ResultUiState.NotWatched -> NotWatchedContent(
                displayTitle = s.displayTitle,
                activeProfile = activeProfile,
                availableProfiles = availableProfiles,
                onSelectProfile = onSelectProfile,
                onSearchManual = onSearchManual,
                onScanAgain = onScanAgain,
            )
            is ResultUiState.Error -> ErrorContent(
                onRetry = onRetry,
                onBack = onBack,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
        contentAlignment = Alignment.Center,
    ) { CircularProgressIndicator() }
}

@Composable
private fun WatchedResult(
    result: ViewingResult.Watched,
    activeProfile: String?,
    availableProfiles: List<String>,
    onSelectProfile: (String?) -> Unit,
    onSearchManual: () -> Unit,
    onScanAgain: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (availableProfiles.isNotEmpty()) {
            ProfileSwitcher(
                activeProfile = activeProfile,
                availableProfiles = availableProfiles,
                onSelect = onSelectProfile,
            )
        }

        Text(
            text = result.displayTitle,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )

        if (result.contentType == ContentType.SERIES) {
            SeriesChip()
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "Found in your imported Netflix history",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }

        // Source-tier chip: Tier 2 when timing data is available, Tier 1 otherwise
        val hasTiming = result.mostRecentDuration != null || result.reached != null
        SourceTierChip(tier = if (hasTiming) 2 else 1)

        // Hierarchy: date → elapsed → reached → count → series/episode
        LabeledValue("Most recent viewing", formatDate(result.mostRecentDate))

        result.mostRecentDuration?.let { formatDuration(it) }?.let { durationText ->
            LabeledValue("Most recent session", durationText)
        }

        result.reached?.let { formatReached(it) }?.let { reachedText ->
            LabeledValue("Reached", reachedText)
        }

        LabeledValue("Viewing records", viewingRecords(result.viewingOccurrences))

        if (result.seriesStats != null) {
            SeriesInsightCard(stats = result.seriesStats)
        }

        if (result.episodes.isNotEmpty()) {
            EpisodeSection(episodes = result.episodes)
        }

        if (result.contentType != ContentType.SERIES && result.sessions.size > 1) {
            RepeatedHistorySection(sessions = result.sessions)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onSearchManual) { Text("Search manually") }
            TextButton(onClick = onScanAgain) { Text("Scan again") }
        }
    }
}

@Composable
private fun NotWatchedContent(
    displayTitle: String,
    activeProfile: String?,
    availableProfiles: List<String>,
    onSelectProfile: (String?) -> Unit,
    onSearchManual: () -> Unit,
    onScanAgain: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (availableProfiles.isNotEmpty()) {
            ProfileSwitcher(
                activeProfile = activeProfile,
                availableProfiles = availableProfiles,
                onSelect = onSelectProfile,
            )
        }

        Text(
            text = displayTitle,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Help,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = "Not found in your imported Netflix history",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = "Results reflect the currently imported data and selected profile. " +
                    "This is not a statement about what you have or haven't watched.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onSearchManual) { Text("Search manually") }
            TextButton(onClick = onScanAgain) { Text("Scan again") }
        }
    }
}

@Composable
private fun ErrorContent(
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Could not load this result",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = "A technical error occurred. This is different from a title that is not in your history.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onRetry) { Text("Try again") }
            TextButton(onClick = onBack) { Text("Back") }
        }
    }
}

@Composable
private fun LabeledValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun SourceTierChip(tier: Int) {
    val label = "Tier $tier"
    val containerColor = if (tier == 2)
        MaterialTheme.colorScheme.secondaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (tier == 2)
        MaterialTheme.colorScheme.onSecondaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(containerColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { contentDescription = "Source: $label" },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}

@Composable
private fun SeriesChip() {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = "Series",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun SeriesInsightCard(stats: SeriesStats) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Series insight",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            LabeledValue("Viewing occurrences", viewingRecords(stats.viewingOccurrences))
            LabeledValue("Distinct episodes", distinctEpisodes(stats.distinctEpisodes))
            LabeledValue("Seasons represented", seasons(stats.seasonsRepresented))
        }
    }
}

@Composable
private fun EpisodeSection(episodes: List<EpisodeRecord>) {
    var expanded by remember { mutableStateOf(false) }
    val displayEpisodes = if (expanded) episodes else episodes.take(3)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Episodes",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )

        if (expanded) {
            val grouped = episodes.groupBy { EpisodeGroupKey(it.seasonNumber, it.seasonLabel) }
            val sortedGroups = grouped.toList().sortedBy { it.first.sortKey }
            sortedGroups.forEach { (group, groupEpisodes) ->
                val header = group.seasonLabel ?: "Extras & specials"
                Text(
                    text = header,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                )
                groupEpisodes.forEach { episode ->
                    EpisodeRow(episode = episode)
                }
            }
        } else {
            displayEpisodes.forEach { episode ->
                EpisodeRow(episode = episode)
            }
        }

        if (episodes.size > 3) {
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                )
                Spacer(Modifier.size(8.dp))
                Text(if (expanded) "Show less" else "Show all episodes")
            }
        }
    }
}

private data class EpisodeGroupKey(
    val seasonNumber: Int?,
    val seasonLabel: String?,
) {
    val sortKey: Int
        get() = seasonNumber ?: Int.MAX_VALUE
}

@Composable
private fun EpisodeRow(episode: EpisodeRecord) {
    var showRecords by remember { mutableStateOf(false) }

    val title = when {
        episode.episodeTitle != null && episode.seasonLabel != null ->
            "${episode.seasonLabel}: ${episode.episodeTitle}"
        episode.episodeTitle != null -> episode.episodeTitle
        episode.seasonLabel != null -> episode.seasonLabel
        else -> episode.rawTitle
    }

    val badge = repeatBadge(episode.recordCount)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showRecords = !showRecords },
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 2,
                )
                if (badge != null) {
                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            Text(
                text = formatDate(episode.mostRecentDate),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            AnimatedVisibility(visible = showRecords) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    episode.records.forEach { session ->
                        SessionRow(session = session)
                    }
                }
            }
        }
    }
}

@Composable
private fun RepeatedHistorySection(sessions: List<ViewingSession>) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Viewing history",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                sessions.forEach { session ->
                    SessionRow(session = session)
                }
            }
        }

        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
            )
            Spacer(Modifier.size(8.dp))
            Text(if (expanded) "Hide viewing history" else "View all viewing dates")
        }
    }
}

@Composable
private fun SessionRow(session: ViewingSession) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatDate(session.viewDate),
            style = MaterialTheme.typography.bodyMedium,
        )
        Column(horizontalAlignment = Alignment.End) {
            if (session.durationMs != null) {
                val formatted = formatDuration(session.durationMs)
                if (formatted != null) {
                    Text(
                        text = "Session: $formatted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (session.reachedMs != null) {
                val formatted = formatReached(session.reachedMs)
                if (formatted != null) {
                    Text(
                        text = "Reached: $formatted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileSwitcher(
    activeProfile: String?,
    availableProfiles: List<String>,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = activeProfile ?: "Select a profile"

    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier
                .height(48.dp)
                .semantics { contentDescription = "Active profile: $label" },
        ) {
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            availableProfiles.forEach { profile ->
                DropdownMenuItem(
                    text = { Text(profile) },
                    onClick = {
                        onSelect(profile)
                        expanded = false
                    },
                    trailingIcon = if (profile == activeProfile) {
                        { Icon(Icons.Default.CheckCircle, contentDescription = "Selected") }
                    } else null,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
fun ResultScreenTier1Preview() {
    StreamingHistoryTheme {
        ResultContent(
            state = ResultUiState.Success(
                ViewingResult.Watched(
                    displayTitle = "The Irishman",
                    normalizedTitle = "the irishman",
                    contentType = ContentType.UNKNOWN,
                    profileName = null,
                    viewingOccurrences = 1,
                    mostRecentDate = "2026-08-23",
                    allDates = listOf("2026-08-23"),
                    mostRecentDuration = null,
                    reached = null,
                    sessions = listOf(
                        ViewingSession("The Irishman", "2026-08-23", null, null, null),
                    ),
                    seriesStats = null,
                    episodes = emptyList(),
                )
            ),
            activeProfile = null,
            availableProfiles = emptyList(),
            onSelectProfile = {},
            onRetry = {},
            onBack = {},
            onSearchManual = {},
            onScanAgain = {},
        )
    }
}
