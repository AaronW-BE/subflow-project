package org.dpdns.alwaysup.subflow

import org.dpdns.alwaysup.subflow.data.repository.SubscriptionRepository
import org.dpdns.alwaysup.subflow.domain.model.BillingCycle
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.ui.screens.dashboard.DashboardSortOrder
import org.dpdns.alwaysup.subflow.ui.screens.dashboard.filterAndSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Pausing a subscription: what the dashboard shows, and where the free tier
 * stops it.
 *
 * The dashboard runs the active and the paused list through one call, so a
 * search finds a subscription in either state. These tests are what keeps the
 * two from drifting apart.
 */
class PausedSubscriptionsTest {

    private fun sub(
        name: String,
        active: Boolean = true,
        category: String = "Streaming",
        amount: Double = 10.0,
        daysAway: Long = 10,
        notes: String = ""
    ) = Subscription(
        id = name.lowercase(),
        name = name,
        category = category,
        amount = amount,
        currency = "USD",
        cycle = BillingCycle.MONTHLY,
        firstBillDate = LocalDate.now().minusMonths(3).toString(),
        nextBillDate = LocalDate.now().plusDays(daysAway).toString(),
        isActive = active,
        notes = notes
    )

    private fun List<Subscription>.names(
        category: String = "All",
        query: String = "",
        order: DashboardSortOrder = DashboardSortOrder.NAME_ASC
    ) = filterAndSort(category, query, order, "USD").map { it.name }

    // ------------------------------------------------------------ the lists

    @Test
    fun `a search finds a subscription whether it is active or paused`() {
        val all = listOf(sub("Netflix", active = false), sub("Spotify"))
        val active = all.filter { it.isActive }
        val paused = all.filter { !it.isActive }

        assertEquals(emptyList<String>(), active.names(query = "netflix"))
        assertEquals(listOf("Netflix"), paused.names(query = "netflix"))
    }

    @Test
    fun `the category chips apply to the paused list too`() {
        val paused = listOf(
            sub("Netflix", active = false, category = "Streaming"),
            sub("Notion", active = false, category = "Productivity")
        )
        assertEquals(listOf("Netflix"), paused.names(category = "Streaming"))
        assertEquals(listOf("Netflix", "Notion"), paused.names())
    }

    @Test
    fun `Entertainment still answers to the Streaming chip`() {
        val subs = listOf(sub("Netflix", category = "Entertainment"))
        assertEquals(listOf("Netflix"), subs.names(category = "Streaming"))
    }

    @Test
    fun `sorting is unchanged by the extraction`() {
        val subs = listOf(
            sub("Spotify", amount = 5.0, daysAway = 30),
            sub("Netflix", amount = 20.0, daysAway = 2)
        )
        assertEquals(listOf("Netflix", "Spotify"), subs.names(order = DashboardSortOrder.RENEWAL_DATE))
        assertEquals(listOf("Netflix", "Spotify"), subs.names(order = DashboardSortOrder.PRICE_HIGH))
        assertEquals(listOf("Spotify", "Netflix"), subs.names(order = DashboardSortOrder.PRICE_LOW))
        assertEquals(listOf("Netflix", "Spotify"), subs.names(order = DashboardSortOrder.NAME_ASC))
    }

    @Test
    fun `notes are searchable, and a blank query keeps everything`() {
        val subs = listOf(sub("Netflix", notes = "shared with mum"), sub("Spotify"))
        assertEquals(listOf("Netflix"), subs.names(query = "mum"))
        assertEquals(listOf("Netflix", "Spotify"), subs.names(query = "   "))
    }

    // -------------------------------------------------------- the free tier

    @Test
    fun `resuming a sixth subscription is where the free tier ends`() {
        // Five active plus a paused sixth is a legal state: the paused one is
        // not counted. Resuming it is the moment it stops being legal.
        assertTrue(SubscriptionRepository.resumeExceedsFreeTier(isPro = false, activeCount = 5))
        assertFalse(SubscriptionRepository.resumeExceedsFreeTier(isPro = false, activeCount = 4))
    }

    @Test
    fun `Pro resumes as many as it likes`() {
        assertFalse(SubscriptionRepository.resumeExceedsFreeTier(isPro = true, activeCount = 5))
        assertFalse(SubscriptionRepository.resumeExceedsFreeTier(isPro = true, activeCount = 50))
    }
}
