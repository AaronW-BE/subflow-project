package org.dpdns.alwaysup.subflow

import android.content.Context
import android.content.res.Configuration
import android.graphics.Typeface
import android.text.TextPaint
import androidx.test.platform.app.InstrumentationRegistry
import org.dpdns.alwaysup.subflow.data.preferences.SYSTEM_LANGUAGE
import org.dpdns.alwaysup.subflow.data.preferences.SupportedLanguages
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.domain.util.CalculationSnapshot
import org.dpdns.alwaysup.subflow.domain.util.CurrencyFormatter
import org.dpdns.alwaysup.subflow.domain.util.RatesSnapshot
import org.dpdns.alwaysup.subflow.domain.util.localeForLanguageCode
import org.dpdns.alwaysup.subflow.widget.WidgetCoverageFontSizeSp
import org.dpdns.alwaysup.subflow.widget.summariseForWidget
import org.dpdns.alwaysup.subflow.widget.widgetPresentation
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/** Native font metrics only: no Activity, persisted fixtures, preferences, billing or live clock. */
class WidgetTypographyTest {
    private lateinit var target: Context
    private val today = LocalDate.of(2026, 6, 15)
    private val snapshot = CalculationSnapshot("USD", today, RatesSnapshot(1, mapOf("USD" to 1.0)))

    @Before fun requireDebugTarget() {
        target = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("org.dpdns.alwaysup.subflow.debug", BuildConfig.APPLICATION_ID)
        assertEquals(BuildConfig.APPLICATION_ID, target.packageName)
    }

    private fun subscription(id: String, amount: Double, currency: String) = Subscription(
        id = id, name = id, amount = amount, currency = currency,
        firstBillDate = today.minusMonths(1).toString(), nextBillDate = today.plusDays(7).toString(),
        reminderDaysBefore = 0, updatedAt = 0L
    )

    private fun known() = subscription("normal", 12.0, "USD")
    private fun unknown() = subscription("unknown", 9.0, "ZZZ")

    private fun forEachLocale(check: (String, Context) -> String?) {
        val failures = SupportedLanguages.filter { it.code != SYSTEM_LANGUAGE }.mapNotNull { language ->
            val locale = localeForLanguageCode(language.code)
            val config = Configuration(target.resources.configuration).apply {
                fontScale = 1f
                setLocale(locale)
                setLayoutDirection(locale)
            }
            check(language.code, target.createConfigurationContext(config))
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private fun overflow(context: Context, text: String, sizeSp: Int, bold: Boolean): String? {
        // Standard font scale: sp == dp. Min responsive width 180dp minus 8dp on each side.
        val density = context.resources.displayMetrics.density
        val availablePx = 164f * density
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = sizeSp * density
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val measuredPx = paint.measureText(text)
        return if (measuredPx <= availablePx) null
        else "'$text' at ${sizeSp}sp measures ${measuredPx}px > ${availablePx}px"
    }

    @Test fun partialCaptionFitsOneLineInEverySupportedLocale() {
        val summary = summariseForWidget(listOf(known(), unknown()), snapshot)
        assertFalse(summary.estimate.isComplete)
        assertEquals(12.0, summary.monthlyTotal!!, 0.0)
        forEachLocale { code, context ->
            val presentation = widgetPresentation(summary, context)
            val caption = presentation.coverage
            if (caption.isNullOrBlank()) "$code: partial amount requires coverage"
            else overflow(context, caption, WidgetCoverageFontSizeSp, false)?.let { "$code: $it" }
        }
    }

    @Test fun unavailableValueFitsOneLineInEverySupportedLocale() {
        val summary = summariseForWidget(listOf(unknown()), snapshot)
        assertNull(summary.monthlyTotal)
        forEachLocale { code, context ->
            val presentation = widgetPresentation(summary, context)
            assertEquals(context.getString(R.string.overview_unavailable), presentation.value)
            overflow(context, presentation.value, presentation.valueFontSizeSp, true)?.let { "$code: $it" }
        }
    }

    @Test fun unavailableHasNoContradictoryKnownPortionCaption() {
        val summary = summariseForWidget(listOf(unknown()), snapshot)
        assertFalse(summary.estimate.isComplete)
        assertNull(summary.monthlyTotal)
        forEachLocale { code, context ->
            val coverage = widgetPresentation(summary, context).coverage
            if (coverage == null) null else "$code: unavailable amount must not claim a known portion: $coverage"
        }
    }

    @Test fun completeNumericPresentationRemains20SpWithoutCaption() {
        val summary = summariseForWidget(listOf(known()), snapshot)
        assertTrue(summary.estimate.isComplete)
        assertEquals(12.0, summary.monthlyTotal!!, 0.0)
        forEachLocale { _, context ->
            val presentation = widgetPresentation(summary, context)
            assertEquals(CurrencyFormatter.format(12.0, "USD", context.resources.configuration.locales[0]), presentation.value)
            assertEquals(20, presentation.valueFontSizeSp)
            assertNull(presentation.coverage)
            null
        }
    }
}
