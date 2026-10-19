package org.dpdns.alwaysup.subflow

import org.dpdns.alwaysup.subflow.domain.model.BillingCycle
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.domain.util.CsvImport
import org.dpdns.alwaysup.subflow.domain.util.CsvImport.Field
import org.dpdns.alwaysup.subflow.domain.util.CsvImport.Problem
import org.dpdns.alwaysup.subflow.domain.util.ExportUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Reading subscriptions out of a CSV file.
 *
 * Two kinds of file must work: the app's own export coming back, and a
 * spreadsheet someone made themselves - in any locale's Excel.
 */
class CsvImportTest {

    private val today = LocalDate.of(2026, 10, 19)
    private var ids = 0

    private fun options(dayFirst: Boolean = true) = CsvImport.Options(
        primaryCurrency = "USD",
        supportedCurrencies = setOf("USD", "EUR", "GBP", "JPY", "CNY"),
        knownCategories = listOf("Streaming", "Productivity", "Cloud", "Utilities", "Health"),
        defaultCategory = "Streaming",
        dayFirstSlashDates = dayFirst,
        extraCycleAliases = mapOf("每月" to BillingCycle.MONTHLY, "每年" to BillingCycle.ANNUALLY),
        today = today,
        newId = { "id${++ids}" }
    )

    private fun rows(text: String, dayFirst: Boolean = true): List<CsvImport.Row> {
        val result = CsvImport.parse(text, options(dayFirst))
        assertTrue("expected rows, got $result", result is CsvImport.Result.Rows)
        return (result as CsvImport.Result.Rows).rows
    }

    private fun parsed(text: String, dayFirst: Boolean = true): List<Subscription> =
        rows(text, dayFirst).map { (it as CsvImport.Parsed).subscription }

    private fun sub(
        name: String,
        amount: Double,
        cycle: BillingCycle = BillingCycle.MONTHLY,
        currency: String = "USD",
        deleted: Boolean = false
    ) = Subscription(
        id = name,
        name = name,
        category = "Streaming",
        amount = amount,
        currency = currency,
        cycle = cycle,
        firstBillDate = "2026-06-14",
        nextBillDate = "2026-11-14",
        isDeleted = deleted
    )

    // ------------------------------------------------------------ round trip

    @Test
    fun `the app's own export comes back as the same subscriptions`() {
        val original = listOf(
            sub("Netflix", 15.49).copy(notes = "family, 4K"),
            sub("iCloud+", 2.99, currency = "EUR").copy(category = "Cloud"),
            sub("Notion", 96.0, BillingCycle.ANNUALLY).copy(category = "Productivity")
        )
        val back = parsed(ExportUtils.buildCsv(original, "USD"))

        assertEquals(original.map { it.name }, back.map { it.name })
        assertEquals(original.map { it.amount }, back.map { it.amount })
        assertEquals(original.map { it.currency }, back.map { it.currency })
        assertEquals(original.map { it.cycle }, back.map { it.cycle })
        assertEquals(original.map { it.firstBillDate }, back.map { it.firstBillDate })
        assertEquals(original.map { it.category }, back.map { it.category })
        assertEquals("family, 4K", back[0].notes)
    }

    @Test
    fun `the byte-order mark the export writes is not part of the first header`() {
        val back = parsed("\uFEFFName,Amount,FirstBillDate\nNetflix,15.49,2026-03-01\n")
        assertEquals("Netflix", back.single().name)
    }

    // -------------------------------------------------------- other files

    @Test
    fun `a semicolon file from European Excel, with decimal commas and dotted dates`() {
        val back = parsed("Name;Amount;FirstBillDate\nNetflix;15,49;01.03.2026\nAdobe;1.234,50;15.02.2026\n")
        assertEquals(15.49, back[0].amount, 0.0)
        assertEquals("2026-03-01", back[0].firstBillDate)
        assertEquals(1234.50, back[1].amount, 0.0)
    }

