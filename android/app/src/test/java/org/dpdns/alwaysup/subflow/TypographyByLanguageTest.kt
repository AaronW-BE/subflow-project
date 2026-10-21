package org.dpdns.alwaysup.subflow

import androidx.compose.ui.text.style.LineBreak
import org.dpdns.alwaysup.subflow.ui.theme.AppleTypography
import org.dpdns.alwaysup.subflow.ui.theme.forLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Korean wraps at spaces, not between syllables (BIN-78). */
class TypographyByLanguageTest {

    @Test
    fun `Korean text keeps its words whole when it wraps`() {
        val ko = AppleTypography.forLanguage("ko")
        listOf(ko.bodyMedium, ko.bodyLarge, ko.titleMedium, ko.labelSmall, ko.headlineLarge).forEach {
            assertEquals(LineBreak.WordBreak.Phrase, it.lineBreak.wordBreak)
        }
    }

    @Test
    fun `everything else is left as it was`() {
        for (language in listOf("en", "zh", "ja", "de", "pt", "it", "")) {
            assertSame(language, AppleTypography, AppleTypography.forLanguage(language))
        }
    }
}
