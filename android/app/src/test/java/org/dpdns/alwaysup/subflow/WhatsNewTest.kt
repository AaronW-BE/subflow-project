package org.dpdns.alwaysup.subflow

import org.dpdns.alwaysup.subflow.data.preferences.PreferencesManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the what's-new sheet is owed.
 *
 * The rule has to separate an install that updated from one that was just
 * installed, because the second gets onboarding and would otherwise be told
 * what is "new" about the only version it has ever had.
 */
class WhatsNewTest {

    private fun show(
        lastSeen: Int,
        current: Int = 9,
        launchCount: Int = 5,
        onboardingComplete: Boolean = true
    ) = PreferencesManager.shouldShowWhatsNew(lastSeen, current, launchCount, onboardingComplete)

    @Test
    fun `an install that updated is shown the sheet`() {
        assertTrue(show(lastSeen = 8))
    }

    @Test
    fun `an install updating into the first build with the key is shown it too`() {
        // Everyone on 1.4.0 has no stored version, which is what 0 means here.
        assertTrue(show(lastSeen = 0))
    }

    @Test
    fun `a first launch is not`() {
        assertFalse(show(lastSeen = 0, launchCount = 1))
    }

    @Test
    fun `nothing is shown before onboarding is finished`() {
        assertFalse(show(lastSeen = 8, onboardingComplete = false))
    }

    @Test
    fun `the same version is not announced twice`() {
        assertFalse(show(lastSeen = 9))
    }

    @Test
    fun `a downgrade announces nothing`() {
        assertFalse(show(lastSeen = 10))
    }
}