    @Test
    fun `headers match in any case, spacing and under common other names`() {
        val back = parsed("SERVICE,Price,Start Date,Billing Cycle,Memo\nSpotify,11.99,2026-01-05,yearly,hi\n")
        val s = back.single()
        assertEquals("Spotify", s.name)
        assertEquals(11.99, s.amount, 0.0)
        assertEquals(BillingCycle.ANNUALLY, s.cycle)
        assertEquals("hi", s.notes)
    }

    @Test
    fun `columns a row can do without get defaults`() {
        val s = parsed("Name,Amount,FirstBillDate\nNetflix,15.49,2026-03-01\n").single()
        assertEquals("USD", s.currency)
        assertEquals(BillingCycle.MONTHLY, s.cycle)
        assertEquals("Streaming", s.category)
        assertEquals(1, s.reminderDaysBefore)
        assertTrue(s.isActive)
    }

    @Test
    fun `the next renewal is worked out from the first bill and today`() {
        val s = parsed("Name,Amount,FirstBillDate,Cycle\nNetflix,15.49,2026-03-25,monthly\n").single()
        assertEquals("2026-10-25", s.nextBillDate)
    }

    @Test
    fun `a missing required column fails the whole file and says which`() {
        val result = CsvImport.parse("Name,Amount\nNetflix,15.49\n", options())
        assertEquals(CsvImport.Result.MissingColumns(setOf(Field.FIRST_BILL_DATE)), result)
    }

    @Test
    fun `an empty file, or a header with nothing under it, is empty`() {
        assertEquals(CsvImport.Result.Empty, CsvImport.parse("   \n\n", options()))
        assertEquals(CsvImport.Result.Empty, CsvImport.parse("Name,Amount,FirstBillDate\n\n", options()))
    }

    // ----------------------------------------------------------- quoting

    @Test
    fun `quoted fields keep their commas, quotes and line breaks, and line numbers stay right`() {
        val text = "Name,Amount,FirstBillDate,Notes\n" +
            "\"Disney+, Premium\",13.99,2026-02-01,\"he said \"\"hi\"\"\nsecond line\"\n" +
            "Bad,abc,2026-02-01,\n"
        val rows = rows(text)
        val first = (rows[0] as CsvImport.Parsed).subscription
        assertEquals("Disney+, Premium", first.name)
        assertEquals("he said \"hi\"\nsecond line", first.notes)
        assertEquals(2, rows[0].line)
        // The quoted note spans lines 2 and 3, so the bad row is on line 4.
        assertEquals(4, rows[1].line)
    }

    @Test
    fun `windows line endings and blank lines between rows`() {
        val back = parsed("Name,Amount,FirstBillDate\r\nA,1,2026-01-01\r\n\r\nB,2,2026-01-02\r\n")
        assertEquals(listOf("A", "B"), back.map { it.name })
    }

    // --------------------------------------------------------- bad rows

    @Test
    fun `each unreadable row is reported with its line, its reason and what it said`() {
        val text = "Name,Amount,Currency,Cycle,FirstBillDate\n" +
            ",9.99,USD,monthly,2026-01-01\n" +
            "A,free,USD,monthly,2026-01-01\n" +
            "B,9.99,XYZ,monthly,2026-01-01\n" +
            "C,9.99,USD,fortnightly,2026-01-01\n" +
            "D,9.99,USD,monthly,2026-02-30\n" +
            "E,-5,USD,monthly,2026-01-01\n"
        val bad = rows(text).map { it as CsvImport.Invalid }
        assertEquals(
            listOf(
                Problem.MISSING_NAME, Problem.BAD_AMOUNT, Problem.BAD_CURRENCY,
                Problem.BAD_CYCLE, Problem.BAD_DATE, Problem.BAD_AMOUNT
            ),
            bad.map { it.problem }
        )
        assertEquals(listOf(2, 3, 4, 5, 6, 7), bad.map { it.line })
        assertEquals("fortnightly", bad[3].value)
    }

    @Test
    fun `currency codes are read in any case`() {
        assertEquals("EUR", parsed("Name,Amount,Currency,FirstBillDate\nA,1,eur,2026-01-01\n").single().currency)
    }

