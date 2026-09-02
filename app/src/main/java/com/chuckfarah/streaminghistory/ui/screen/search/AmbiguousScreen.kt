package com.chuckfarah.streaminghistory.ui.screen.search

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import com.chuckfarah.streaminghistory.domain.model.TitleCandidate
import com.chuckfarah.streaminghistory.ui.theme.StreamingHistoryTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmbiguousScreen(
    originalQuery: String,
    onSelected: (String) -> Unit,
    onBack: () -> Unit,
    onSearchManual: () -> Unit = onBack,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.searchState.collectAsState()

    LaunchedEffect(originalQuery) {
        viewModel.search(originalQuery)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select title") },
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
        AmbiguousContent(
            originalQuery = originalQuery,
            state = state,
            onSelected = onSelected,
            onSearchManual = onSearchManual,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
        )
    }
}

@Composable
fun AmbiguousContent(
    originalQuery: String,
    state: SearchUiState,
    onSelected: (String) -> Unit,
    onSearchManual: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        Text(
            text = "We found a few possibilities",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )

        Text(
            text = "\"$originalQuery\" could be one of these titles. Which one did you mean?",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when (val s = state) {
            is SearchUiState.Ambiguous -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    s.candidates.forEach { candidate ->
                        CandidateCard(
                            candidate = candidate,
                            onClick = { onSelected(candidate.normalizedTitle) },
                        )
                    }
                }
            }
            is SearchUiState.Loading -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            }
            else -> Unit
        }

        TextButton(
            onClick = onSearchManual,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text("Search manually")
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CandidateCard(
    candidate: TitleCandidate,
    onClick: () -> Unit,
) {
    val label = when (candidate.contentType) {
        ContentType.SERIES -> "Series"
        ContentType.MOVIE -> "Movie"
        ContentType.UNKNOWN -> null
    }

    val semanticsLabel = buildString {
        append(candidate.displayTitle)
        append(", ${candidate.recordCount} in history")
        if (label != null) append(", $label")
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = semanticsLabel },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = candidate.displayTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 2,
                )
                Text(
                    text = "${candidate.recordCount} in history",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (label != null) {
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
fun AmbiguousContentPreview() {
    StreamingHistoryTheme {
        AmbiguousContent(
            originalQuery = "Stranger Things",
            state = SearchUiState.Ambiguous(
                query = "Stranger Things",
                candidates = listOf(
                    TitleCandidate("Stranger Things", "stranger things", 92, 12, ContentType.SERIES),
                    TitleCandidate("Stranger Things 4", "stranger things 4", 78, 2, ContentType.UNKNOWN),
                ),
            ),
            onSelected = {},
            onSearchManual = {},
        )
    }
}
