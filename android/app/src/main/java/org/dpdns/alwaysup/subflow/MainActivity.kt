package org.dpdns.alwaysup.subflow

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import org.dpdns.alwaysup.subflow.data.ads.AdsConsentManager
import org.dpdns.alwaysup.subflow.data.ads.LocalAdsConsent
import org.dpdns.alwaysup.subflow.data.billing.BillingManager
import org.dpdns.alwaysup.subflow.data.notifications.RenewalNotificationWorker
import org.dpdns.alwaysup.subflow.data.preferences.PreferencesManager
import org.dpdns.alwaysup.subflow.data.repository.AuthRepository
import org.dpdns.alwaysup.subflow.data.repository.SubscriptionRepository
import org.dpdns.alwaysup.subflow.domain.util.localeForLanguageCode
import org.dpdns.alwaysup.subflow.ui.navigation.SubFlowNavHost
import org.dpdns.alwaysup.subflow.ui.theme.SubFlowTheme
import org.dpdns.alwaysup.subflow.ui.util.LocalHapticsEnabled
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var authRepository: AuthRepository
    private lateinit var subscriptionRepository: SubscriptionRepository
    private lateinit var billingManager: BillingManager
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var adsConsentManager: AdsConsentManager

    /** Language the activity was created with; a change forces a recreate. */
    private var attachedLanguage: String = "en"

    private var pendingSubscriptionId by mutableStateOf<String?>(null)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Alerts start working as soon as it is granted; nothing to do here. */ }

    override fun attachBaseContext(newBase: Context) {
        val langCode = PreferencesManager.resolveInitialLanguage(newBase)
        attachedLanguage = langCode
        val locale = localeForLanguageCode(langCode)
        Locale.setDefault(locale)

        super.attachBaseContext(newBase)

        // Override ONLY the locale. `createConfigurationContext` was snapshotting
        // the whole Configuration, which froze uiMode, fontScale and density at
        // launch: because uiMode is in configChanges the Activity never
        // recreates, so switching the phone to light mode (or changing the font
        // size) did nothing until the app was force-stopped.
        //
        // An override Configuration leaves every unset field tracking the device.
        applyOverrideConfiguration(
            Configuration().apply {
                setLocale(locale)
                setLayoutDirection(locale)
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        authRepository = AuthRepository(this)
        subscriptionRepository = SubscriptionRepository(this)
        preferencesManager = PreferencesManager(this)
        billingManager = BillingManager(this, authRepository)
        adsConsentManager = AdsConsentManager(applicationContext)

        billingManager.startConnection()
        preferencesManager.recordLaunch()
        handleDeepLink(intent)

        // Deferred so it does not block the first frame.
        window.decorView.post { RenewalNotificationWorker.createChannel(this) }

        setContent {
            val currentLanguage by preferencesManager.language.collectAsState()
            val currentThemeMode by preferencesManager.themeMode.collectAsState()
            val hapticsEnabled by preferencesManager.hapticsEnabled.collectAsState()
            val onboardingComplete by preferencesManager.onboardingComplete.collectAsState()

            // Both of these put a system dialog on screen. They wait until the
            // user has actually seen the app rather than stacking over the
            // first-run intro, and they run one after the other rather than
            // both at once.
            LaunchedEffect(onboardingComplete) {
                if (!onboardingComplete) return@LaunchedEffect
                RenewalNotificationWorker.scheduleDailyRenewalCheck(this@MainActivity)
                adsConsentManager.gather(this@MainActivity) {
                    requestNotificationPermissionIfNeeded()
                }
            }

            // Recreating is the only way to rebuild the whole resource stack for
            // a new locale; patching Configuration in place left cached strings
            // in the old language.
            LaunchedEffect(currentLanguage) {
                if (currentLanguage != attachedLanguage) recreate()
            }

            CompositionLocalProvider(
                LocalHapticsEnabled provides hapticsEnabled,
                LocalAdsConsent provides adsConsentManager
            ) {
                SubFlowTheme(themeMode = currentThemeMode) {
                    SubFlowNavHost(
                        navController = rememberNavController(),
                        subscriptionRepository = subscriptionRepository,
                        authRepository = authRepository,
                        preferencesManager = preferencesManager,
                        billingManager = billingManager,
                        adsConsentManager = adsConsentManager,
                        pendingSubscriptionId = pendingSubscriptionId,
                        onPendingSubscriptionHandled = { pendingSubscriptionId = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    override fun onResume() {
        super.onResume()
        // Catches purchases, cancellations and refunds that happened elsewhere -
        // for example a subscription cancelled directly in the Play Store.
        billingManager.refresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        billingManager.endConnection()
    }

    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "subflow" && data.host == "subscription") {
            pendingSubscriptionId = data.lastPathSegment
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
