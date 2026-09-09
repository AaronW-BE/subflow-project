package org.dpdns.alwaysup.subflow

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.dpdns.alwaysup.subflow.data.remote.SubscriptionDto
import org.dpdns.alwaysup.subflow.domain.model.BillingCycle
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.domain.model.TrialOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The names on the wire, pinned.
 *
 * Sync used to send the domain type through the default Gson instance, which
 * names fields as Kotlin declares them and writes enums with `name()`. Of the
 * sixteen fields only six matched what the server reads; every date, the
 * active flag, the colour, the icon and the update timestamp were dropped in
 * both directions. Nothing failed, because JSON that does not match simply
 * decodes as absent - which is why this is a test rather than a comment.
 *
 * The expected keys below are the `json:` tags on `model.Subscription` in the
 * Go backend. If one side moves, this fails.
 */
class SyncWireFormatTest {

    private val gson = Gson()

    private val sub = Subscription(
        id = "sub_1",
        name = "Claude Pro",
        category = "Productivity",
        amount = 0.0,
        currency = "USD",
        cycle = BillingCycle.MONTHLY,
        firstBillDate = "2026-09-01",
        nextBillDate = "2026-09-19",
        reminderDaysBefore = 1,
        isActive = true,
        colorHex = "#D97757",
        iconUrl = "",
        notes = "note",
        updatedAt = 1_757_000_000_000L,
        isDeleted = false,
        isTrial = true,
        trialEndDate = "2026-09-19",
        trialConverts = true,
        postTrialAmount = 20.0,
        postTrialCycle = BillingCycle.ANNUALLY,
        trialOutcome = TrialOutcome.PENDING
    )

    @Test
    fun `every field is written under the name the server reads`() {
        val json = JsonParser.parseString(gson.toJson(SubscriptionDto.fromDomain(sub))).asJsonObject
        val expected = setOf(
            "id", "name", "category", "amount", "currency", "cycle",
            "first_bill_date", "next_bill_date", "reminder_days_before",
            "is_active", "color_hex", "icon_url", "notes", "updated_at", "is_deleted",
            "is_trial", "trial_end_date", "trial_converts",
            "post_trial_amount", "post_trial_cycle", "trial_outcome"
        )
        assertEquals(expected, json.keySet())
    }

    @Test
    fun `the camelCase names that used to be sent are gone`() {
        val json = JsonParser.parseString(gson.toJson(SubscriptionDto.fromDomain(sub))).asJsonObject
        for (stale in listOf(
            "firstBillDate", "nextBillDate", "reminderDaysBefore",
            "isActive", "colorHex", "iconUrl", "updatedAt", "isDeleted"
        )) {
            assertFalse("still sending $stale", json.has(stale))
        }
    }

    @Test
    fun `a cycle travels as its key, not as the enum constant`() {
        val json = JsonParser.parseString(gson.toJson(SubscriptionDto.fromDomain(sub))).asJsonObject
        // The server compares against "monthly"; "MONTHLY" is a different string
        // and would have been read as an unknown cycle.
        assertEquals("monthly", json["cycle"].asString)
        assertEquals("annually", json["post_trial_cycle"].asString)
    }

    @Test
    fun `a subscription survives the round trip unchanged`() {
        val json = gson.toJson(SubscriptionDto.fromDomain(sub))
        val back = gson.fromJson(json, SubscriptionDto::class.java).toDomain()
        assertEquals(sub, back)
    }

    @Test
    fun `a response missing optional keys decodes instead of crashing`() {
        // Notes carries `omitempty` on the server, so an absent key is normal.
        // Gson builds objects without running the constructor, and a missing
        // key leaves a null behind however non-null the declared type is.
        val lean = """{"id":"sub_2","name":"Netflix","cycle":"monthly"}"""
        val back = gson.fromJson(lean, SubscriptionDto::class.java).toDomain()
        assertEquals("sub_2", back.id)
        assertEquals("Netflix", back.name)
        assertEquals("", back.notes)
        assertEquals("", back.firstBillDate)
        assertEquals(BillingCycle.MONTHLY, back.cycle)
        assertTrue("an unstated trial is not a trial", !back.isTrial)
        assertEquals(TrialOutcome.PENDING, back.trialOutcome)
    }
}
