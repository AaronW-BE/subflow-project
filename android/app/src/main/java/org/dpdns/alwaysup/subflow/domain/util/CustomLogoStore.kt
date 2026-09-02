package org.dpdns.alwaysup.subflow.domain.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * Stores the images users pick for subscriptions the catalogue does not cover.
 *
 * The picked image is copied rather than referenced. Android's photo picker
 * hands back a `content://` URI carrying a temporary read grant that dies with
 * the process, so storing that string would give a logo that works until the
 * app is next launched and then silently renders nothing.
 *
 * Everything stays in the app's private files directory. No upload, no network,
 * no MediaStore write — picking a logo touches nothing outside this app, which
 * is the only way it can be added without weakening what the privacy policy
 * says about data staying on the device.
 */
object CustomLogoStore {

    private const val TAG = "SubFlowLogo"
    private const val DIR = "subscription_logos"

    /** Badges render at most 64dp; 256px covers that on a 4x density screen. */
    private const val MAX_EDGE = 256

    /**
     * Copies [source] into private storage, downscaled, and returns a URI to
     * keep in `Subscription.iconUrl`.
     *
     * Returns null when the image cannot be read or decoded, which the caller
     * should treat as "the user picked something unusable" rather than as an
     * error worth interrupting them over — the badge simply keeps its previous
     * appearance.
     */
    fun save(context: Context, subscriptionId: String, source: Uri): String? {
        val decoded = decodeScaled(context, source) ?: run {
            Log.d(TAG, "Could not decode the picked image")
            return null
        }
        return try {
            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            // Named by subscription so re-picking replaces rather than
            // accumulates, and so deleting the row can find the file.
            val target = File(dir, "$subscriptionId.png")
            FileOutputStream(target).use { out ->
                decoded.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Uri.fromFile(target).toString()
        } catch (e: Exception) {
            Log.d(TAG, "Could not store the logo: ${e.message}")
            null
        } finally {
            decoded.recycle()
        }
    }

    /** Removes a stored logo. Safe to call when there is none. */
    fun delete(context: Context, subscriptionId: String) {
        runCatching { File(File(context.filesDir, DIR), "$subscriptionId.png").delete() }
    }

    /**
     * Whether this value is one of ours.
     *
     * `iconUrl` also carries the catalogue's remote URLs, which must never be
     * loaded — see BrandLogos for why. Only a file we wrote is safe to render.
     */
    fun isStoredLogo(iconUrl: String?): Boolean =
        !iconUrl.isNullOrBlank() && iconUrl.startsWith("file://")

    /**
     * Decodes at a sample size large enough for the badge and no larger.
     *
     * A modern phone photo is 12 megapixels. Decoding one at full size to draw
     * it at 44dp risks an OutOfMemoryError on a low-end device for an image
     * that is about to be thrown away.
     */
    private fun decodeScaled(context: Context, source: Uri): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }

        val longest = max(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) {
            null
        } else {
            val options = BitmapFactory.Options().apply {
                inSampleSize = generateSequence(1) { it * 2 }
                    .first { longest / it <= MAX_EDGE }
            }
            context.contentResolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }
    } catch (e: Exception) {
        Log.d(TAG, "Could not read the picked image: ${e.message}")
        null
    }
}
