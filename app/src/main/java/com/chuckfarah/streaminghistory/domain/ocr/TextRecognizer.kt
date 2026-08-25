package com.chuckfarah.streaminghistory.domain.ocr

import android.graphics.Bitmap
import android.graphics.Rect

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
 * Full OCR result prepared for the UI: raw text, all blocks, and the top title candidates.
 */
data class OcrResult(
    val rawText: String,
    val allBlocks: List<TextBlock>,
    val titleCandidates: List<OcrTitleCandidate>,
)
