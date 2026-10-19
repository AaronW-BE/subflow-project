package org.dpdns.alwaysup.subflow.domain.util

import org.dpdns.alwaysup.subflow.domain.model.BillingCycle
import org.dpdns.alwaysup.subflow.domain.model.PresetService
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

/**
 * Reads subscriptions out of a CSV file, without touching the database.
 *
 * Two kinds of file have to work: the app's own export, so a spreadsheet can
 * go out and come back, and whatever someone typed into a spreadsheet of
 * their own. The second is why so much here is forgiving - headers in any
 * case and under common other names, a semicolon where Excel in Germany,
 * France or Spain puts one, decimal commas, day-first dates - and why a row
 * that cannot be read is reported with its line number instead of failing
 * the whole file.
 *
 * Pure: the caller supplies today, the currencies the app supports and how
 * ids are made, so every rule here is unit-tested.
 */
object CsvImport {

    enum class Field { NAME, AMOUNT, CURRENCY, CYCLE, FIRST_BILL_DATE, CATEGORY, NOTES, REMINDER }

    enum class Problem { MISSING_NAME, BAD_AMOUNT, BAD_CURRENCY, BAD_CYCLE, BAD_DATE }

    /** The columns a file must have. Everything else has a default. */
    val REQUIRED = setOf(Field.NAME, Field.AMOUNT, Field.FIRST_BILL_DATE)

    data class Options(
        val primaryCurrency: String,
        val supportedCurrencies: Set<String>,
        /** Canonical category keys; a match in any case is stored as the key. */
        val knownCategories: List<String>,
        /** Used when a row leaves the category blank. */
        val defaultCategory: String,
        /**
         * How to read 03/04/2026 when neither part gives it away: true reads
         * it as 3 April. Follows the user's locale - only the US writes the
         * month first.
         */
        val dayFirstSlashDates: Boolean,
        /** Cycle names in the interface language, e.g. 每月 -> MONTHLY. */
        val extraCycleAliases: Map<String, BillingCycle> = emptyMap(),
        /**
         * Column names in the interface language - the add form's own field
         * labels, so a sheet headed 名称, 价格, 首次扣费日期 reads the same as
         * one headed Name, Price, First Payment Date.
         */
        val extraHeaderAliases: Map<String, Field> = emptyMap(),
        val today: LocalDate = LocalDate.now(),
        val newId: () -> String = { "sub_" + UUID.randomUUID().toString().take(10) }
    )

    sealed interface Result {
        /** Nothing but whitespace, or a header with no rows under it. */
        data object Empty : Result

        /** The header row lacks a column every row needs. */
        data class MissingColumns(val missing: Set<Field>) : Result

        data class Rows(val rows: List<Row>) : Result
    }

    /** One data row. [line] is the 1-based line it starts on, header included. */
    sealed interface Row {
        val line: Int
    }

    data class Parsed(override val line: Int, val subscription: Subscription) : Row

    data class Invalid(override val line: Int, val problem: Problem, val value: String) : Row

    /** What an import would do, before anything is written. */
    data class Preview(
        val toAdd: List<Subscription>,
        val duplicates: List<Parsed>,
        val invalid: List<Invalid>
    )

    fun parse(text: String, options: Options): Result {
        val body = text.removePrefix("\uFEFF")
        if (body.isBlank()) return Result.Empty

        val delimiter = detectDelimiter(body.lineSequence().first { it.isNotBlank() })
        val records = tokenize(body, delimiter).filterNot { (_, cells) -> cells.all { it.isBlank() } }
        if (records.isEmpty()) return Result.Empty

        val (_, header) = records.first()
        val columns = mapHeader(header, options.extraHeaderAliases)
        val missing = REQUIRED - columns.keys
        if (missing.isNotEmpty()) return Result.MissingColumns(missing)
        if (records.size == 1) return Result.Empty

        val decimalComma = delimiter == ';'
        val rows = records.drop(1).map { (line, cells) ->
            readRow(line, cells, columns, options, decimalComma)
        }
        return Result.Rows(rows)
    }

