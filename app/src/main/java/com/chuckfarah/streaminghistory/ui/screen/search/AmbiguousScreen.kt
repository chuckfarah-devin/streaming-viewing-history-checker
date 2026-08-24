package com.chuckfarah.streaminghistory.ui.screen.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chuckfarah.streaminghistory.domain.model.TitleCandidate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmbiguousScreen(
    originalQuery: String,
    onSelected: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    // Re-run the search to get candidates for this query
    val state by viewModel.searchState.collectAsState()

    LaunchedEffect(originalQuery) {
        viewModel.search(originalQuery)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select title") },
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
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text    = "Multiple titles match \"$originalQuery\". Select one:",
                style   = MaterialTheme.typography.bodyMedium,
                color   = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )

            when (val s = state) {
                is SearchUiState.Ambiguous -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(s.candidates) { candidate ->
                            CandidateRow(candidate = candidate, onClick = {
                                onSelected(candidate.normalizedTitle)
                            })
                        }
                    }
                }
                is SearchUiState.Loading -> CircularProgressIndicator()
                else -> Unit
            }
        }
    }
}

@Composable
private fun CandidateRow(candidate: TitleCandidate, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text  = candidate.displayTitle,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text  = "${candidate.recordCount} viewing occurrence${if (candidate.recordCount != 1) "s" else ""}  •  ${candidate.contentType.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
