package org.dpdns.alwaysup.subflow.widget

import android.content.Context
import org.dpdns.alwaysup.subflow.R
import org.dpdns.alwaysup.subflow.domain.util.CurrencyFormatter

/** Rendering choices shared by Glance and native typography coverage; no valuation here. */
internal data class WidgetPresentation(
    val value: String,
    val valueFontSizeSp: Int,
    val coverage: String?
)

internal const val WidgetCoverageFontSizeSp = 10

internal fun widgetPresentation(summary: WidgetSummary, localized: Context): WidgetPresentation {
    val locale = localized.resources.configuration.locales[0]
    return WidgetPresentation(
        value = summary.monthlyTotal?.let { CurrencyFormatter.format(it, summary.currency, locale) }
            ?: localized.getString(R.string.overview_unavailable),
        valueFontSizeSp = if (summary.monthlyTotal != null) 20 else 12,
        coverage = if (summary.monthlyTotal != null && !summary.estimate.isComplete) {
            localized.getString(R.string.widget_partial_coverage)
        } else null
    )
}
