package org.dpdns.alwaysup.subflow

import org.dpdns.alwaysup.subflow.domain.model.BillingCycle
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.ui.screens.dashboard.SUBSCRIPTION_CATEGORIES
import org.dpdns.alwaysup.subflow.ui.screens.dashboard.categoryFilters
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * The dashboard's category chips: one for each category in use, so a
 * subscription filed under any of the add form's categories can be filtered to.
 */
class CategoryFiltersTest {

    private fun sub(
        category: String,
        active: Boolean = true,
        deleted: Boolean = false
    ) = Subscription(
        id = category.lowercase(),
        name = category,
        category = category,
        amount = 10.0,
        currency = "USD",
        cycle = BillingCycle.MONTHLY,
        firstBillDate = LocalDate.now().minusMonths(3).toString(),
        nextBillDate = LocalDate.now().plusDays(10).toString(),
        isActive = active,
        isDeleted = deleted
    )

    @Test
    fun `no subscriptions leaves only All`() {
        assertEquals(listOf("All"), categoryFilters(emptyList(), "All"))
    }

    @Test
    fun `every category the add form offers gets a chip once it is used`() {
        val subs = SUBSCRIPTION_CATEGORIES.map { sub(it) }

        assertEquals(listOf("All") + SUBSCRIPTION_CATEGORIES, categoryFilters(subs, "All"))
    }

    @Test
    fun `chips follow the add form's order, not the order subscriptions were added`() {
        val subs = listOf(sub("News"), sub("Streaming"), sub("Finance"))

        assertEquals(listOf("All", "Streaming", "Finance", "News"), categoryFilters(subs, "All"))
    }

    @Test
    fun `a paused subscription keeps its category's chip`() {
        val subs = listOf(sub("Gaming", active = false))

        assertEquals(listOf("All", "Gaming"), categoryFilters(subs, "All"))
    }

    @Test
    fun `a deleted subscription does not`() {
        val subs = listOf(sub("Gaming", deleted = true))

        assertEquals(listOf("All"), categoryFilters(subs, "All"))
    }

    @Test
    fun `the selected chip stays after its last subscription goes`() {
        assertEquals(listOf("All", "Education"), categoryFilters(emptyList(), "Education"))
    }

    @Test
    fun `legacy Entertainment shows up as Streaming`() {
        assertEquals(listOf("All", "Streaming"), categoryFilters(listOf(sub("Entertainment")), "All"))
    }

    @Test
    fun `stored categories match regardless of case`() {
        assertEquals(listOf("All", "Lifestyle"), categoryFilters(listOf(sub("lifestyle")), "All"))
    }
}
