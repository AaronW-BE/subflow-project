package org.dpdns.alwaysup.subflow

import kotlinx.coroutines.runBlocking
import org.dpdns.alwaysup.subflow.data.repository.SubscriptionRepository
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.domain.util.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class BudgetPersistenceTest {
    private fun sub(id: String, active: Boolean = false) = Subscription(id = id, name = id, amount = 20.0,
        firstBillDate = "2020-01-01", nextBillDate = "2020-02-01", isActive = active, updatedAt = 123L)
    private val snapshot = CalculationSnapshot("USD", LocalDate.of(2026, 2, 1), RatesSnapshot(1, mapOf("USD" to 1.0)))

    @Test fun `accepted bulk records match normalization quota subset and exact undo`() = runBlocking {
        val repo = SubscriptionRepository(RuntimeEnvironment.getApplication())
        repo.clearAllData()
        val active = (1..4).map { sub("active$it", true) }
        val paused = listOf(sub("accepted"), sub("refused").copy(currency = "XXX"))
        (active + paused).forEach { assertTrue(repo.saveSubscription(it, true).isSuccess) }
        val before = (active + paused).map { requireNotNull(repo.getById(it.id)) }
        val result = repo.setActiveMany(paused.map { it.id }, true, false)
        assertEquals(1, result.changes.size)
        assertEquals(1, result.refusedByLimit)
        val change = result.changes.single()
        assertEquals(repo.getById(change.updated.id), change.updated)
        assertTrue(change.updated.isActive)
        assertFalse(change.updated.nextBillDate == "2020-02-01")
        val over = Budget.newlyOver(Budget.monthlyTotal(before, snapshot), Budget.monthlyTotal(Budget.replacing(before, result.changes.map { it.updated }), snapshot), MonthlyBudget(90.0, "USD"), snapshot, true)
        assertEquals(10.0, requireNotNull(over).overBy, 0.0)
        result.changes.forEach { assertTrue(repo.revertTo(it.previous, false).isSuccess) }
        assertEquals(change.previous, repo.getById(change.previous.id))
        assertFalse(requireNotNull(repo.getById("refused")).isActive)
    }

    @Test fun `ordinary pending trial resume stays zero while cancelled conversion uses accepted paid cost and undoes all fields`() = runBlocking {
        val repo = SubscriptionRepository(RuntimeEnvironment.getApplication())
        repo.clearAllData()
        val trial = sub("trial").copy(isTrial = true, amount = 0.0, postTrialAmount = 35.0,
            trialEndDate = "2020-02-01", currency = "XXX")
        assertTrue(repo.saveSubscription(trial, true).isSuccess)
        val pending = repo.setActive("trial", true, true).getOrThrow()
        assertEquals(repo.getById("trial"), pending.updated)
        assertTrue(Budget.monthlyTotal(listOf(pending.updated), snapshot).isComplete)
        assertEquals(0.0, Budget.monthlyTotal(listOf(pending.updated), snapshot).knownAmount, 0.0)
        repo.saveSubscription(Trials.cancel(pending.updated), true).getOrThrow()
        val converted = repo.setActive("trial", true, true).getOrThrow()
        assertTrue(converted.convertedToPaid)
        assertEquals(35.0, converted.updated.amount, 0.0)
        assertEquals(repo.getById("trial"), converted.updated)
        assertFalse(Budget.monthlyTotal(listOf(converted.updated), snapshot).isComplete)
        repo.revertTo(converted.previous, true).getOrThrow()
        assertEquals(converted.previous, repo.getById("trial"))
    }
}
