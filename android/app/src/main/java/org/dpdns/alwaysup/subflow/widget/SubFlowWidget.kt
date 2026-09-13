package org.dpdns.alwaysup.subflow.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.dpdns.alwaysup.subflow.MainActivity
import org.dpdns.alwaysup.subflow.R
import org.dpdns.alwaysup.subflow.data.local.SubFlowDatabase
import org.dpdns.alwaysup.subflow.data.preferences.PreferencesManager
import org.dpdns.alwaysup.subflow.data.repository.ExchangeRateRepository
import org.dpdns.alwaysup.subflow.domain.util.CurrencyFormatter

/*
 * The home screen widget: what this month costs, and what renews when.
 *
 * It reads Room and SharedPreferences directly. The widget is rendered from a
 * broadcast, with no Activity and no ViewModel alive, so anything that assumes
 * one would either crash or quietly render an empty state.
 *
 * It is not gated on Pro. It shows what the dashboard already shows to
 * everyone, and a locked widget sitting on someone's home screen is an advert,
 * not a feature.
 */

// Glance never sees the Compose theme, so the widget has its own palette. It
// is held as colour resources rather than literals so values-night resolves it
// the same way the rest of the app is themed - there is no "is it dark" check
// anywhere below, and none should be. The values match ui/theme/Color.kt.
private val WidgetSurface = ColorProvider(R.color.widget_surface)
private val WidgetInk = ColorProvider(R.color.widget_ink)
private val WidgetInkDim = ColorProvider(R.color.widget_ink_dim)
private val WidgetAccent = ColorProvider(R.color.widget_accent)

/** Below this the widget is one cell tall, and the total is all that fits. */
private val ListThreshold = 100.dp

class SubFlowWidget : GlanceAppWidget() {

    // Short: the total alone. Tall: the total over a scrolling list of every
    // active subscription. Responsive rather than Exact so the launcher picks
    // by the size the user dragged it to, without a second provider.
    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(180.dp, 60.dp), DpSize(180.dp, 110.dp))
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // The rates the app last cached. Without this the widget would fall
        // back to the built-in table and disagree with the dashboard by a few
        // percent on any multi-currency account.
        ExchangeRateRepository(context).primeFromCache()

        val subscriptions = SubFlowDatabase.getDatabase(context)
            .subscriptionDao()
            .getActiveSubscriptions()
            .map { it.toDomain() }
        val currency = PreferencesManager.readCurrencyStatic(context)
        val summary = summariseForWidget(subscriptions, currency)

        provideContent { WidgetBody(summary) }
    }
}

@Composable
private fun WidgetBody(summary: WidgetSummary) {
    val context = LocalContext.current
    val tall = LocalSize.current.height >= ListThreshold

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetSurface)
            .cornerRadius(16.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = context.getString(R.string.total_monthly_spend),
            style = TextStyle(color = WidgetInkDim, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        )
        Spacer(modifier = GlanceModifier.height(2.dp))

        if (summary.activeCount == 0) {
            Text(
                text = context.getString(R.string.empty_title),
                style = TextStyle(color = WidgetInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            )
            return@Column
        }

        Text(
            text = CurrencyFormatter.format(summary.monthlyTotal, summary.currency),
            style = TextStyle(color = WidgetInk, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        )

        if (!tall) return@Column

        Spacer(modifier = GlanceModifier.height(6.dp))
        // The list takes whatever height is left and scrolls past it, so a
        // widget dragged taller shows more rows rather than more blank space.
        LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            items(summary.upcoming, itemId = { it.id.hashCode().toLong() }) { renewal ->
                RenewalRow(renewal)
            }
        }
    }
}

@Composable
private fun RenewalRow(renewal: UpcomingRenewal) {
    val context = LocalContext.current
    val days = renewal.daysLeft
    val whenText = when {
        days < 0L -> context.getString(
            if (renewal.isTrial) R.string.trial_ended else R.string.renewal_overdue
        )
        days == 0L -> context.getString(
            if (renewal.isTrial) R.string.trial_ends_today else R.string.renewal_today
        )
        else -> context.resources.getQuantityString(
            if (renewal.isTrial) R.plurals.trial_days_left else R.plurals.renewal_days_left,
            days.toInt(),
            days.toInt()
        )
    }
    // Within three days is when a reminder would fire; the same threshold
    // colours the dashboard's rows.
    val soon = days <= 3L
    val price = if (renewal.isTrial) {
        context.getString(R.string.trial_free_amount)
    } else {
        CurrencyFormatter.format(renewal.amount, renewal.currency)
    }

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            // Straight to the subscription, through the deep link the renewal
            // notification already uses.
            .clickable(
                actionStartActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("subflow://subscription/${renewal.id}")
                    ).setComponent(ComponentName(context, MainActivity::class.java))
                )
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = renewal.name,
                maxLines = 1,
                style = TextStyle(color = WidgetInk, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            )
            Text(
                text = whenText,
                maxLines = 1,
                style = TextStyle(
                    color = if (soon) WidgetAccent else WidgetInkDim,
                    fontSize = 11.sp,
                    fontWeight = if (soon) FontWeight.Medium else FontWeight.Normal
                )
            )
        }
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = price,
            maxLines = 1,
            style = TextStyle(
                color = if (renewal.isTrial) WidgetAccent else WidgetInk,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

class SubFlowWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SubFlowWidget()
}

private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Redraws every placed widget.
 *
 * Called from the write path in the repository and from the currency setting,
 * because those are the things that change what it says. The day rolling over
 * is left to `updatePeriodMillis` in the provider XML: nothing in the app is
 * running at midnight to notice it.
 *
 * Fire-and-forget on purpose - no caller has anything useful to do with the
 * result, and a save must not wait on a launcher. Safe when no widget is
 * placed: `updateAll` is a no-op then.
 */
fun refreshSubFlowWidget(context: Context) {
    val app = context.applicationContext
    widgetScope.launch { runCatching { SubFlowWidget().updateAll(app) } }
}
