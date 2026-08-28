package com.chuckfarah.streaminghistory.ui.screen.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.chuckfarah.streaminghistory.BuildConfig
import com.chuckfarah.streaminghistory.domain.model.ContentType
import com.chuckfarah.streaminghistory.domain.model.MatchResult
import com.chuckfarah.streaminghistory.domain.model.TitleCandidate
import com.chuckfarah.streaminghistory.domain.ocr.OcrResult
import com.chuckfarah.streaminghistory.ui.theme.LocalExtendedColorScheme
import com.chuckfarah.streaminghistory.ui.theme.StreamingHistoryTheme
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrResultView(
    ocrResult: OcrResult,
    onResult: (normalizedTitle: String) -> Unit,
    onTryAgain: () -> Unit,
    onSearchManual: () -> Unit,
    onBack: () -> Unit,
    onTryEnhanced: () -> Unit = {},
    canTryEnhanced: Boolean = false,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan result") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))

            when {
                ocrResult.error != null -> RecognitionErrorView(
                    onTryAgain = onTryAgain,
                    onBack = onBack,
                )
                ocrResult.titleCandidates.isEmpty() -> NoTextView(
                    onTryAgain = onTryAgain,
                    onBack = onBack,
                )
                else -> ResultBody(
                    ocrResult = ocrResult,
                    onResult = onResult,
                    onTryAgain = onTryAgain,
                    onSearchManual = onSearchManual,
                    onBack = onBack,
                    onTryEnhanced = onTryEnhanced,
                    canTryEnhanced = canTryEnhanced,
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ResultBody(
    ocrResult: OcrResult,
    onResult: (String) -> Unit,
    onTryAgain: () -> Unit,
    onSearchManual: () -> Unit,
    onBack: () -> Unit,
    onTryEnhanced: () -> Unit,
    canTryEnhanced: Boolean,
) {
    when (val best = ocrResult.bestMatch) {
        is MatchResult.Confident -> ConfidentMatchView(
            match = best,
            onResult = onResult,
            onTryAgain = onTryAgain,
            onSearchManual = onSearchManual,
            onBack = onBack,
        )
        is MatchResult.Ambiguous -> AmbiguousMatchView(
            candidates = best.candidates,
            onResult = onResult,
            onTryAgain = onTryAgain,
            onSearchManual = onSearchManual,
            onBack = onBack,
        )
        MatchResult.None, null -> UncertainMatchView(
            ocrResult = ocrResult,
            onTryAgain = onTryAgain,
            onSearchManual = onSearchManual,
            onBack = onBack,
            onTryEnhanced = onTryEnhanced,
            canTryEnhanced = canTryEnhanced,
        )
    }
}

@Composable
private fun ConfidentMatchView(
    match: MatchResult.Confident,
    onResult: (String) -> Unit,
    onTryAgain: () -> Unit,
    onSearchManual: () -> Unit,
    onBack: () -> Unit,
) {
    var canceled by remember { mutableStateOf(false) }

    LaunchedEffect(match, canceled) {
        if (!canceled) {
            delay(1200)
            onResult(match.normalizedTitle)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Found it",
            style = MaterialTheme.typography.headlineMedium,
            color = LocalExtendedColorScheme.current.success,
        )

        Text(
            text = match.displayTitle,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )

        if (!canceled) {
            CircularProgressIndicator()
            Text(
                text = "Opening your history…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TextButton(onClick = { canceled = true }) {
            Text("Not the right title?")
        }

        if (canceled) {
            ActionRow(
                primaryLabel = "Try again",
                onPrimary = onTryAgain,
                onSearchManual = onSearchManual,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun AmbiguousMatchView(
    candidates: List<TitleCandidate>,
    onResult: (String) -> Unit,
    onTryAgain: () -> Unit,
    onSearchManual: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "We found a few possibilities",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )

        Text(
            text = "Which one did you mean?",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            candidates.forEach { candidate ->
                CandidateCard(
                    candidate = candidate,
                    onClick = { onResult(candidate.normalizedTitle) },
                )
            }
        }

        TextButton(onClick = onSearchManual) {
            Text("Search manually")
        }

        ActionRow(
            primaryLabel = "Try again",
            onPrimary = onTryAgain,
            onSearchManual = onSearchManual,
            onBack = onBack,
            showSearchManualButton = false,
        )
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
                LabelChip(text = label)
            }
        }
    }
}

@Composable
private fun LabelChip(text: String) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun UncertainMatchView(
    ocrResult: OcrResult,
    onTryAgain: () -> Unit,
    onSearchManual: () -> Unit,
    onBack: () -> Unit,
    onTryEnhanced: () -> Unit,
    canTryEnhanced: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MessageWithActions(
            icon = null,
            title = "We couldn't confidently identify the title",
            body = "OCR succeeded, but the title didn't match your imported history closely enough. " +
                    "This is a recognition problem, not a 'not watched' result.",
            primaryLabel = "Try again",
            onPrimary = onTryAgain,
            onSearchManual = onSearchManual,
            onBack = onBack,
        )

        if (canTryEnhanced) {
            EnhancedRecognitionPrompt(
                onTryEnhanced = onTryEnhanced,
            )
        }

        if (BuildConfig.DEBUG) {
            DebugDiagnostics(ocrResult = ocrResult)
        }
    }
}

@Composable
private fun EnhancedRecognitionPrompt(
    onTryEnhanced: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Not confident with this photo?",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Enhanced recognition uses your internet connection to send the captured " +
                        "image to Google's servers for more accurate text reading.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onTryEnhanced,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Try enhanced recognition")
            }
        }
    }
}