    // --------------------------------------------------------- values

    @Test
    fun `amounts as people write them`() {
        val comma = false
        assertEquals(9.99, CsvImport.parseAmount("9.99", comma)!!, 0.0)
        assertEquals(9.99, CsvImport.parseAmount("$9.99", comma)!!, 0.0)
        assertEquals(9.99, CsvImport.parseAmount("9,99", comma)!!, 0.0)
        assertEquals(1234.5, CsvImport.parseAmount("1,234.50", comma)!!, 0.0)
        assertEquals(1234.5, CsvImport.parseAmount("1.234,50", comma)!!, 0.0)
        assertEquals(1234.0, CsvImport.parseAmount("1,234", comma)!!, 0.0)
        assertEquals(1500.0, CsvImport.parseAmount("¥1,500", comma)!!, 0.0)
        assertEquals(0.0, CsvImport.parseAmount("0", comma)!!, 0.0)
        assertNull(CsvImport.parseAmount("", comma))
        assertNull(CsvImport.parseAmount("-3", comma))
        assertNull(CsvImport.parseAmount("free", comma))
    }

    @Test
    fun `in a semicolon file a dot before three digits is a thousands mark`() {
        assertEquals(1500.0, CsvImport.parseAmount("1.500", decimalComma = true)!!, 0.0)
        assertEquals(1.5, CsvImport.parseAmount("1.500", decimalComma = false)!!, 0.0)
        assertEquals(9.99, CsvImport.parseAmount("9,99", decimalComma = true)!!, 0.0)
    }

    @Test
    fun `dates in the forms spreadsheets write them`() {
        assertEquals(LocalDate.of(2026, 3, 4), CsvImport.parseDate("2026-03-04", true))
        assertEquals(LocalDate.of(2026, 3, 4), CsvImport.parseDate("2026/3/4", true))
        assertEquals(LocalDate.of(2026, 3, 4), CsvImport.parseDate("04.03.2026", true))
        // Ambiguous: the locale decides.
        assertEquals(LocalDate.of(2026, 4, 3), CsvImport.parseDate("03/04/2026", dayFirst = true))
        assertEquals(LocalDate.of(2026, 3, 4), CsvImport.parseDate("03/04/2026", dayFirst = false))
        // Unambiguous: a part over 12 settles it whatever the locale.
        assertEquals(LocalDate.of(2026, 3, 25), CsvImport.parseDate("25/03/2026", dayFirst = false))
        assertEquals(LocalDate.of(2026, 3, 25), CsvImport.parseDate("03/25/2026", dayFirst = true))
        assertNull(CsvImport.parseDate("2026-02-30", true))
        assertNull(CsvImport.parseDate("26-3-4", true))
        assertNull(CsvImport.parseDate("next week", true))
    }

    @Test
    fun `cycles by name, abbreviation, the interface language, or left blank`() {
        val extra = options().extraCycleAliases
        assertEquals(BillingCycle.MONTHLY, CsvImport.parseCycle("", extra))
        assertEquals(BillingCycle.MONTHLY, CsvImport.parseCycle("Monthly", extra))
        assertEquals(BillingCycle.ANNUALLY, CsvImport.parseCycle("annually", extra))
        assertEquals(BillingCycle.ANNUALLY, CsvImport.parseCycle("Yearly", extra))
        assertEquals(BillingCycle.QUARTERLY, CsvImport.parseCycle("3 months", extra))
        assertEquals(BillingCycle.WEEKLY, CsvImport.parseCycle("week", extra))
        assertEquals(BillingCycle.ANNUALLY, CsvImport.parseCycle("每年", extra))
        assertNull(CsvImport.parseCycle("fortnightly", extra))
    }

    @Test
    fun `categories match the app's own in any case, and anything else is kept`() {
        val back = parsed(
            "Name,Amount,FirstBillDate,Category\nA,1,2026-01-01,streaming\nB,1,2026-01-01,\nC,1,2026-01-01,Music\n"
        )
        assertEquals(listOf("Streaming", "Streaming", "Music"), back.map { it.category })
    }

