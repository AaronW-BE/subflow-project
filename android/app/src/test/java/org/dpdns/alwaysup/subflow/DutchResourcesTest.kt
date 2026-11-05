package org.dpdns.alwaysup.subflow

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.dpdns.alwaysup.subflow.domain.util.localeForLanguageCode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DutchResourcesTest {
    private fun dutchContext(): Context {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(context.resources.configuration).apply {
            setLocale(localeForLanguageCode("nl"))
        }
        return context.createConfigurationContext(config)
    }

    @Test
    fun `Dutch resources resolve instead of falling back to English`() {
        val context = dutchContext()
        assertEquals("Instellingen", context.getString(R.string.settings_title))
        assertEquals("Abonnementen", context.getString(R.string.nav_subscriptions))
        assertEquals("Opslaan", context.getString(R.string.save))
    }

    @Test
    fun `Dutch reminder plurals use singular only for one`() {
        val resources = dutchContext().resources
        assertEquals("Nog 1 dag", resources.getQuantityString(R.plurals.renewal_days_left, 1, 1))
        assertEquals("Nog 2 dagen", resources.getQuantityString(R.plurals.renewal_days_left, 2, 2))
        assertEquals("Nog 0 dagen", resources.getQuantityString(R.plurals.renewal_days_left, 0, 0))
    }
}
