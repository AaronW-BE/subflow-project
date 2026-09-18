package org.dpdns.alwaysup.subflow

import org.dpdns.alwaysup.subflow.data.billing.BillingManager
import org.dpdns.alwaysup.subflow.data.billing.BillingManager.Companion.SKU_ANNUAL
import org.dpdns.alwaysup.subflow.data.billing.BillingManager.Companion.SKU_LIFETIME
import org.dpdns.alwaysup.subflow.data.billing.BillingManager.Companion.SKU_MONTHLY
import org.dpdns.alwaysup.subflow.domain.model.ProTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which tier an account is granted when it owns more than one product.
 *
 * Restore used to grant each purchase in turn, so whichever Play listed last
 * won - a Lifetime owner who had not yet cancelled a monthly subscription could
 * be recorded as Monthly.
 */
class HighestTierTest {

    @Test
    fun `Lifetime wins whichever order Play lists the purchases in`() {
        assertEquals(ProTier.LIFETIME, BillingManager.highestTier(listOf(SKU_LIFETIME, SKU_MONTHLY)))
        assertEquals(ProTier.LIFETIME, BillingManager.highestTier(listOf(SKU_MONTHLY, SKU_LIFETIME)))
    }

    @Test
    fun `annual outranks monthly`() {
        assertEquals(ProTier.ANNUAL, BillingManager.highestTier(listOf(SKU_MONTHLY, SKU_ANNUAL)))
    }

    @Test
    fun `unknown products are ignored rather than granted`() {
        assertEquals(ProTier.MONTHLY, BillingManager.highestTier(listOf("some_old_sku", SKU_MONTHLY)))
        assertNull(BillingManager.highestTier(listOf("some_old_sku")))
        assertNull(BillingManager.highestTier(emptyList()))
    }
}
