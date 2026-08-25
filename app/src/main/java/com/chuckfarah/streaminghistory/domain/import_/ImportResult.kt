package com.chuckfarah.streaminghistory.domain.import_

/** Result returned from an import operation (Tier 1 or Tier 2). */
sealed class ImportResult {

    // ── Tier 1 ───────────────────────────────────────────────────────────────

    /**
     * Tier 1 import completed successfully.
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

    // ── Tier 2 ───────────────────────────────────────────────────────────────

    /**
     * Tier 2 import completed successfully.
     *
     * @param recordsUpgraded  Existing Tier 1 rows upgraded with Tier 2 data.
     * @param recordsInserted  New Tier 2 records inserted (no matching Tier 1).
     * @param rowsSkipped      Rows skipped (supplemental content, malformed).
     * @param fileFingerprint  SHA-256 hex of the file content.
     * @param profiles         Distinct profile names found in the imported data.
     */
    data class Tier2Success(
        val recordsUpgraded: Int,
        val recordsInserted: Int,
        val rowsSkipped: Int,
        val fileFingerprint: String,
        val profiles: List<String>,
    ) : ImportResult()

    // ── Shared ───────────────────────────────────────────────────────────────

    /**
     * The file had already been imported (fingerprint match).
     * No records were added or modified.
     */
    data class AlreadyImported(val fileFingerprint: String) : ImportResult()

    /** A file-level error (bad header, unreadable file, DB failure). */
    data class Failure(val message: String) : ImportResult()
}
