package com.chuckfarah.streaminghistory.ui.screen.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Profile selection screen — TS §7.2.
 *
 * Shown after a Tier 2 import that contains multiple profiles, or accessible
 * from Home to switch profiles.
 *
 * @param preloadedProfiles Optional list passed from the import screen to avoid
 *                          an extra DB round-trip.  If null, profiles are loaded
 *                          from the database.
 * @param onDone            Called when the user confirms a selection or taps Back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSelectionScreen(
    preloadedProfiles: List<String>? = null,
    onDone: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val profiles      by viewModel.profiles.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()

    LaunchedEffect(preloadedProfiles) {
        if (preloadedProfiles != null) viewModel.loadProfiles(preloadedProfiles)
        else viewModel.loadProfiles()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Profile") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                text     = "Choose the Netflix profile to view history for:",
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )

            if (profiles.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(profiles) { profile ->
                        ProfileRow(
                            profile   = profile,
                            isActive  = profile == activeProfile,
                            onClick   = {
                                viewModel.selectProfile(profile)
                                onDone()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(
    profile: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(profile) },
        trailingContent = {
            if (isActive) {
                Icon(
                    imageVector        = Icons.Default.Check,
                    contentDescription = "Active profile",
                    tint               = MaterialTheme.colorScheme.primary,
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}
