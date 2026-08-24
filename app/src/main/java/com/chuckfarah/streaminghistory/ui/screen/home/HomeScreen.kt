package com.chuckfarah.streaminghistory.ui.screen.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chuckfarah.streaminghistory.ui.screen.import_.ImportViewModel

@Composable
fun HomeScreen(
    onNavigateToImport: () -> Unit,
    onNavigateToSearch: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val recordCount by produceState(initialValue = 0, viewModel) {
        // Re-read count whenever the screen is visible
    }

    Scaffold { padding ->
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text  = "Streaming History",
                style = MaterialTheme.typography.headlineMedium,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text  = "Check your Netflix viewing history",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(48.dp))

            Button(
                onClick  = onNavigateToImport,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Import Netflix History (CSV)")
            }

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick  = onNavigateToSearch,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Search History")
            }
        }
    }
}
