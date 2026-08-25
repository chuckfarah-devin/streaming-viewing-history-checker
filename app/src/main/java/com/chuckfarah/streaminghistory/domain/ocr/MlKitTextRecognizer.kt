package com.chuckfarah.streaminghistory.domain.ocr

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bundled ML Kit Text Recognition V2 implementation of [TextRecognizer] (TS §5.2).
 *
 * The bundled model is packaged with the APK, so recognition works offline.
 * Model: `com.google.mlkit:text-recognition:16.0.1`.
 */
@Singleton
class MlKitTextRecognizer @Inject constructor() : TextRecognizer {

    override val name: String = "ML Kit On-Device Text Recognition V2"
    override val requiresNetwork: Boolean = false

    private val mlKitRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun recognize(imageBitmap: Bitmap): TextRecognizerOutput =
        withContext(Dispatchers.IO) {
            val inputImage = InputImage.fromBitmap(imageBitmap, /* rotationDegrees= */ 0)
            val visionText = Tasks.await(mlKitRecognizer.process(inputImage))

            val blocks = visionText.textBlocks.map { mlKitBlock ->
                // Block-level confidence is not exposed by ML Kit; take the average
                // of the per-line confidence values (TS §5.2 per-element confidence).
                val blockConfidence = mlKitBlock.lines
                    .takeIf { it.isNotEmpty() }
                    ?.map { it.confidence }
                    ?.average()
                    ?.toFloat()

                TextBlock(
                    text        = mlKitBlock.text,
                    boundingBox = mlKitBlock.boundingBox,
                    confidence  = blockConfidence,
                )
            }

            TextRecognizerOutput(
                rawText     = visionText.text,
                blocks      = blocks,
                providerName = name,
            )
        }
}
