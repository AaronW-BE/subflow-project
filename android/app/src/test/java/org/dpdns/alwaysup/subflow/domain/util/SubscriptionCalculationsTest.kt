package org.dpdns.alwaysup.subflow.domain.util

import org.dpdns.alwaysup.subflow.domain.model.BillingCycle
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.domain.model.TrialOutcome
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SubscriptionCalculationsTest {
    private val today = LocalDate.of(2026, 9, 10)
    private fun snapshot(rates: Map<String, Double> = mapOf("USD" to 1.0, "EUR" to 0.8)) =
        CalculationSnapshot("USD", today, RatesSnapshot(1, rates))
    private fun sub(id: String = "a", amount: Double = 10.0, currency: String = "USD") =
        Subscription(id, "Service", amount = amount, currency = currency,
            firstBillDate = "2026-01-01", nextBillDate = today.toString(), updatedAt = 0)
    private fun available(result: ConversionResult): Double = (result as ConversionResult.Available).amount
    private fun failure(result: ConversionResult) = (result as ConversionResult.Unavailable).reason

    @Test fun conversionUsesUnitsPerUsdInBothDirectionsAndNormalizesCase() {
        val rates = snapshot().rates
        assertEquals(10.0, available(convertAmount(8.0, "eur", "usd", rates)), 0.0)
        assertEquals(8.0, available(convertAmount(10.0, "USD", "EUR", rates)), 0.0)
    }

    @Test fun zeroAndSameCurrencyDoNotRequireRates() {
        val rates = RatesSnapshot(0, emptyMap())
        assertEquals(7.0, available(convertAmount(7.0, "xxx", "XXX", rates)), 0.0)
        assertEquals(0.0, available(convertAmount(0.0, "XXX", "USD", rates)), 0.0)
        assertEquals(ConversionFailure.INVALID_AMOUNT, failure(convertAmount(Double.NaN, "USD", "USD", rates)))
    }

    @Test fun missingSourceAndTargetAreExplicit() {
        assertEquals(ConversionFailure.MISSING_SOURCE_RATE, failure(convertAmount(1.0, "XXX", "USD", snapshot().rates)))
        assertEquals(ConversionFailure.MISSING_TARGET_RATE, failure(convertAmount(1.0, "USD", "XXX", snapshot().rates)))
    }

    @Test fun invalidRatesNeverBecomeFallbackAmounts() {
        for (bad in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            val rates = RatesSnapshot(0, mapOf("USD" to 1.0, "BAD" to bad))
            assertEquals(ConversionFailure.INVALID_SOURCE_RATE, failure(convertAmount(1.0, "BAD", "USD", rates)))
            assertEquals(ConversionFailure.INVALID_TARGET_RATE, failure(convertAmount(1.0, "USD", "BAD", rates)))
        }
    }

    @Test fun snapshotCopiesAndProtectsInputMapAndMetadata() {
        val input = mutableMapOf("eur" to 0.8)
        val rates = RatesSnapshot(42, input, 123L, "Provider", "https://example.com")
        input["eur"] = 99.0
        input["USD"] = 1.0
        assertEquals(mapOf("EUR" to 0.8), rates.ratesPerUsd)
        assertEquals(42L, rates.revision)
        assertEquals(123L, rates.quotedAtEpochMillis)
        assertEquals("Provider", rates.providerName)
        assertEquals("https://example.com", rates.providerUrl)
        try {
            (rates.ratesPerUsd as MutableMap)["EUR"] = 10.0
            fail("Snapshot must reject mutation")
        } catch (_: UnsupportedOperationException) { }
    }

    @Test fun emptyFreeAndPendingTrialAreCompleteZero() {
        for (records in listOf(emptyList(), listOf(sub(amount = 0.0, currency = "XXX")),
            listOf(sub(amount = Double.NaN, currency = "XXX").copy(isTrial = true)))) {
            val estimate = calculateOverview(records, snapshot()).currentMonthlyEstimate
            assertTrue(estimate.isComplete)
            assertEquals(0.0, estimate.displayAmount!!, 0.0)
            assertEquals(0, estimate.convertiblePositiveCount)
        }
    }

    @Test fun unknownPositiveCostIsNotZeroEvenWithFreeRecords() {
        val estimate = calculateOverview(listOf(sub(currency = "xxx"), sub("free", 0.0)), snapshot()).currentMonthlyEstimate
        assertFalse(estimate.isComplete)
        assertNull(estimate.displayAmount)
        assertEquals(0.0, estimate.knownAmount, 0.0)
        assertEquals(listOf("a"), estimate.missingSubscriptionIds)
        assertEquals(setOf("XXX"), estimate.missingCurrencies)
    }

    @Test fun mixedKnownPartialShowsOnlyKnownContribution() {
        val estimate = calculateOverview(listOf(sub(), sub("unknown", currency = "XXX")), snapshot()).currentMonthlyEstimate
        assertFalse(estimate.isComplete)
        assertEquals(10.0, estimate.displayAmount!!, 0.0)
        assertEquals(1, estimate.convertiblePositiveCount)
    }

    @Test fun futureTrialCostCanFailIndependentlyOfCurrentZero() {
        val trial = sub(currency = "XXX").copy(isTrial = true, trialEndDate = "2026-09-11", postTrialAmount = 20.0)
        val overview = calculateOverview(listOf(trial), snapshot())
        assertTrue(overview.currentMonthlyEstimate.isComplete)
        assertEquals(0.0, overview.currentMonthlyEstimate.displayAmount!!, 0.0)
        val forecast = aggregateContributions(listOf(normalizeContribution(trial.id, trial.postTrialAmount, trial.currency, snapshot(), trial.postTrialCycle)))
        assertFalse(forecast.isComplete)
        assertNull(forecast.displayAmount)
        assertEquals(LocalDate.of(2026, 9, 11), overview.upcomingEvents.single().date)
    }

    @Test fun inventoryIncludesPausedButNotDeletedAndOnlyActivePendingTrials() {
        val records = listOf(sub(), sub("paused").copy(isActive = false),
            sub("kept").copy(isTrial = true, trialEndDate = "2026-09-01", trialKeepEndDate = "2026-09-01"),
            sub("pausedTrial").copy(isTrial = true, isActive = false),
            sub("deleted").copy(isDeleted = true),
            sub("converted").copy(isTrial = true, trialOutcome = TrialOutcome.CONVERTED))
        val overview = calculateOverview(records, snapshot())
        assertEquals(InventoryCounts(5, 3, 2, 1), overview.counts)
        assertEquals(20.0, overview.currentMonthlyEstimate.knownAmount, 0.0)
        assertEquals(5, overview.normalizedPrices.size)
        assertEquals(10.0, available(overview.normalizedPrices.first { it.subscriptionId == "paused" }.monthlyPrice), 0.0)
        assertFalse(overview.upcomingEvents.any { it.subscriptionId == "kept" })
    }

    @Test fun upcomingDatesAreValidFutureOrTodayWithStableNameIdTies() {
        val records = listOf(sub("b"), sub("a"), sub("early").copy(name = "Alpha"),
            sub("past").copy(nextBillDate = "2026-09-09"),
            sub("invalid").copy(nextBillDate = "2026-02-30"),
            sub("future").copy(nextBillDate = "2026-09-11"),
            sub("trial").copy(isTrial = true, trialEndDate = "invalid"),
            sub("paused").copy(isActive = false), sub("deleted").copy(isDeleted = true))
        assertEquals(listOf("early", "a", "b", "future"), calculateOverview(records, snapshot()).upcomingEvents.map { it.subscriptionId })
    }

    @Test fun eventRetainsOriginalCyclePriceCurrencyAndDoesNotRollDates() {
        val record = sub(amount = 48.0, currency = "eur").copy(cycle = BillingCycle.ANNUALLY)
        val event = calculateOverview(listOf(record), snapshot()).upcomingEvents.single()
        assertEquals(48.0, event.amount, 0.0)
        assertEquals("eur", event.currency)
        assertEquals(BillingCycle.ANNUALLY, event.cycle)
        assertEquals(today, event.date)
    }

    @Test fun cyclesUseExistingMonthlyFormula() {
        for (cycle in BillingCycle.entries) {
            val overview = calculateOverview(listOf(sub().copy(cycle = cycle)), snapshot())
            assertEquals(cycle.toMonthly(10.0), overview.currentMonthlyEstimate.knownAmount, 0.0)
        }
    }

    @Test fun repeatedNamesAndEvenDuplicateInputIdsAreNotDeduplicated() {
        val overview = calculateOverview(listOf(sub(), sub()), snapshot())
        assertEquals(2, overview.counts.total)
        assertEquals(20.0, overview.currentMonthlyEstimate.knownAmount, 0.0)
        assertEquals(2, overview.normalizedPrices.size)
        assertEquals(2, overview.upcomingEvents.size)
    }

    @Test fun positiveAmountsMustNotUnderflowIntoFreeCosts() {
        val rates = RatesSnapshot(0, mapOf("USD" to 1.0, "EUR" to 0.5))
        assertEquals(ConversionFailure.UNDERFLOW,
            failure(convertAmount(Double.MIN_VALUE, "USD", "EUR", rates)))
        val contribution = normalizeContribution("tiny", Double.MIN_VALUE, "USD", snapshot(), BillingCycle.ANNUALLY)
        assertEquals(ConversionFailure.UNDERFLOW, failure(contribution.result))
        assertNull(aggregateContributions(listOf(contribution)).displayAmount)
    }

    @Test fun invalidAmountsAndOverflowRemainExplicitAndFinite() {
        for (amount in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertEquals(ConversionFailure.INVALID_AMOUNT,
                failure(normalizeContribution("a", amount, "USD", snapshot()).result))
        }
        val rates = RatesSnapshot(0, mapOf("USD" to 1.0, "EUR" to 2.0))
        assertEquals(ConversionFailure.OVERFLOW,
            failure(convertAmount(Double.MAX_VALUE, "USD", "EUR", rates)))
        assertEquals(ConversionFailure.OVERFLOW,
            failure(normalizeContribution("a", Double.MAX_VALUE, "USD", snapshot(), BillingCycle.WEEKLY).result))
        val estimate = calculateOverview(listOf(sub("a", Double.MAX_VALUE), sub("b", Double.MAX_VALUE)), snapshot()).currentMonthlyEstimate
        assertFalse(estimate.isComplete)
        assertTrue(estimate.knownAmount.isFinite())
        assertEquals(ConversionFailure.OVERFLOW, estimate.missing.single().reason)
        assertEquals(1, estimate.convertiblePositiveCount)
    }
}
