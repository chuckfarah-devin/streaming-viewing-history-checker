package com.chuckfarah.streaminghistory.ui.screen.camera

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chuckfarah.streaminghistory.domain.ocr.OcrResult
import com.chuckfarah.streaminghistory.domain.ocr.OcrTitleCandidate

/**
 * Displays the ML Kit OCR output and the top title candidates extracted from it.
 *
 * This is a diagnostic / verification screen (Step 10).  It lets the user
 * confirm that Netflix title text is being recognized before confidence/selection
 * logic is wired in (Step 11).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrResultView(
    ocrResult: OcrResult,
    onBack: () -> Unit,
    onTryAgain: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OCR Result") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 24.dp),
        ) {
            // ── Provider ────────────────────────────────────────────────────────
            item {
                Text(
                    text  = "Provider: ${ocrResult.allBlocks.firstOrNull()?.let { "ML Kit" } ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Top title candidates ────────────────────────────────────────────
            item {
                Text(
                    text  = "Top title candidates",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (ocrResult.titleCandidates.isNotEmpty()) {
                items(ocrResult.titleCandidates) { candidate ->
                    CandidateRow(candidate)
                }
            } else {
                item {
                    Text(
                        text  = "No title candidates found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── Full recognized text ────────────────────────────────────────────
            item { Spacer(Modifier.height(8.dp)) }

            item {
                Text(
                    text  = "Full recognized text",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            item {
                Text(
                    text  = ocrResult.rawText.ifBlank { "(no text recognized)" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── All detected blocks ─────────────────────────────────────────────
            item { Spacer(Modifier.height(8.dp)) }

            item {
                Text(
                    text  = "All detected text blocks",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            items(ocrResult.allBlocks) { block ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text     = block.text,
                            style    = MaterialTheme.typography.bodyMedium,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text  = "conf=${block.confidence ?: "—"}  " +
                                    "h=${block.boundingBox?.height() ?: 0}px",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── Actions ─────────────────────────────────────────────────────────
            item { Spacer(Modifier.height(16.dp)) }

            item {
                Row(
                    modifier            = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onTryAgain,
                        modifier = Modifier.weight(1f),
                    ) { Text("Try again") }
                    Button(
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                    ) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(candidate: OcrTitleCandidate) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Row(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment   = Alignment.CenterVertically,
        ) {
            Text(
                text     = candidate.text,
                style    = MaterialTheme.typography.bodyLarge,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text  = "%.1f".format(candidate.score),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
