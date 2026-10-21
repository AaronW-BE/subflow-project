package org.dpdns.alwaysup.subflow.data.review

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory
import org.dpdns.alwaysup.subflow.data.preferences.PreferencesManager
import java.util.concurrent.TimeUnit

/**
 * When to ask for a Play rating.
 *
 * Someone who has used the app enough to have an opinion, at a moment that
 * went well, and not often. Kept pure so the rules are tested; Play adds its
 * own quota on top and may still show nothing.
 */
object ReviewPolicy {
    const val MIN_INSTALL_DAYS = 7L
    const val MIN_LAUNCHES = 5
    const val MIN_RUNNING_SUBSCRIPTIONS = 3
    const val COOLDOWN_DAYS = 90L

    fun shouldAsk(
        nowMillis: Long,
        firstInstallMillis: Long,
        launchCount: Int,
        runningSubscriptions: Int,
        lastAskedMillis: Long?
    ): Boolean {
        val installedLongEnough = nowMillis - firstInstallMillis >= TimeUnit.DAYS.toMillis(MIN_INSTALL_DAYS)
        val cooledDown = lastAskedMillis == null ||
            nowMillis - lastAskedMillis >= TimeUnit.DAYS.toMillis(COOLDOWN_DAYS)
        return installedLongEnough &&
            launchCount >= MIN_LAUNCHES &&
            runningSubscriptions >= MIN_RUNNING_SUBSCRIPTIONS &&
            cooledDown
    }
}

/**
 * Asks for a Play rating through the In-App Review API, inside the app.
 *
 * Called after a subscription is saved: a natural pause after something went
 * well, never on launch and never in the middle of a task. The time is
 * recorded before asking, whether or not Play shows anything - the API does
 * not say, and asking again on the next save would be pestering whichever
 * way it went.
 */
class ReviewPrompter(
    private val context: Context,
    private val preferences: PreferencesManager
) {
    fun maybeAsk(activity: Activity?, runningSubscriptions: Int) {
        if (activity == null) return
        val firstInstall = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        }.getOrNull() ?: return
        val now = System.currentTimeMillis()
        val ask = ReviewPolicy.shouldAsk(
            nowMillis = now,
            firstInstallMillis = firstInstall,
            launchCount = preferences.launchCount(),
            runningSubscriptions = runningSubscriptions,
            lastAskedMillis = preferences.reviewLastAskedAt
        )
        if (!ask) return
        preferences.markReviewAsked(now)

        val manager = ReviewManagerFactory.create(context)
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (request.isSuccessful) {
                manager.launchReviewFlow(activity, request.result)
            } else {
                // Not installed from Play, or Play declined: nothing to show,
                // and nothing for the user to be told.
                Log.i("SubFlowReview", "Review flow unavailable: ${request.exception?.message}")
            }
        }
    }
}
