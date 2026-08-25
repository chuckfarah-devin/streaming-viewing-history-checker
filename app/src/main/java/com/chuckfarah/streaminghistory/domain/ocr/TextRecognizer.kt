package com.chuckfarah.streaminghistory.domain.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.chuckfarah.streaminghistory.domain.model.MatchResult

/**
 * Abstraction over on-device and cloud OCR providers (TS §6.1).
 *
 * Phase 1 has one implementation: bundled ML Kit on-device text recognition.
 * A future Google Cloud Vision implementation can satisfy the same interface.
 */
interface TextRecognizer {
    /** Human-readable provider name, e.g. "ML Kit On-Device". */
    val name: String

    /** Whether this provider requires an Internet connection. */
    val requiresNetwork: Boolean

    /**
     * Run OCR on the provided [imageBitmap] and return all detected text blocks.
     *
     * The caller retains ownership of [imageBitmap].  Implementations must not
     * store or persist the image.
     */
    suspend fun recognize(imageBitmap: Bitmap): TextRecognizerOutput
}

/**
 * Output of a [TextRecognizer] run.
 *
 * @param rawText Full concatenated text returned by the recognizer.
 * @param blocks Individual detected text blocks, each with text, bounding box and confidence.
 * @param providerName Name of the provider that produced this output.
 */
data class TextRecognizerOutput(
    val rawText: String,
    val blocks: List<TextBlock>,
    val providerName: String,
)

/**
 * One detected text block from an OCR provider.
 *
 * @param text The recognized text string.
 * @param boundingBox Detected bounding rectangle in image coordinates, or null if unavailable.
 * @param confidence Provider confidence in [0.0, 1.0], or null if not provided for this block.
 */
data class TextBlock(
    val text: String,
    val boundingBox: Rect?,
    val confidence: Float?,
)

/**
 * A candidate title extracted from OCR output, ranked by the heuristic in [OcrCandidateExtractor].
 *
 * @param text Candidate title text.
 * @param score Relative candidate score; higher means more likely to be the program title.
 */
data class OcrTitleCandidate(
    val text: String,
    val score: Float,
)

/**
 * One OCR candidate run through the existing title-matching pipeline.
 *
 * @param ocrText The text produced by the OCR candidate extractor.
 * @param matchResult The result of matching [ocrText] against the viewing-history database.
 */
data class OcrMatchedCandidate(
    val ocrText: String,
    val matchResult: MatchResult,
)

/**
 * Full OCR result prepared for the UI.
 *
 * @param rawText Full concatenated text from the recognizer.
 * @param allBlocks All detected text blocks.
 * @param titleCandidates Top OCR title candidates before matching.
 * @param matchedCandidates Each OCR candidate matched against history (diagnostic).
 * @param bestMatch The strongest match across candidates, or null if no candidate matched.
 * @param error Optional technical failure.  `null` if OCR completed (even if no text was found).
 */
data class OcrResult(
    val rawText: String,
    val allBlocks: List<TextBlock>,
    val titleCandidates: List<OcrTitleCandidate>,
    val matchedCandidates: List<OcrMatchedCandidate> = emptyList(),
    val bestMatch: MatchResult? = null,
    val error: Throwable? = null,
)
