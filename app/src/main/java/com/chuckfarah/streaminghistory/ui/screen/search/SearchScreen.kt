package com.chuckfarah.streaminghistory.ui.screen.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chuckfarah.streaminghistory.domain.model.ManualSearchRow
import com.chuckfarah.streaminghistory.ui.theme.StreamingHistoryTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.searchState.collectAsState()
    var query by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search history") },
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
        SearchContent(
            query = query,
            onQueryChange = {
                query = it
                viewModel.resetSearch()
            },
            onSearch = { viewModel.search(query) },
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
        )
    }
}

@Composable
fun SearchContent(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    state: SearchUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        Text(
            text = "Search your imported Netflix history",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )

        Text(
            text = "Enter the title you want to check.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Title") },
            placeholder = { Text("e.g. The Irishman") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = onSearch) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        )

        Button(
            onClick = onSearch,
            modifier = Modifier.fillMaxWidth(),
            enabled = query.isNotBlank() && state !is SearchUiState.Loading,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text("Search")
        }

        Spacer(Modifier.height(8.dp))

        when (val s = state) {
            is SearchUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
            is SearchUiState.Results  -> SearchResultsView(rows = s.rows)
            is SearchUiState.NoMatch  -> NoMatchView(query = query)
            is SearchUiState.Error    -> ErrorView()
            else -> Unit
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SearchResultsView(rows: List<ManualSearchRow>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Text(
            text = "${rows.size} result${if (rows.size == 1) "" else "s"} found",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SearchResultRow(row = row)
        }
    }
}

@Composable
private fun SearchResultRow(row: ManualSearchRow) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = row.rawTitle,
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = row.viewDate,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (row.durationMs != null) {
                Text(
                    text = formatDuration(row.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (row.sourceTier == 2) {
                Text(
                    text = "T2",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (!row.profileName.isNullOrEmpty()) {
                Text(
                    text = row.profileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Formats milliseconds as h:mm:ss or m:ss. */
private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

@Composable
private fun NoMatchView(query: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "No previous viewing found in your imported Netflix history.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "\"$query\" was not found.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Results reflect the currently selected profile and imported Netflix data.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorView() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Something went wrong while searching.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = "This is a technical error, not a history result.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@PreviewLightDark
@Composable
fun SearchContentIdlePreview() {
    StreamingHistoryTheme {
        SearchContent(
            query = "",
            onQueryChange = {},
            onSearch = {},
            state = SearchUiState.Idle,
        )
    }
}

@Preview
@Composable
fun SearchContentNoMatchPreview() {
    StreamingHistoryTheme {
        SearchContent(
            query = "Better Call Saul",
            onQueryChange = {},
            onSearch = {},
            state = SearchUiState.NoMatch,
        )
    }
}

@Preview
@Composable
fun SearchContentResultsPreview() {
    StreamingHistoryTheme {
        SearchContent(
            query = "The Watcher",
            onQueryChange = {},
            onSearch = {},
            state = SearchUiState.Results(
                rows = listOf(
                    ManualSearchRow("The Watcher: The Gloaming", "2022-10-28", 1, null, null, null),
                    ManualSearchRow("The Watcher: Held Hostage", "2022-10-22", 2, "Chuck", 2_820_000L, 2_750_000L),
                    ManualSearchRow("The Watcher: Götterdämmerung", "2022-10-17", 1, null, null, null),
                ),
            ),
        )
    }
}
