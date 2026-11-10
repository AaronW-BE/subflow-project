package org.dpdns.alwaysup.subflow.data.repository

import org.dpdns.alwaysup.subflow.domain.model.BillingCycle
import org.dpdns.alwaysup.subflow.domain.model.PresetService
import org.junit.Assert.assertEquals
import org.junit.Test

class PresetCatalogueTest {
    @Test
    fun `legacy remote entertainment category is normalized before display`() {
        val remote = PresetService(
            id = "remote",
            name = "Remote service",
            category = "Entertainment",
            brandColor = "#000000",
            iconUrl = "https://example.com/logo.png",
            defaultCycle = BillingCycle.MONTHLY,
            defaultAmountUSD = 10.0
        )

        assertEquals("Streaming", normalizePresetCategories(listOf(remote)).single().category)
    }

    @Test
    fun `case differences in legacy category are normalized`() {
        val remote = PresetService(
            id = "remote-lower",
            name = "Remote service",
            category = "entertainment",
            brandColor = "#000000",
            iconUrl = "",
            defaultAmountUSD = 10.0
        )

        assertEquals("Streaming", normalizePresetCategories(listOf(remote)).single().category)
    }
}
