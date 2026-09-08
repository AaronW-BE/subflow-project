package org.dpdns.alwaysup.subflow.ui.screens.analytics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.domain.util.CurrencyConverter
import org.dpdns.alwaysup.subflow.domain.util.DateCalculators
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One month of the forecast, and how many bills make it up. */
data class ForecastMonth(
    val label: String,
    val amount: Double,
    val charges: Int,
    val isCurrentMonth: Boolean
)

/**
 * What each of the next months is actually billed.
 *
 * Every other number on this screen is smoothed: an annual plan counts as a
 * twelfth of itself every month, which is the honest answer to "what does this
 * cost me" and the wrong answer to "what leaves my account in March". A year of
 * an annual plan arrives on one day. Someone whose average is 40 and whose
 * renewals cluster can be looking at a 300 month without anything in the app
 * having said so.
 *
 * So this walks real bill dates rather than dividing. Each subscription is
 * stepped forward from its first bill by whole cycles - the same walk
 * [DateCalculators.computeNextRenewalDate] does, so a month-end date lands on
 * the same days the rest of the app would pick - and every landing inside the
 * window is added to that month whole.
 */
internal fun buildBillingForecast(
    subs: List<Subscription>,
    primaryCurrency: String,
    locale: Locale,
    today: LocalDate = LocalDate.now(),
    months: Int = 6
): List<ForecastMonth> {
    val monthFormatter = DateTimeFormatter.ofPattern("MMM", locale)
    val windowStart = today.withDayOfMonth(1)
    val lastMonth = windowStart.plusMonths((months - 1).toLong())
    val windowEnd = lastMonth.withDayOfMonth(lastMonth.lengthOfMonth())

    val totals = DoubleArray(months)
    val counts = IntArray(months)

    subs.forEach { sub ->
        // The charge, not the monthly equivalent: this chart exists to undo
        // that division.
        val charge = CurrencyConverter.convert(sub.amount, sub.currency, primaryCurrency)
        var date = DateCalculators.parseOrNull(sub.firstBillDate)
            ?: DateCalculators.parseOrNull(sub.nextBillDate)
            ?: return@forEach

        // Stepping one cycle at a time rather than jumping: plusMonths(1) twice
        // from Jan 31 gives Mar 28, plusMonths(2) gives Mar 31, and the rest of
        // the app takes the first road.
        var guard = 0
        while (date.isBefore(windowStart) && guard < MAX_STEPS) {
            date = DateCalculators.advance(date, sub.cycle)
            guard++
        }
        while (!date.isAfter(windowEnd) && guard < MAX_STEPS) {
            val index = monthIndex(windowStart, date)
            if (index in 0 until months) {
                totals[index] += charge
                counts[index] += 1
            }
            date = DateCalculators.advance(date, sub.cycle)
            guard++
        }
    }

    return (0 until months).map { i ->
        val month = windowStart.plusMonths(i.toLong())
        ForecastMonth(
            label = month.format(monthFormatter),
            amount = totals[i],
            charges = counts[i],
            isCurrentMonth = i == 0
        )
    }
}

/**
 * A weekly plan first billed a decade ago is ~520 steps from today; anything
 * beyond this is a corrupt date, and an unbounded walk on one would hang the
 * analytics screen rather than draw it wrong.
 */
private const val MAX_STEPS = 4000

private fun monthIndex(windowStart: LocalDate, date: LocalDate): Int =
    (date.year - windowStart.year) * 12 + (date.monthValue - windowStart.monthValue)

/**
 * The forecast as bars, with the smoothed monthly average behind them.
 *
 * The dashed line is the whole point of the picture: without something to be
 * taller than, a bar chart of six months just says "these are the months". With
 * it, a renewal month announces itself.
 */
@Composable
internal fun BillingForecastChart(
    forecast: List<ForecastMonth>,
    monthlyAverage: Double,
    modifier: Modifier = Modifier
) {
    if (forecast.isEmpty()) return

    val peak = forecast.maxOf { it.amount }
    // The average can exceed every bar - six months with no annual renewal in
    // them - and a reference line drawn off the top of the chart is worse than
    // no line, so the scale takes both into account.
    val ceiling = maxOf(peak, monthlyAverage).coerceAtLeast(0.0001)

    val reveal by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(700),
        label = "forecastReveal"
    )

    val barColor = MaterialTheme.colorScheme.primary
    val averageColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            val slot = size.width / forecast.size
            val barWidth = slot * 0.52f
            val topPadding = size.height * 0.08f
            val usable = size.height - topPadding

            forecast.forEachIndexed { index, month ->
                val ratio = (month.amount / ceiling).toFloat().coerceIn(0f, 1f) * reveal
                val barHeight = usable * ratio
                // A month with no bill in it is a real answer, and a bar of
                // zero height would read as a rendering fault; leave a stub.
                val drawn = maxOf(barHeight, if (month.amount > 0.0) 2.dp.toPx() else 1.dp.toPx())
                val left = index * slot + (slot - barWidth) / 2f
                drawRoundRect(
                    color = if (month.isCurrentMonth) barColor else barColor.copy(alpha = 0.42f),
                    topLeft = Offset(left, size.height - drawn),
                    size = Size(barWidth, drawn),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
            }

            if (monthlyAverage > 0.0) {
                val y = size.height - usable * (monthlyAverage / ceiling).toFloat().coerceIn(0f, 1f)
                drawLine(
                    color = averageColor.copy(alpha = 0.5f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(6.dp.toPx(), 5.dp.toPx()),
                        0f
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            forecast.forEach { month ->
                Text(
                    text = month.label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = if (month.isCurrentMonth) FontWeight.Bold else FontWeight.Normal,
                    color = if (month.isCurrentMonth) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    }
                )
            }
        }
    }
}
