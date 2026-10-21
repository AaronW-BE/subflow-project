package org.dpdns.alwaysup.subflow.ui

import android.content.Context
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.dpdns.alwaysup.subflow.R
import org.dpdns.alwaysup.subflow.data.billing.BillingConnectionState
import org.dpdns.alwaysup.subflow.data.billing.ProPlan
import org.dpdns.alwaysup.subflow.domain.model.ProTier
import org.dpdns.alwaysup.subflow.ui.screens.paywall.PaywallReason
import org.dpdns.alwaysup.subflow.ui.screens.paywall.PaywallScreen
import org.dpdns.alwaysup.subflow.ui.theme.SubFlowTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The paywall leads with what was tapped, lists only what Pro adds, and
 * names the day a trial turns into a charge (BIN-74).
 *
 * Prices here are made up for the test; the screen itself only ever shows
 * what Play's ProductDetails returns.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h2400dp")
class PaywallScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.of(2026, 10, 21)

    private val annual = ProPlan(
        productId = "pro_annual", tier = ProTier.ANNUAL, formattedPrice = "$9.99",
        priceAmountMicros = 9_990_000, priceCurrencyCode = "USD", offerToken = "t", freeTrialDays = 7
    )
    private val monthly = ProPlan(
        productId = "pro_monthly", tier = ProTier.MONTHLY, formattedPrice = "$1.99",
        priceAmountMicros = 1_990_000, priceCurrencyCode = "USD", offerToken = "t", freeTrialDays = 0
    )

    private fun show(
        reason: PaywallReason = PaywallReason.GENERAL,
        tracked: Int = 0,
        isPro: Boolean = false
    ) = compose.setContent {
        SubFlowTheme {
            PaywallScreen(
                plans = mapOf(ProTier.ANNUAL to annual, ProTier.MONTHLY to monthly),
                connectionState = BillingConnectionState.READY,
                catalogueLoaded = true,
                isPro = isPro,
                proTier = if (isPro) ProTier.ANNUAL else ProTier.FREE,
                onDismiss = {},
                onPurchaseTier = {},
                onRestorePurchases = {},
                onManageSubscription = {},
                onRetry = {},
                onOpenUrl = {},
                reason = reason,
                trackedCount = tracked,
                today = today
            )
        }
    }

    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

    private fun top(id: Int) =
        compose.onNodeWithText(text(id)).getUnclippedBoundsInRoot().top

    @Test
    fun `opened from the header, earlier alerts lead`() {
        show()
        compose.onNodeWithText(text(R.string.paywall_headline)).assertExists()
        assertTrue(top(R.string.feat_push_alerts) < top(R.string.feat_unlimited))
        assertTrue(top(R.string.feat_unlimited) < top(R.string.feat_budget))
    }

    @Test
    fun `opened from the budget row, the budget leads`() {
        show(reason = PaywallReason.BUDGET)
        compose.onNodeWithText(text(R.string.paywall_headline_budget)).assertExists()
        assertTrue(top(R.string.feat_budget) < top(R.string.feat_push_alerts))
    }

    @Test
    fun `at the limit, it says the limit and the user's own count`() {
        show(reason = PaywallReason.LIMIT, tracked = 5)
        compose.onNodeWithText(text(R.string.paywall_headline_limit, 5)).assertExists()
        compose.onNodeWithText(text(R.string.feat_unlimited_used, 5, 5)).assertExists()
        assertTrue(top(R.string.feat_unlimited) < top(R.string.feat_push_alerts))
    }

    @Test
    fun `the trial is said as the date it is charged`() {
        show()
        val date = LocalDate.of(2026, 10, 28)
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))
        compose.onNodeWithText(
            text(R.string.trial_footnote_until, date, text(R.string.plan_per_year, "$9.99"))
        ).assertExists()
    }

    @Test
    fun `what stays free is said, and is not sold`() {
        show()
        compose.onNodeWithText(text(R.string.paywall_free_for_all)).assertExists()
    }

    @Test
    fun `a Pro user is not told their free-plan count`() {
        show(tracked = 12, isPro = true)
        compose.onNodeWithText(text(R.string.feat_unlimited_used, 12, 5)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.feat_unlimited_sub)).assertExists()
    }
}