    /**
     * Splits [rows] into what would be added, what already exists, and what
     * could not be read.
     *
     * A duplicate is the same service at the same price, in the same currency
     * and on the same cycle as a subscription already on the device - or as an
     * earlier row of the same file, which is what importing one export twice
     * looks like from the inside.
     */
    fun preview(rows: List<Row>, existing: List<Subscription>): Preview {
        val seen = existing.filterNot { it.isDeleted }.map { duplicateKey(it) }.toMutableSet()
        val toAdd = mutableListOf<Subscription>()
        val duplicates = mutableListOf<Parsed>()
        val invalid = mutableListOf<Invalid>()
        for (row in rows) {
            when (row) {
                is Invalid -> invalid += row
                is Parsed -> if (seen.add(duplicateKey(row.subscription))) {
                    toAdd += row.subscription
                } else {
                    duplicates += row
                }
            }
        }
        return Preview(toAdd, duplicates, invalid)
    }

    // ------------------------------------------------------------------ rows

    private fun readRow(
        line: Int,
        cells: List<String>,
        columns: Map<Field, Int>,
        options: Options,
        decimalComma: Boolean
    ): Row {
        fun cell(field: Field): String =
            columns[field]?.let { cells.getOrNull(it) }?.trim().orEmpty()

        val name = cell(Field.NAME)
        if (name.isEmpty()) return Invalid(line, Problem.MISSING_NAME, "")

        val rawAmount = cell(Field.AMOUNT)
        val amount = parseAmount(rawAmount, decimalComma)
            ?: return Invalid(line, Problem.BAD_AMOUNT, rawAmount)

        val rawCurrency = cell(Field.CURRENCY)
        val currency = if (rawCurrency.isEmpty()) {
            options.primaryCurrency
        } else {
            rawCurrency.uppercase(Locale.ROOT).takeIf { it in options.supportedCurrencies }
                ?: return Invalid(line, Problem.BAD_CURRENCY, rawCurrency)
        }

        val rawCycle = cell(Field.CYCLE)
        val cycle = parseCycle(rawCycle, options.extraCycleAliases)
            ?: return Invalid(line, Problem.BAD_CYCLE, rawCycle)

        val rawDate = cell(Field.FIRST_BILL_DATE)
        val firstBill = parseDate(rawDate, options.dayFirstSlashDates)
            ?: return Invalid(line, Problem.BAD_DATE, rawDate)
        val firstBillIso = firstBill.toString()

        val rawCategory = cell(Field.CATEGORY)
        val category = when {
            rawCategory.isEmpty() -> options.defaultCategory
            else -> options.knownCategories.firstOrNull { it.equals(rawCategory, ignoreCase = true) }
                ?: rawCategory
        }

        return Parsed(
            line,
            Subscription(
                id = options.newId(),
                name = name,
                category = category,
                amount = amount,
                currency = currency,
                cycle = cycle,
                firstBillDate = firstBillIso,
                nextBillDate = DateCalculators.computeNextRenewalDate(firstBillIso, cycle, options.today),
                reminderDaysBefore = parseReminder(cell(Field.REMINDER)),
                notes = cell(Field.NOTES)
            )
        )
    }

    /**
     * Gives an imported subscription the colour and icon of the preset it
     * names, as picking that preset in the add form would have. A Netflix row
     * from a spreadsheet should look like every other Netflix in the list,
     * not like a custom service someone invented.
     */
    fun withPresetLooks(subscription: Subscription, presets: List<PresetService>): Subscription {
        val key = normaliseName(subscription.name)
        val preset = presets.firstOrNull {
            normaliseName(it.name) == key || normaliseName(it.id) == key
        } ?: return subscription
        return subscription.copy(colorHex = preset.brandColor, iconUrl = preset.iconUrl)
    }