    @Test
    fun `a reminder the app does not offer falls back to a day before`() {
        val back = parsed(
            "Name,Amount,FirstBillDate,ReminderDaysBefore\nA,1,2026-01-01,7\nB,1,2026-01-01,5\nC,1,2026-01-01,0\n"
        )
        assertEquals(listOf(7, 1, 0), back.map { it.reminderDaysBefore })
    }

    // --------------------------------------------------------- preview

    @Test
    fun `the preview skips what is already on the device and what repeats in the file`() {
        val existing = listOf(
            sub("Netflix", 15.49),
            sub("Gone", 5.0, deleted = true)
        )
        val text = "Name,Amount,FirstBillDate\n" +
            "netflix ,15.49,2026-01-01\n" + // already there, different case and spacing
            "Netflix,22.99,2026-01-01\n" + // another plan of the same service: new
            "Gone,5,2026-01-01\n" + // only a deleted one exists: new
            "Spotify,11.99,2026-01-01\n" +
            "Spotify,11.99,2026-01-01\n" + // repeated in the file
            "Broken,x,2026-01-01\n"
        val preview = CsvImport.preview(rows(text), existing)

        assertEquals(listOf("Netflix", "Gone", "Spotify"), preview.toAdd.map { it.name })
        assertEquals(listOf(2, 6), preview.duplicates.map { it.line })
        assertEquals(listOf(7), preview.invalid.map { it.line })
    }

    @Test
    fun `the same price in another currency is not a duplicate`() {
        val preview = CsvImport.preview(
            rows("Name,Amount,Currency,FirstBillDate\nNetflix,15.49,EUR,2026-01-01\n"),
            listOf(sub("Netflix", 15.49, currency = "USD"))
        )
        assertEquals(1, preview.toAdd.size)
    }

    // --------------------------------------------------------- tokens

    @Test
    fun `the delimiter is whichever the header uses, ignoring ones inside quotes`() {
        assertEquals(',', CsvImport.detectDelimiter("Name,Amount,Date"))
        assertEquals(';', CsvImport.detectDelimiter("Name;Amount;Date"))
        assertEquals('\t', CsvImport.detectDelimiter("Name\tAmount\tDate"))
        assertEquals(';', CsvImport.detectDelimiter("\"Name, full\";Amount;Date"))
    }

    // --------------------------------------------------- interface language

    @Test
    fun `a sheet headed in the interface language reads like an English one`() {
        val zh = options().copy(
            extraHeaderAliases = mapOf(
                "名称" to Field.NAME,
                "价格" to Field.AMOUNT,
                "首次扣费日期" to Field.FIRST_BILL_DATE,
                "计费周期" to Field.CYCLE
            )
        )
        val result = CsvImport.parse("名称,价格,首次扣费日期,计费周期\n奈飞,15.49,2026-03-01,每年\n", zh)
        val s = ((result as CsvImport.Result.Rows).rows.single() as CsvImport.Parsed).subscription
        assertEquals("奈飞", s.name)
        assertEquals(15.49, s.amount, 0.0)
        assertEquals(BillingCycle.ANNUALLY, s.cycle)
    }

    @Test
    fun `an imported row that names a preset looks like that preset`() {
        val presets = listOf(
            org.dpdns.alwaysup.subflow.domain.model.PresetService(
                id = "disney", name = "Disney+", category = "Streaming",
                brandColor = "#113CCF", iconUrl = "disney", defaultAmountUSD = 15.99
            )
        )
        val imported = parsed("Name,Amount,FirstBillDate\ndisney +,13.99,2026-01-01\nMy Gym,40,2026-01-01\n")
        val looks = imported.map { CsvImport.withPresetLooks(it, presets) }
        assertEquals("#113CCF", looks[0].colorHex)
        assertEquals("disney", looks[0].iconUrl)
        // Not a preset: left as it came.
        assertEquals(imported[1], looks[1])
    }
}
