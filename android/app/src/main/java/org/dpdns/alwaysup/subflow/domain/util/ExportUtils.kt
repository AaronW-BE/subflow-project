package org.dpdns.alwaysup.subflow.domain.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import java.io.File
import java.time.LocalDate
import java.util.Locale

/**
 * Writes exports to the cache directory and shares them through FileProvider.
 *
 * The previous implementation put the whole CSV into Intent.EXTRA_TEXT, which
 * most target apps silently drop - Drive, Gmail attachments and file managers
 * all expect a content:// stream.
 */
object ExportUtils {

    private const val EXPORT_DIR = "exports"

    /** U+FEFF: written as EF BB BF, the three bytes Excel looks for. */
    const val UTF8_BOM = "\uFEFF"

    /**
     * The spending report as CSV, meant to be opened in a spreadsheet.
     *
     * It starts with a UTF-8 byte-order mark. Excel opens a double-clicked
     * .csv in the system's legacy code page unless one is there, which turned
     * every Chinese, Japanese or accented name and note into mojibake - in an
     * app that ships in seven languages. Sheets and Numbers ignore it.
     *
     * The mark belongs to this file and not to [shareIntent], which also
     * writes the JSON backup: JSON does not allow one, and the app's own
     * restore reads that file with Gson.
     */
    fun buildCsv(subs: List<Subscription>, primaryCurrency: String): String {
        val sb = StringBuilder(UTF8_BOM)
        sb.append(
            "Name,Category,Amount,Currency,Cycle,MonthlyNormalized,MonthlyIn$primaryCurrency," +
                "FirstBillDate,NextRenewalDate,ReminderDaysBefore,Notes\n"
        )
        for (s in subs) {
            val monthlyHome = CurrencyConverter.convert(s.monthlyAmount, s.currency, primaryCurrency)
            sb.append(csv(s.name)).append(',')
                .append(csv(s.category)).append(',')
                .append(String.format(Locale.US, "%.2f", s.amount)).append(',')
                .append(s.currency).append(',')
                .append(s.cycle.key).append(',')
                .append(String.format(Locale.US, "%.2f", s.monthlyAmount)).append(',')
                .append(String.format(Locale.US, "%.2f", monthlyHome)).append(',')
                .append(s.firstBillDate).append(',')
                .append(s.nextBillDate).append(',')
                .append(s.reminderDaysBefore).append(',')
                .append(csv(s.notes)).append('\n')
        }
        return sb.toString()
    }

    /** Minimal RFC 4180 escaping. */
    private fun csv(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

    /**
     * Writes [content] to a cache file and returns a share Intent, or null if
     * the file could not be created.
     */
    fun shareIntent(
        context: Context,
        fileName: String,
        content: String,
        mimeType: String,
        subject: String
    ): Intent? = try {
        val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
        // One file per name keeps the cache from growing without bound.
        val file = File(dir, fileName)
        file.writeText(content)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } catch (e: Exception) {
        null
    }

    fun timestampedName(prefix: String, extension: String): String =
        "$prefix-${LocalDate.now()}.$extension"
}
