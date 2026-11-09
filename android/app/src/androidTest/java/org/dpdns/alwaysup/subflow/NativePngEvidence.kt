package org.dpdns.alwaysup.subflow

import android.graphics.Bitmap
import java.io.File
import java.io.OutputStream

internal fun clearPngEvidence(destination: File) {
    check(!destination.exists() || destination.delete()) { "Cannot remove old native evidence: ${destination.name}" }
}

internal fun writeFreshPngEvidence(
    destination: File,
    capture: () -> Bitmap?,
    encode: (Bitmap, OutputStream) -> Boolean = { bitmap, stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
) {
    clearPngEvidence(destination)
    val bitmap = checkNotNull(capture()) { "Current native screenshot is missing: ${destination.name}" }
    var complete = false
    try {
        destination.outputStream().use { stream ->
            check(encode(bitmap, stream)) { "Current native PNG encoding failed: ${destination.name}" }
        }
        check(destination.length() > 0) { "Current native PNG is empty: ${destination.name}" }
        complete = true
    } finally {
        bitmap.recycle()
        if (!complete) destination.delete()
    }
}
