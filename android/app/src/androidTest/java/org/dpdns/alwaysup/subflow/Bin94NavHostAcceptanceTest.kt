package org.dpdns.alwaysup.subflow

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.dpdns.alwaysup.subflow.data.ads.AdsConsentManager
import org.dpdns.alwaysup.subflow.data.billing.BillingManager
import org.dpdns.alwaysup.subflow.data.preferences.PreferencesManager
import org.dpdns.alwaysup.subflow.data.repository.AuthRepository
import org.dpdns.alwaysup.subflow.data.repository.SubscriptionRepository
import org.dpdns.alwaysup.subflow.domain.model.*
import org.dpdns.alwaysup.subflow.domain.util.MonthlyBudget
import org.dpdns.alwaysup.subflow.ui.navigation.Screen
import org.dpdns.alwaysup.subflow.ui.navigation.SubFlowNavHost
import org.dpdns.alwaysup.subflow.ui.screens.settings.SettingsPage
import org.dpdns.alwaysup.subflow.ui.theme.SubFlowTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.security.MessageDigest

/** Destructive ONLY to the explicitly authorized disposable Debug package. No Play ownership claim. */
@RunWith(AndroidJUnit4::class)
class Bin94NavHostAcceptanceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var context: Context
    private lateinit var repository: SubscriptionRepository
    private lateinit var preferences: PreferencesManager
    private lateinit var controlledAuth: AuthRepository
    private lateinit var billingAuth: AuthRepository
    private lateinit var billing: BillingManager
    private val billingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @get:Rule val testName = org.junit.rules.TestName()
    private lateinit var nav: NavHostController
    private var scenario: ActivityScenario<MainActivity>? = null
    private var intentsStarted = false
    private val today get() = LocalDate.now()
    private fun text(id: Int) = context.getString(id)

    @Before fun installControlledNavHost() {
        context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        // MUST precede every mutation, including preferences and Activity startup.
        assertEquals("org.dpdns.alwaysup.subflow.debug", BuildConfig.APPLICATION_ID)
        assertEquals(BuildConfig.APPLICATION_ID, context.packageName)
        assertTrue("Physical device must already be awake", (context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isInteractive)
        assertFalse("User must unlock the device before acceptance", (context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isKeyguardLocked)
        preferences = PreferencesManager(context)
        preferences.restartOnboarding() // original composition must not gather consent/request permissions
        preferences.setCurrency("USD")
        preferences.setMonthlyBudget(null)
        preferences.markSwipeHintSeen()
        preferences.consumeWhatsNew(BuildConfig.VERSION_CODE)
        // Explicit synthetic local guest: avoid invoking a sign-in/provider action from either host.
        context.getSharedPreferences("subflow_auth_prefs", Context.MODE_PRIVATE).edit().clear()
            .putString("user_id", "BIN94_SYNTHETIC_GUEST")
            .putString("auth_provider", "guest").putBoolean("is_pro", false).commit()
        repository = SubscriptionRepository(context)
        runBlocking { repository.clearAllData() }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        // onActivity runs after onCreate: MainActivity's own BillingManager captured FREE auth.
        scenario!!.onActivity { activity ->
            billingAuth = AuthRepository(context).also { it.updateProStatus(false) }
            billing = BillingManager(context, billingAuth, billingScope) // deliberately not connected, never purchases
            controlledAuth = AuthRepository(context).also { it.updateProStatus(true, ProTier.ANNUAL) }
            activity.setContent {
                SubFlowTheme {
                    nav = rememberNavController()
                    SubFlowNavHost(nav, repository, controlledAuth, preferences, billing, AdsConsentManager(context))
                }
            }
        }
        preferences.completeOnboarding() // only the replacement composition observes this instance
        compose.waitForIdle()
        assertTrue(controlledAuth.isPro.value)
        assertFalse(billingAuth.isPro.value)
    }

    private fun record(id: String, amount: Double = 12.0, currency: String = "USD", active: Boolean = true) =
        Subscription(id, id, amount = amount, currency = currency, firstBillDate = today.minusMonths(2).toString(),
            nextBillDate = today.plusDays(7).toString(), reminderDaysBefore = 0, isActive = active)

    private fun seedInventory() = runBlocking {
        val trialDate = today.minusDays(1).toString()
        listOf(record("USD"), record("Quoted, \"name\"", 10.0).copy(notes = "line one\nline \"two\""),
            record("MissingFX", 9.0, "ZZZ"), record("Free", 0.0),
            record("Pending", 0.0).copy(isTrial = true, trialEndDate = trialDate,
                nextBillDate = trialDate, trialKeepEndDate = trialDate, postTrialAmount = 20.0),
            record("Paused", 8.0, active = false), record("Deleted").copy(isDeleted = true)
        ).forEach { repository.saveSubscription(it, isPro = true).getOrThrow() }
    }

    private fun navigate(route: String) {
        compose.runOnIdle { nav.navigate(route) }
        compose.waitForIdle()
    }
    private fun visible(id: Int) = compose.onAllNodesWithText(text(id)).onFirst().performScrollTo().assertIsDisplayed()

    private fun awaitDisplayed(value: String) {
        compose.waitUntil(10_000) {
            runCatching { compose.onNodeWithText(value).assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithText(value).assertIsDisplayed()
    }
    private fun persistedBudget() = PreferencesManager(context).monthlyBudget.value

    @Test fun globalInventoryAndExplicitPartialSurviveSubsetNavigation() {
        seedInventory()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("6\n${text(R.string.overview_all)}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("5\n${text(R.string.overview_active)}").assertExists()
        compose.onNodeWithText("1\n${text(R.string.overview_paused)}").assertExists()
        compose.onNodeWithText(context.getString(R.string.overview_pending, 1)).assertExists()
        visible(R.string.overview_partial)
        compose.onNodeWithText("1\n${text(R.string.overview_paused)}").performScrollTo().performClick()
        compose.onNodeWithText("1\n${text(R.string.overview_paused)}").assertExists() // global inventory survives filtering
        compose.onNodeWithText(text(R.string.overview_reset)).performScrollTo().performClick()
        compose.onNodeWithText("6\n${text(R.string.overview_all)}").assertExists()
    }

    @Test fun nativeBudgetDialogFreezesOriginalCurrencyAndPersistsOnlyConfirmation() {
        preferences.setMonthlyBudget(MonthlyBudget(31.0, "ZZZ"))
        navigate(Screen.SettingsSubPage.createRoute(SettingsPage.PREFERENCES))
        compose.onNodeWithText(text(R.string.monthly_budget)).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("47")
        compose.runOnIdle { preferences.setCurrency("EUR") }
        compose.onNodeWithText(context.getString(R.string.monthly_budget_hint, "ZZZ")).assertExists()
        compose.onNode(hasSetTextAction()).assertTextContains("47")
        compose.onNodeWithText(text(R.string.cancel)).performClick()
        assertEquals(MonthlyBudget(31.0, "ZZZ"), persistedBudget())
        compose.onNodeWithText(text(R.string.monthly_budget)).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("47")
        compose.onNodeWithText(text(R.string.save)).performClick()
        compose.waitForIdle()
        assertEquals(MonthlyBudget(47.0, "ZZZ"), persistedBudget())
        compose.onNodeWithText(text(R.string.monthly_budget)).performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("47")
        compose.onNodeWithText(text(R.string.cancel)).performClick()
    }

    @Test fun realDetailResumeAcceptsSixthActiveAndSuppressesUnknownBudgetConclusion() {
        seedInventory()
        preferences.setMonthlyBudget(MonthlyBudget(1.0, "USD"))
        compose.waitUntil(10_000) { runBlocking { repository.activeSubscriptions.first().size == 6 } }
        navigate(Screen.SubscriptionDetail.createRoute("Paused"))
        scrollTo(hasText(text(R.string.detail_resume)))
        compose.onNodeWithText(text(R.string.detail_resume)).performClick()
        awaitDisplayed(context.getString(R.string.resumed_item, "Paused"))
        compose.waitUntil(10_000) { runBlocking { repository.getById("Paused")?.isActive == true } }
        assertEquals(6, runBlocking { repository.getActiveCount() })
        assertTrue(controlledAuth.isPro.value)
        assertFalse(billingAuth.isPro.value)
    }

    @Test fun analyticsExportProducesReadableDebugFileProviderCsvWithoutExternalShare() {
        seedInventory()
        Intents.init()
        intentsStarted = true
        Intents.intending(hasAction(Intent.ACTION_CHOOSER)).respondWith(
            android.app.Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
        navigate(Screen.Analytics.route)
        visible(R.string.overview_partial)
        scrollTo(hasText(text(R.string.export_report)))
        compose.onNodeWithText(text(R.string.export_report)).performClick()
        compose.waitUntil(10_000) { Intents.getIntents().any { it.action == Intent.ACTION_CHOOSER } }
        val chooser = Intents.getIntents().single { it.action == Intent.ACTION_CHOOSER }
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/csv", send.type)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        @Suppress("DEPRECATION")
        val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("content", uri.scheme)
        assertEquals("org.dpdns.alwaysup.subflow.debug.fileprovider", uri.authority)
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        assertArrayEquals(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), bytes.take(3).toByteArray())
        val rows = parseCsv(bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF"))
        assertEquals(12, rows.first().size)
        assertEquals("MonthlyInUSD", rows.first()[6])
        assertEquals("MonthlyConversionStatus", rows.first().last())
        val data = rows.drop(1).associateBy { it[0] }
        assertEquals(6, data.size)
        assertTrue(data.containsKey("Paused"))
        assertFalse(data.containsKey("Deleted"))
        assertEquals("", data.getValue("MissingFX")[6])
        assertEquals("MISSING_SOURCE_RATE", data.getValue("MissingFX")[11])
        assertEquals("line one\nline \"two\"", data.getValue("Quoted, \"name\"")[10])
    }

    @Test fun nativeCsvChooserGrantsCrossUidReceiverAndVerifiesSavedBytes() {
        seedInventory()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val receiverPackage = context.packageName + ".test"
        val receiverLabel = "SubFlow QA CSV receiver"
        assertEquals(receiverPackage, instrumentation.context.packageName)
        assertNotEquals(context.applicationInfo.uid, instrumentation.context.applicationInfo.uid)
        Intents.init()
        intentsStarted = true
        Intents.intending(hasAction(Intent.ACTION_CHOOSER)).respondWith(
            android.app.Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))
        navigate(Screen.Analytics.route)
        visible(R.string.overview_partial)
        scrollTo(hasText(text(R.string.export_report)))
        compose.onNodeWithText(text(R.string.export_report)).performClick()
        compose.waitUntil(10_000) { Intents.getIntents().any { it.action == Intent.ACTION_CHOOSER } }
        val chooser = Intents.getIntents().single { it.action == Intent.ACTION_CHOOSER }
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("text/csv", send.type)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        @Suppress("DEPRECATION")
        val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals("content", uri.scheme)
        assertEquals("org.dpdns.alwaysup.subflow.debug.fileprovider", uri.authority)
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        assertTrue(bytes.isNotEmpty())
        val expectedHash = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        Intents.release()
        intentsStarted = false

        val receiverSend = Intent(send).setComponent(ComponentName(
            receiverPackage, "org.dpdns.alwaysup.subflow.Bin94CsvReceiverActivity"))
            .putExtra("QA_EXPECTED_SHA256", expectedHash)
        // Replay the real chooser; only the owned QA target is selected, never ranked apps.
        val replay = Intent(chooser).putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(receiverSend))
        Configurator.getInstance().setUiAutomationFlags(
            android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val device = UiDevice.getInstance(instrumentation)
        scenario!!.onActivity { it.startActivity(replay) }
        val target = device.wait(Until.findObject(By.text(receiverLabel)), 10_000)
        assertNotNull("Owned QA receiver must be visible in the native chooser", target)
        target!!.click()
        try {
            assertTrue("Receiver window did not appear",
                device.wait(Until.hasObject(By.pkg(receiverPackage)), 10_000))
            assertEquals(receiverPackage, device.currentPackageName)
            assertTrue(device.wait(Until.hasObject(By.pkg(receiverPackage).text(receiverLabel)), 10_000))
            assertTrue(device.wait(Until.hasObject(By.pkg(receiverPackage)
                .text("Saved and verified ${bytes.size} bytes")), 10_000))
            assertTrue(device.hasObject(By.pkg(receiverPackage).text("SHA-256: $expectedHash")))
            assertTrue((context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isInteractive)
            assertFalse((context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isKeyguardLocked)
            assertEquals(receiverPackage, device.currentPackageName)
            instrumentation.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
                .waitForIdle(1_000, 10_000)
            device.dumpWindowHierarchy(File(context.cacheDir, "bin94-csv-receiver.xml"))
            assertTrue("Receiver screenshot could not be saved",
                device.takeScreenshot(File(context.cacheDir, "bin94-csv-receiver.png")))
        } finally {
            if (device.currentPackageName == receiverPackage) device.pressBack()
        }
        assertTrue("Back must return to the owned Debug MainActivity",
            device.wait(Until.hasObject(By.pkg(context.packageName)), 10_000))
        assertEquals(context.packageName, device.currentPackageName)
    }

    // Insert exact schedules without saveSubscription's intentional renewal normalization.
    // All mutations under test still go through the real NavHost/repository callbacks.
    private fun seedExact(vararg records: Subscription) = runBlocking {
        val dao = org.dpdns.alwaysup.subflow.data.local.SubFlowDatabase.getDatabase(context).subscriptionDao()
        records.forEach { dao.insertOrUpdate(org.dpdns.alwaysup.subflow.data.local.SubscriptionEntity.fromDomain(it)) }
    }

    private fun usd(value: Double) = org.dpdns.alwaysup.subflow.domain.util.CurrencyFormatter.format(
        value, "USD", context.resources.configuration.locales[0])

    private fun scrollTo(matcher: SemanticsMatcher) {
        val vertical = SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)
        compose.onNode(hasScrollAction() and vertical).performScrollToNode(matcher)
    }

    private fun analyticsScrollTo(matcher: SemanticsMatcher) = scrollTo(matcher)

    // Non-clickable AppleCards expose their text children under the caption's semantics parent.
    // Scope to that actual node, not to the whole lazy list (which has other unavailable cards).
    private fun captionCard(caption: Int): SemanticsMatcher {
        val parent = compose.onNodeWithText(text(caption)).fetchSemanticsNode().parent!!
        val root = SemanticsMatcher("caption card ${parent.id}") { it.id == parent.id }
        return root or hasAnyAncestor(root)
    }

    @Test fun keptExpiredUnknownTrialKeepsKnownHistoryButSuspendsForecast() {
        val day = today
        val end = day.minusDays(1).toString()
        seedExact(
            record("Known history").copy(firstBillDate = day.minusYears(2).toString(), nextBillDate = day.toString()),
            record("Kept unresolved", 0.0, "ZZZ").copy(isTrial = true, trialEndDate = end,
                nextBillDate = end, trialKeepEndDate = end, trialConverts = true, postTrialAmount = 20.0)
        )
        navigate(Screen.Analytics.route)
        analyticsScrollTo(hasText(text(R.string.history_caption)))
        compose.onNodeWithText(text(R.string.history_caption)).performScrollTo().assertIsDisplayed()
        val history = captionCard(R.string.history_caption)
        compose.onNode(history and hasText(usd(12.0))).assertExists()
        compose.onNode(history and hasText(text(R.string.overview_partial))).assertDoesNotExist()
        compose.onNode(history and hasText(text(R.string.analytics_coverage_unavailable))).assertDoesNotExist()
        // The chart's month labels are real text semantics; its Canvas has no spoken amounts.
        val month = day.format(java.time.format.DateTimeFormatter.ofPattern("MMM", context.resources.configuration.locales[0]))
        compose.onNode(history and hasText(month)).assertExists()

        analyticsScrollTo(hasText(text(R.string.billing_forecast).uppercase()))
        compose.onNodeWithText(text(R.string.billing_forecast).uppercase()).assertIsDisplayed()
        // Export follows the final forecast card. Bring that end of the lazy list into composition.
        analyticsScrollTo(hasText(text(R.string.export_report)))
        compose.onAllNodesWithText(text(R.string.analytics_coverage_unavailable)).onLast()
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.billing_forecast_caption)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.forecast_average_line)).assertDoesNotExist()
    }

    @Test fun unknownAnnualOutsideHorizonKeepsPartialStreamingAndCompleteForecast() {
        val day = today
        seedExact(
            record("Known streaming").copy(category = "Streaming", firstBillDate = day.minusYears(2).toString(),
                nextBillDate = day.toString()),
            // The annual anniversary lies beyond the six-month forecast horizon.
            record("Unknown annual", 20.0, "ZZZ").copy(category = "Streaming", cycle = BillingCycle.ANNUALLY,
                firstBillDate = day.withDayOfMonth(1).minusYears(2).minusMonths(4).toString(),
                nextBillDate = day.plusYears(1).toString())
        )
        navigate(Screen.Analytics.route)
        analyticsScrollTo(hasText(text(R.string.history_caption)))
        compose.onNodeWithText(text(R.string.history_caption)).performScrollTo().assertIsDisplayed()
        val history = captionCard(R.string.history_caption)
        compose.onNode(history and hasText(usd(12.0))).assertExists()
        compose.onNode(history and hasText(text(R.string.overview_partial))).assertExists()
        compose.onNode(history and hasText(text(R.string.analytics_coverage_unavailable))).assertExists()

        // The merged category row itself must retain both the known subtotal and coverage warning.
        val streaming = hasText(text(R.string.filter_streaming)) and hasText(usd(12.0)) and
            hasText(text(R.string.overview_partial))
        analyticsScrollTo(streaming)
        compose.onNode(streaming).performScrollTo().assertIsDisplayed()

        val summary = context.getString(R.string.forecast_even, usd(12.0))
        analyticsScrollTo(hasText(summary))
        compose.onNodeWithText(summary).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.billing_forecast_caption)).performScrollTo().assertIsDisplayed()
        val forecast = captionCard(R.string.billing_forecast_caption)
        compose.onNode(forecast and hasText(summary)).assertExists()
        compose.onNode(forecast and hasText(text(R.string.analytics_coverage_unavailable))).assertDoesNotExist()
        compose.onNode(forecast and hasText(text(R.string.forecast_average_line))).assertDoesNotExist()
    }

    @Test fun dashboardCancelledTrialResumeShowsActualOverrunAndUndoRestoresWholeRecord() {
        val day = today
        val end = day.minusDays(1).toString()
        val original = record("Cancelled trial", 0.0, active = false).copy(
            firstBillDate = day.minusDays(15).toString(), nextBillDate = end, cycle = BillingCycle.ANNUALLY, isTrial = true,
            trialEndDate = end, trialKeepEndDate = end, trialOutcome = TrialOutcome.CANCELLED,
            trialConverts = true, postTrialAmount = 9.0, postTrialCycle = BillingCycle.MONTHLY,
            notes = "Exact undo includes dates, outcome, currency, price and timestamp", updatedAt = 123456789L)
        seedExact(record("Active free", 0.0), original)
        preferences.setMonthlyBudget(MonthlyBudget(1.0, "USD"))
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("1\n${text(R.string.overview_paused)}").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("1\n${text(R.string.overview_paused)}").performScrollTo().performClick()
        val row = hasContentDescription(original.name, substring = true) and hasClickAction()
        scrollTo(row)
        val actions = compose.onNode(row).assertIsDisplayed().fetchSemanticsNode().config[
            androidx.compose.ui.semantics.SemanticsActions.CustomActions]
        val resume = actions.single { it.label == text(R.string.resume_action) }
        val message = org.dpdns.alwaysup.subflow.domain.util.joinSentences(
            context.getString(R.string.resumed_as_paid, original.name, usd(9.0)),
            context.getString(R.string.budget_now_over, usd(8.0), usd(1.0)),
            context.resources.configuration.locales[0])
        compose.runOnIdle { assertTrue(resume.action()) }
        awaitDisplayed(message)
        compose.waitUntil(10_000) { runBlocking { repository.getById(original.id)?.isActive == true } }
        val updated = runBlocking { repository.getById(original.id)!! }
        val next = day.minusDays(1).plusMonths(1).toString()
        assertEquals(original.copy(isActive = true, isTrial = false, trialOutcome = TrialOutcome.CONVERTED,
            amount = 9.0, cycle = BillingCycle.MONTHLY, firstBillDate = end, nextBillDate = next,
            updatedAt = updated.updatedAt), updated)
        compose.onNodeWithText(text(R.string.undo)).assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { runBlocking { repository.getById(original.id) == original } }
        assertEquals(original, runBlocking { repository.getById(original.id) })
    }

    /** RFC4180 reader, including embedded newline and doubled quotes; not the production generator. */
    private fun parseCsv(csv: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < csv.length) {
            val c = csv[i++]
            when {
                c == '"' && quoted && i < csv.length && csv[i] == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { row.add(cell.toString()); cell.clear() }
                c == '\n' && !quoted -> { row.add(cell.toString()); rows.add(row.toList()); row.clear(); cell.clear() }
                else -> cell.append(c)
            }
        }
        assertFalse("Unclosed CSV quote", quoted)
        return rows
    }

    @After fun disposeFixture() {
        try {
            if (scenario != null) {
                val bitmap = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot()
                bitmap?.let {
                    File(context.cacheDir, "bin94-${testName.methodName}.png").outputStream().use { stream ->
                        it.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    }
                    it.recycle()
                }
            }
        } finally {
            if (intentsStarted) Intents.release()
            scenario?.close()
            if (::billing.isInitialized) billing.endConnection()
            billingScope.cancel()
            if (::controlledAuth.isInitialized) controlledAuth.updateProStatus(false)
            if (::billingAuth.isInitialized) billingAuth.updateProStatus(false)
            if (::repository.isInitialized) runBlocking { repository.clearAllData() }
            if (::preferences.isInitialized) { preferences.setMonthlyBudget(null); preferences.setCurrency("USD") }
        }
    }
}