@Composable
private fun NoTextView(
    onTryAgain: () -> Unit,
    onBack: () -> Unit,
) {
    MessageWithActions(
        icon = null,
        title = "We couldn't read the title",
        body = "Try taking another photo. Make sure the show or movie title is clearly visible and well-lit.",
        primaryLabel = "Try again",
        onPrimary = onTryAgain,
        onSearchManual = null,
        onBack = onBack,
    )
}

@Composable
private fun RecognitionErrorView(
    onTryAgain: () -> Unit,
    onBack: () -> Unit,
) {
    MessageWithActions(
        icon = null,
        title = "Something went wrong",
        body = "The image could not be read. Please try again.",
        primaryLabel = "Try again",
        onPrimary = onTryAgain,
        onSearchManual = null,
        onBack = onBack,
    )
}

@Composable
private fun MessageWithActions(
    icon: @Composable (() -> Unit)?,
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onSearchManual: (() -> Unit)?,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        icon?.invoke()

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )

        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        ActionRow(
            primaryLabel = primaryLabel,
            onPrimary = onPrimary,
            onSearchManual = onSearchManual,
            onBack = onBack,
        )
    }
}

@Composable
private fun ActionRow(
    primaryLabel: String,
    onPrimary: () -> Unit,
    onSearchManual: (() -> Unit)?,
    onBack: () -> Unit,
    showSearchManualButton: Boolean = true,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = onPrimary,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(primaryLabel)
        }

        if (showSearchManualButton && onSearchManual != null) {
            OutlinedButton(
                onClick = onSearchManual,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Search manually")
            }
        }

        TextButton(onClick = onBack) { Text("Back") }
    }
}

@Composable
private fun DebugDiagnostics(ocrResult: OcrResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Developer diagnostics",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            DiagnosticSection(title = "Raw OCR text") {
                Text(
                    text = ocrResult.rawText.ifBlank { "(empty)" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            DiagnosticSection(title = "Extracted title candidates") {
                if (ocrResult.titleCandidates.isEmpty()) {
                    Text("No candidates extracted.", style = MaterialTheme.typography.bodySmall)
                } else {
                    ocrResult.titleCandidates.forEach { candidate ->
                        Text(
                            text = "• \"${candidate.text}\" — score ${String.format(Locale.US, "%.2f", candidate.score)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            DiagnosticSection(title = "Matched candidates") {
                if (ocrResult.matchedCandidates.isEmpty()) {
                    Text("No candidates were matched.", style = MaterialTheme.typography.bodySmall)
                } else {
                    ocrResult.matchedCandidates.forEach { matched ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "OCR text: \"${matched.ocrText}\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            when (val result = matched.matchResult) {
                                is MatchResult.Confident -> {
                                    Text(
                                        text = "  Confident: ${result.displayTitle} (score ${result.score}, normalized: ${result.normalizedTitle})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                                is MatchResult.Ambiguous -> {
                                    Text(
                                        text = "  Ambiguous; top: ${result.candidates.firstOrNull()?.displayTitle ?: "—"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                }
                                MatchResult.None -> {
                                    Text(
                                        text = "  No match",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            DiagnosticSection(title = "Best match / rejection reason") {
                when (val best = ocrResult.bestMatch) {
                    is MatchResult.Confident -> {
                        Text(
                            text = "Best: ${best.displayTitle} (score ${best.score}, normalized: ${best.normalizedTitle})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    is MatchResult.Ambiguous -> {
                        Text(
                            text = "Best is ambiguous; top score: ${best.candidates.firstOrNull()?.score ?: 0}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    MatchResult.None, null -> {
                        val bestScore = ocrResult.matchedCandidates
                            .mapNotNull { (it.matchResult as? MatchResult.Confident)?.score }
                            .maxOrNull()
                        Text(
                            text = bestScore?.let {
                                "Rejected: best candidate score was $it, below the confidence threshold."
                            } ?: "Rejected: no candidate matched any history title.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@PreviewLightDark
@Composable
fun OcrResultViewAmbiguousPreview() {
    StreamingHistoryTheme {
        OcrResultView(
            ocrResult = OcrResult(
                rawText = "Stranger Things",
                allBlocks = emptyList(),
                titleCandidates = emptyList(),
                bestMatch = MatchResult.Ambiguous(
                    candidates = listOf(
                        TitleCandidate("Stranger Things", "stranger things", 92, 12, ContentType.SERIES),
                        TitleCandidate("Stranger Things 4", "stranger things 4", 78, 2, ContentType.UNKNOWN),
                    ),
                ),
            ),
            onResult = {},
            onTryAgain = {},
            onSearchManual = {},
            onBack = {},
        )
    }
}
