package com.chuckfarah.streaminghistory.ui.screen.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chuckfarah.streaminghistory.ui.theme.StreamingHistoryTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onResult: (String) -> Unit,
    onAmbiguous: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.searchState.collectAsState()
    var query by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        when (val s = state) {
            is SearchUiState.Confident -> {
                onResult(s.normalizedTitle)
                viewModel.resetSearch()
            }
            is SearchUiState.Ambiguous -> {
                onAmbiguous(s.query)
                viewModel.resetSearch()
            }
            else -> Unit
        }
    }

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
            onBack = onBack,
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
    onBack: () -> Unit,
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
            is SearchUiState.NoMatch -> NoMatchView(query = query)
            is SearchUiState.Error -> ErrorView()
            else -> Unit
        }

        Spacer(Modifier.height(24.dp))
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
            onBack = {},
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
            onBack = {},
        )
    }
}