    private fun duplicateKey(s: Subscription): String =
        listOf(
            normaliseName(s.name),
            String.format(Locale.ROOT, "%.2f", s.amount),
            s.currency.uppercase(Locale.ROOT),
            s.cycle.name
        ).joinToString("|")

    private fun normaliseName(name: String): String =
        name.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    // --------------------------------------------------------------- header

    private val HEADER_ALIASES: Map<String, Field> = buildMap {
        listOf("name", "service", "subscription", "title").forEach { put(it, Field.NAME) }
        listOf("amount", "price", "cost").forEach { put(it, Field.AMOUNT) }
        put("currency", Field.CURRENCY)
        listOf("cycle", "billingcycle", "frequency", "period", "interval").forEach { put(it, Field.CYCLE) }
        listOf(
            "firstbilldate", "firstpaymentdate", "firstpayment", "startdate", "start",
            "billingdate", "date"
        ).forEach { put(it, Field.FIRST_BILL_DATE) }
        put("category", Field.CATEGORY)
        listOf("notes", "note", "memo").forEach { put(it, Field.NOTES) }
        listOf("reminderdaysbefore", "reminder", "reminderdays").forEach { put(it, Field.REMINDER) }
    }

    /** Column index for each field the header names; the first match wins. */
    internal fun mapHeader(
        header: List<String>,
        extra: Map<String, Field> = emptyMap()
    ): Map<Field, Int> {
        val extraByKey = extra.mapKeys { normaliseKey(it.key) }
        val out = linkedMapOf<Field, Int>()
        header.forEachIndexed { index, raw ->
            val key = normaliseKey(raw)
            val field = HEADER_ALIASES[key] ?: extraByKey[key] ?: return@forEachIndexed
            out.putIfAbsent(field, index)
        }
        return out
    }

    private fun normaliseKey(raw: String): String =
        raw.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    // ---------------------------------------------------------------- values

    /**
     * A price as people write it: "9.99", "9,99", "$1,234.50", "1.234,50".
     *
     * Where a lone separator could be either - "1,234" or "1.500" - the file's
     * delimiter decides: a semicolon file comes from a locale with decimal
     * commas, so there a dot followed by three digits is a thousands mark.
     */
    internal fun parseAmount(raw: String, decimalComma: Boolean): Double? {
        val cleaned = raw.filter { it.isDigit() || it == '.' || it == ',' || it == '-' }
        if (cleaned.isEmpty() || cleaned.startsWith('-')) return null
        val dots = cleaned.count { it == '.' }
        val commas = cleaned.count { it == ',' }
        val normalised = when {
            dots > 0 && commas > 0 ->
                if (cleaned.lastIndexOf(',') > cleaned.lastIndexOf('.')) {
                    cleaned.replace(".", "").replace(',', '.')
                } else {
                    cleaned.replace(",", "")
                }
            commas == 1 -> {
                val after = cleaned.length - cleaned.indexOf(',') - 1
                if (after == 3 && !decimalComma) cleaned.replace(",", "") else cleaned.replace(',', '.')
            }
            commas > 1 -> cleaned.replace(",", "")
            dots == 1 -> {
                val after = cleaned.length - cleaned.indexOf('.') - 1
                if (after == 3 && decimalComma) cleaned.replace(".", "") else cleaned
            }
            dots > 1 -> cleaned.replace(".", "")
            else -> cleaned
        }
        return normalised.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
    }

    private val CYCLE_ALIASES: Map<String, BillingCycle> = buildMap {
        listOf("weekly", "week", "w", "wk", "1w", "perweek").forEach { put(it, BillingCycle.WEEKLY) }
        listOf("monthly", "month", "m", "mo", "1m", "permonth").forEach { put(it, BillingCycle.MONTHLY) }
        listOf("quarterly", "quarter", "q", "3m", "3months", "threemonths")
            .forEach { put(it, BillingCycle.QUARTERLY) }
        listOf("annually", "annual", "yearly", "year", "y", "yr", "1y", "12m", "12months", "peryear")
            .forEach { put(it, BillingCycle.ANNUALLY) }
    }

