package org.dpdns.alwaysup.subflow.ui.support

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.dpdns.alwaysup.subflow.BuildConfig

const val SUPPORT_EMAIL = "bin-tech@outlook.com"

/**
 * Opens a mail app with the support address, the version and the device
 * already filled in, so a reply does not have to begin by asking which build
 * it is about.
 *
 * Shared rather than private to Settings since the what's-new sheet offers the
 * same thing: whoever has just read what changed is the likeliest person to
 * have something to say about it, and that is the reply we are short of.
 */
fun openSupportEmail(context: Context) {
    val body = "\n\n---\nSubFlow ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
        "Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:$SUPPORT_EMAIL")
        putExtra(Intent.EXTRA_SUBJECT, "SubFlow Android support")
        putExtra(Intent.EXTRA_TEXT, body)
    }
    runCatching { context.startActivity(intent) }
}
