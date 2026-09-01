package org.dpdns.alwaysup.subflow

import android.app.Application
import androidx.work.Configuration

/**
 * WorkManager's default initializer is removed in the manifest so that the
 * daily renewal scan is not scheduled while the process is still starting up.
 * The work is enqueued from [MainActivity] once the first frame is on screen.
 *
 * Ads are not initialised here either: the Mobile Ads SDK must wait for the
 * UMP consent result (see AdsConsentManager).
 */
class SubFlowApplication : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.ERROR)
            .build()

}
