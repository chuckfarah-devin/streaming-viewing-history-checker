package com.chuckfarah.streaminghistory.domain.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Base64
import android.util.Log
import com.chuckfarah.streaminghistory.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.net.ssl.HttpsURLConnection

/**
 * Google Cloud Vision API REST implementation of [TextRecognizer] (TS §6.2).
 *
 * Images are compressed to JPEG (quality 80, max dimension 1920 px) and sent
 * over HTTPS to `https://vision.googleapis.com/v1/images:annotate` with the
 * `TEXT_DETECTION` feature. Only image data is transmitted; viewing history
 * and other personal data never leave the device.
 *
 * The API key is injected from `local.properties` via `BuildConfig` and must
 * never be committed (TS §6.2).
 */
class GoogleVisionTextRecognizer @Inject constructor() : TextRecognizer {

    override val name: String = "Google Cloud Vision API"
    override val requiresNetwork: Boolean = true

    override suspend fun recognize(imageBitmap: Bitmap): TextRecognizerOutput =
        withContext(Dispatchers.IO) {
            val apiKey = BuildConfig.GOOGLE_VISION_API_KEY
            if (apiKey.isBlank()) {
                Log.w(TAG, "Google Cloud Vision API key is missing; skipping network OCR")
                return@withContext emptyOutput()
            }

            val scaled = scaleToMaxDimension(imageBitmap)
            val base64Image = compressToJpegBase64(scaled)

            val requestJson = JSONObject().apply {
                put("requests", JSONArray().apply {
                    put(JSONObject().apply {
                        put("image", JSONObject().apply {
                            put("content", base64Image)
                        })
                        put("features", JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "TEXT_DETECTION")
                            })
                        })
                    })
                })
            }

            val response = postJson(
                endpoint = "https://vision.googleapis.com/v1/images:annotate?key=${
                    URLEncoder.encode(apiKey, "UTF-8")
                }",
                json = requestJson.toString(),
            )

            parseResponse(response)
        }

    private fun emptyOutput() = TextRecognizerOutput(
        rawText     = "",
        blocks      = emptyList(),
        providerName = name,
    )

    private fun scaleToMaxDimension(bitmap: Bitmap, maxDimension: Int = 1920): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val max = maxOf(width, height)
        if (max <= maxDimension) return bitmap

        val scale = maxDimension.toFloat() / max
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun compressToJpegBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        val bytes = stream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun postJson(endpoint: String, json: String): String {
        val url = URL(endpoint)
        val connection = (url.openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
        }

        connection.outputStream.use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
            out.flush()
        }

        val responseCode = connection.responseCode
        val inputStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val response = inputStream?.use { it.reader().readText() } ?: ""

        if (responseCode !in 200..299) {
            throw NetworkOcrException("Vision API error $responseCode: $response")
        }

        return response
    }

    private fun parseResponse(json: String): TextRecognizerOutput {
        val root = JSONObject(json)
        val responses = root.optJSONArray("responses") ?: JSONArray()
        if (responses.length() == 0) return emptyOutput()

        val firstResponse = responses.getJSONObject(0)
        val textAnnotations = firstResponse.optJSONArray("textAnnotations") ?: return emptyOutput()
        if (textAnnotations.length() == 0) return emptyOutput()

        val fullText = textAnnotations.getJSONObject(0).optString("description", "")

        val blocks = mutableListOf<TextBlock>()
        for (i in 1 until textAnnotations.length()) {
            val annotation = textAnnotations.getJSONObject(i)
            val text = annotation.optString("description", "")
            val boundingBox = parseBoundingBox(annotation.optJSONObject("boundingPoly"))
            if (text.isNotBlank() && boundingBox != null) {
                blocks += TextBlock(text = text, boundingBox = boundingBox, confidence = null)
            }
        }

        return TextRecognizerOutput(
            rawText     = fullText,
            blocks      = blocks,
            providerName = name,
        )
    }

    private fun parseBoundingBox(poly: JSONObject?): Rect? {
        val vertices = poly?.optJSONArray("vertices") ?: return null
        if (vertices.length() < 4) return null

        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var bottom = Int.MIN_VALUE

        for (i in 0 until vertices.length()) {
            val vertex = vertices.getJSONObject(i)
            val x = vertex.optInt("x", 0)
            val y = vertex.optInt("y", 0)
            left = minOf(left, x)
            top = minOf(top, y)
            right = maxOf(right, x)
            bottom = maxOf(bottom, y)
        }

        return Rect(left, top, right, bottom)
    }

    /** Exception thrown when the network or Google Vision API itself fails. */
    class NetworkOcrException(message: String) : Exception(message)

    companion object {
        private const val TAG = "GoogleVisionOcr"
    }
}
