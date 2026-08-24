package com.chuckfarah.streaminghistory.domain.matching

import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Normalizes a title string for comparison (TS §4.1).
 *
 * Steps applied in order:
 *  1. Unicode NFC normalization
 *  2. Lowercase
 *  3. Trim leading/trailing whitespace
 *  4. Collapse internal whitespace runs to a single space
 *  5. Curly apostrophes/quotes → straight equivalents
 *  6. Em-dash / en-dash → hyphen-minus
 *  7. Diacritic removal: NFD decompose, strip combining characters (Unicode Mn)
 *  8. Remove control characters
 *
 * Normalization does NOT remove punctuation, leading articles, or subtitles.
 */
@Singleton
class TitleNormalizer @Inject constructor() {

    fun normalize(input: String): String {
        // Step 1: NFC
        var s = Normalizer.normalize(input, Normalizer.Form.NFC)
        // Step 2: lowercase
        s = s.lowercase()
        // Step 3: trim
        s = s.trim()
        // Step 4: collapse internal whitespace
        s = s.replace(Regex("\\s+"), " ")
        // Step 5: curly apostrophes/quotes → straight
        s = s.replace('\u2018', '\'').replace('\u2019', '\'')
             .replace('\u201C', '"').replace('\u201D', '"')
        // Step 6: em/en dash → hyphen-minus
        s = s.replace('\u2014', '-').replace('\u2013', '-')
        // Step 7: diacritics — NFD then strip combining marks
        val nfd = Normalizer.normalize(s, Normalizer.Form.NFD)
        s = nfd.replace(Regex("\\p{Mn}"), "")
        // Step 8: control characters
        s = s.replace(Regex("\\p{Cc}"), "")
        return s
    }
}
