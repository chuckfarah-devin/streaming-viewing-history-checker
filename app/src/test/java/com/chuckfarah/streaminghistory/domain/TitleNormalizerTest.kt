package com.chuckfarah.streaminghistory.domain

import com.chuckfarah.streaminghistory.domain.matching.TitleNormalizer
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

class TitleNormalizerTest {

    private lateinit var normalizer: TitleNormalizer

    @Before fun setup() { normalizer = TitleNormalizer() }

    // ── Basic operations ──────────────────────────────────────────────────────

    @Test fun `lowercases input`() {
        assertThat(normalizer.normalize("The Irishman")).isEqualTo("the irishman")
    }

    @Test fun `trims leading and trailing whitespace`() {
        assertThat(normalizer.normalize("  The Irishman  ")).isEqualTo("the irishman")
    }

    @Test fun `collapses internal whitespace`() {
        assertThat(normalizer.normalize("The   Irishman")).isEqualTo("the irishman")
    }

    @Test fun `converts curly apostrophe to straight`() {
        // \u2019 is the right single quotation mark
        assertThat(normalizer.normalize("It\u2019s Okay")).isEqualTo("it's okay")
    }

    @Test fun `converts em-dash to hyphen`() {
        assertThat(normalizer.normalize("Black\u2014Mirror")).isEqualTo("black-mirror")
    }

    @Test fun `converts en-dash to hyphen`() {
        assertThat(normalizer.normalize("My\u2013Life")).isEqualTo("my-life")
    }

    // ── Diacritics ────────────────────────────────────────────────────────────

    @Test fun `removes diacritics from e-acute`() {
        assertThat(normalizer.normalize("\u00e9lan")).isEqualTo("elan")
    }

    @Test fun `removes diacritics from n-tilde`() {
        assertThat(normalizer.normalize("ma\u00f1ana")).isEqualTo("manana")
    }

    @Test fun `removes diacritics from o-umlaut`() {
        assertThat(normalizer.normalize("Sch\u00f6nberg")).isEqualTo("schonberg")
    }

    // ── Idempotency ───────────────────────────────────────────────────────────

    @Test fun `normalizing already-normalized string is a no-op`() {
        val once   = normalizer.normalize("stranger things")
        val twice  = normalizer.normalize(once)
        assertThat(twice).isEqualTo(once)
    }

    // ── Punctuation and parentheses are preserved ─────────────────────────────

    @Test fun `does not strip parentheses or year`() {
        // Critical: "It (2017)" must NOT normalize to "it"
        assertThat(normalizer.normalize("It (2017)")).isEqualTo("it (2017)")
    }

    @Test fun `does not remove leading article`() {
        assertThat(normalizer.normalize("The Crown")).isEqualTo("the crown")
    }

    @Test fun `preserves colon in series title`() {
        val result = normalizer.normalize("Stranger Things: Season 1: Chapter One")
        assertThat(result).isEqualTo("stranger things: season 1: chapter one")
    }

    // ── Empty / blank ─────────────────────────────────────────────────────────

    @Test fun `empty string returns empty string`() {
        assertThat(normalizer.normalize("")).isEqualTo("")
    }

    @Test fun `whitespace-only returns empty string`() {
        assertThat(normalizer.normalize("   ")).isEqualTo("")
    }
}
