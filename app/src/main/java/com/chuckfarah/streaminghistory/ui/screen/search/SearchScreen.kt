package com.chuckfarah.streaminghistory.ui.screen.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

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

    // Side-effect: navigate when the match result is resolved
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
                title = { Text("Search History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value         = query,
                onValueChange = { query = it; viewModel.resetSearch() },
                label         = { Text("Title") },
                placeholder   = { Text("e.g. The Irishman") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                trailingIcon  = {
                    IconButton(onClick = { viewModel.search(query) }) {
                        Icon(Icons.Default.Search, "Search")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search(query) }),
            )

            Button(
                onClick  = { viewModel.search(query) },
                modifier = Modifier.fillMaxWidth(),
                enabled  = query.isNotBlank() && state !is SearchUiState.Loading,
            ) {
                Text("Search")
            }

            when (val s = state) {
                is SearchUiState.Loading -> {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is SearchUiState.NoMatch -> {
                    Text(
                        text  = "No previous viewing found.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text  = "\"$query\" was not found in your imported history.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is SearchUiState.Error -> {
                    Text(
                        text  = "Search error: ${s.message}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                else -> Unit  // Idle, Confident and Ambiguous handled via LaunchedEffect
            }
        }
    }
}
