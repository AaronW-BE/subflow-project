package org.dpdns.alwaysup.subflow.data.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentForm
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import org.dpdns.alwaysup.subflow.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Google's User Messaging Platform gate.
 *
 * Serving ads to EEA/UK users without collecting a TCF consent signal breaks
 * both AdMob policy and GDPR, so no ad is requested until [canRequestAds] is
 * true. Outside those regions the SDK reports "not required" and ads start
 * immediately.
 */
class AdsConsentManager(private val appContext: Context) {

    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(appContext)

    private val adsInitialised = AtomicBoolean(false)

    private val _canRequestAds = MutableStateFlow(consentInformation.canRequestAds())
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    /** True once a privacy options form exists, so Settings can offer the entry point. */
    val isPrivacyOptionsRequired: Boolean
        get() = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /**
     * Runs the consent flow. Safe to call on every launch: the SDK caches the
     * answer and only shows a form when one is actually required.
     */
    fun gather(activity: Activity, onComplete: () -> Unit = {}) {
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .apply {
                if (BuildConfig.DEBUG) {
                    // Replays the EEA form without travelling. setDebugGeography
                    // alone is ignored - UMP only honours it for a registered
                    // debug device, which is what the hash is for. Device tests
                    // on 2026-09-01 confirmed the geography was silently
                    // dropped while the hash was missing (gdprApplies came back
                    // 0 on a forced-EEA build). The SDK logs the device's hash
                    // on first run; set subflow.umpTestDeviceHash to use it.
                    setConsentDebugSettings(
                        ConsentDebugSettings.Builder(activity)
                            .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                            .apply {
                                BuildConfig.UMP_TEST_DEVICE_HASH
                                    .takeIf { it.isNotBlank() }
                                    ?.let { addTestDeviceHashedId(it) }
                            }
                            .build()
                    )
                }
            }
            .build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form error ${formError.errorCode}: ${formError.message}")
                    }
                    _canRequestAds.value = consentInformation.canRequestAds()
                    maybeInitialiseAds()
                    onComplete()
                }
            },
            { requestError ->
                Log.w(TAG, "Consent info update failed: ${requestError.message}")
                // Fail closed: without a consent signal we simply do not show ads.
                _canRequestAds.value = consentInformation.canRequestAds()
                maybeInitialiseAds()
                onComplete()
            }
        )
    }

    /** Re-opens the consent form from Settings, as required by the TCF policy. */
    fun showPrivacyOptions(activity: Activity, onDismiss: (String?) -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            _canRequestAds.value = consentInformation.canRequestAds()
            onDismiss(error?.message)
        }
    }

    private fun maybeInitialiseAds() {
        if (!_canRequestAds.value) return
        if (!adsInitialised.compareAndSet(false, true)) return
        // Initialisation touches the disk and the network; keep it off the main thread.
        Thread({ MobileAds.initialize(appContext) {} }, "AdMobInit").apply {
            priority = Thread.MIN_PRIORITY
            start()
        }
    }

    private companion object {
        const val TAG = "SubFlowConsent"
    }
}

/** Provided app-wide so any screen can decide whether a banner slot may render. */
val LocalAdsConsent = androidx.compose.runtime.compositionLocalOf<AdsConsentManager?> { null }
