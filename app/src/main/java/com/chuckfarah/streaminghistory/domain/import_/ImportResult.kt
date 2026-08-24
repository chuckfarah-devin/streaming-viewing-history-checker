package com.chuckfarah.streaminghistory.domain.import_

/** Result returned from a Tier 1 import operation. */
sealed class ImportResult {
    /**
     * Import completed successfully.
     *
     * @param recordsImported  Rows written to the database.
     * @param rowsSkipped      Rows that failed validation and were skipped.
     * @param fileFingerprint  SHA-256 hex of the file content.
     */
    data class Success(
        val recordsImported: Int,
        val rowsSkipped: Int,
        val fileFingerprint: String,
    ) : ImportResult()

    /**
     * The file had already been imported (fingerprint match).
     * No records were added or modified.
     */
    data class AlreadyImported(val fileFingerprint: String) : ImportResult()

    /** A file-level error (bad header, unreadable file, DB failure). */
    data class Failure(val message: String) : ImportResult()
}