    /** A blank cycle is monthly: it is what a blank most often means. */
    internal fun parseCycle(raw: String, extra: Map<String, BillingCycle>): BillingCycle? {
        if (raw.isBlank()) return BillingCycle.MONTHLY
        val key = normaliseKey(raw)
        return CYCLE_ALIASES[key]
            ?: extra.entries.firstOrNull { normaliseKey(it.key) == key || it.key.trim() == raw.trim() }?.value
    }

    private val ISO = Regex("""^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})$""")
    private val DOTTED = Regex("""^(\d{1,2})\.(\d{1,2})\.(\d{4})$""")
    private val SLASHED = Regex("""^(\d{1,2})[/-](\d{1,2})[/-](\d{4})$""")

    /**
     * Year first in any separator; day.month.year as German writes it; and
     * slashes with the year last, where a part over 12 settles which is the
     * day and [dayFirst] settles the rest.
     */
    internal fun parseDate(raw: String, dayFirst: Boolean): LocalDate? {
        val s = raw.trim()
        ISO.matchEntire(s)?.let { m ->
            val (y, mo, d) = m.destructured
            return dateOrNull(y.toInt(), mo.toInt(), d.toInt())
        }
        DOTTED.matchEntire(s)?.let { m ->
            val (d, mo, y) = m.destructured
            return dateOrNull(y.toInt(), mo.toInt(), d.toInt())
        }
        SLASHED.matchEntire(s)?.let { m ->
            val (aRaw, bRaw, y) = m.destructured
            val a = aRaw.toInt()
            val b = bRaw.toInt()
            val dayFirstHere = when {
                a > 12 -> true
                b > 12 -> false
                else -> dayFirst
            }
            return if (dayFirstHere) dateOrNull(y.toInt(), b, a) else dateOrNull(y.toInt(), a, b)
        }
        return null
    }

    private fun dateOrNull(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate.of(year, month, day) }.getOrNull()

    /**
     * The reminder lead, falling back to a day before when the file leaves it
     * out or names one the app does not offer. Not a reason to reject the
     * row: the subscription is right either way, and the lead is one tap to
     * change.
     */
    internal fun parseReminder(raw: String): Int =
        raw.trim().toIntOrNull()?.takeIf { it in setOf(0, 1, 3, 7) } ?: 1

    // --------------------------------------------------------------- tokens

    /** Whichever of comma, semicolon or tab the header line uses most. */
    internal fun detectDelimiter(headerLine: String): Char {
        var inQuotes = false
        val counts = mutableMapOf(',' to 0, ';' to 0, '\t' to 0)
        for (c in headerLine) {
            if (c == '"') inQuotes = !inQuotes
            if (!inQuotes && c in counts) counts[c] = counts.getValue(c) + 1
        }
        return counts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: ','
    }

    /**
     * RFC 4180 records: quoted fields may hold the delimiter, line breaks and
     * doubled quotes. Each record carries the line it starts on.
     */
    internal fun tokenize(text: String, delimiter: Char): List<Pair<Int, List<String>>> {
        val records = mutableListOf<Pair<Int, List<String>>>()
        var fields = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var line = 1
        var recordLine = 1
        var i = 0

        fun endRecord() {
            fields.add(field.toString())
            field.clear()
            records.add(recordLine to fields)
            fields = mutableListOf()
        }

        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    field.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                inQuotes -> {
                    if (c == '\n') line++
                    field.append(c)
                }
                c == delimiter -> {
                    fields.add(field.toString())
                    field.clear()
                }
                c == '\r' -> Unit
                c == '\n' -> {
                    endRecord()
                    line++
                    recordLine = line
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || fields.isNotEmpty()) endRecord()
        return records
    }
}
