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
import com.chuckfarah.streaminghistory.domain.model.MatchResult
import com.chuckfarah.streaminghistory.domain.model.TitleCandidate
import com.chuckfarah.streaminghistory.domain.ocr.OcrResult

/**
 * Displays the ML Kit OCR output, the matched title result, and the strongest
 * history match.  It navigates the user to the normal result flow when a title
 * is confidently or ambiguously matched.
 *
 * This is the Step 11 integration screen.  Diagnostic details (OCR candidates,
 * per-candidate match classification, scores) are shown as a development aid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrResultView(
    ocrResult: OcrResult,
    onResult: (normalizedTitle: String) -> Unit,
    onTryAgain: () -> Unit,
    onBack: () -> Unit,
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
            when {
                ocrResult.error != null -> item {
                    RecognitionErrorView(error = ocrResult.error!!, onTryAgain = onTryAgain, onBack = onBack)
                }
                ocrResult.titleCandidates.isEmpty() -> item {
                    NoTextView(onTryAgain = onTryAgain, onBack = onBack)
                }
                else -> {
                    item { MatchStateCard(ocrResult = ocrResult, onResult = onResult, onTryAgain = onTryAgain, onBack = onBack) }
                    item { DiagnosticsCard(ocrResult = ocrResult) }
                }
            }
        }
    }
}

@Composable
private fun MatchStateCard(
    ocrResult: OcrResult,
    onResult: (String) -> Unit,
    onTryAgain: () -> Unit,
    onBack: () -> Unit,
) {
    when (val best = ocrResult.bestMatch) {
        is MatchResult.Confident -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text  = "Confident match",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text  = best.displayTitle,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text  = "Score: ${best.score}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Button(
                        onClick = { onResult(best.normalizedTitle) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("View history") }
                }
            }
        }
        is MatchResult.Ambiguous -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text  = "Ambiguous match — select a title:",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    best.candidates.forEach { candidate ->
                        CandidateRow(candidate = candidate, onClick = { onResult(candidate.normalizedTitle) })
                    }
                }
            }
        }
        MatchResult.None, null -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text  = "Title not confidently identified",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text  = if (best is MatchResult.None)
                            "OCR succeeded, but no title in your history matches closely enough."
                        else
                            "OCR did not find any usable candidate text.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text  = "This is not the same as 'not watched' — the title simply could not be confidently recognized.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onBack) { Text("Back") }
                        Button(onClick = onTryAgain) { Text("Try again") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(candidate: TitleCandidate, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text     = candidate.displayTitle,
                style    = MaterialTheme.typography.bodyMedium,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text  = "${candidate.score}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun NoTextView(onTryAgain: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text  = "No text recognized",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text  = "The image did not contain readable text. Try taking another photo.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Button(onClick = onTryAgain) { Text("Try again") }
        }
    }
}

@Composable
private fun RecognitionErrorView(error: Throwable, onTryAgain: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text  = "Recognition failed",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text  = "${error.message}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text  = "This is a technical error, not a 'not watched' result.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            Button(onClick = onTryAgain) { Text("Try again") }
        }
    }
}

@Composable
private fun DiagnosticsCard(ocrResult: OcrResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text  = "Diagnostics",
                style = MaterialTheme.typography.titleSmall,
            )

            Text(
                text  = "Raw text: ${ocrResult.rawText}",
                style = MaterialTheme.typography.bodySmall,
            )

            Text(
                text  = "OCR candidates:",
                style = MaterialTheme.typography.bodySmall,
            )
            ocrResult.titleCandidates.forEach { candidate ->
                Text(
                    text  = "  • ${candidate.text} (score ${String.format("%.1f", candidate.score)})",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Text(
                text  = "Matched candidates:",
                style = MaterialTheme.typography.bodySmall,
            )
            ocrResult.matchedCandidates.forEach { mc ->
                val classification = when (mc.matchResult) {
                    is MatchResult.Confident -> "Confident ${mc.matchResult.score}"
                    is MatchResult.Ambiguous -> "Ambiguous"
                    is MatchResult.None      -> "None"
                }
                Text(
                    text  = "  • ${mc.ocrText} → $classification",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
