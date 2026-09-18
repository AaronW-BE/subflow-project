package org.dpdns.alwaysup.subflow

import org.dpdns.alwaysup.subflow.domain.model.BillingCycle
import org.dpdns.alwaysup.subflow.domain.model.Subscription
import org.dpdns.alwaysup.subflow.domain.util.ExportUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The spending report a Pro user opens in a spreadsheet.
 *
 * Everything here is about the file surviving the trip into Excel, Sheets or
 * Numbers intact: its encoding, its quoting and its numbers.
 */
class CsvExportTest {

    private fun sub(
        name: String = "Netflix",
        amount: Double = 15.49,
        currency: String = "USD",
        cycle: BillingCycle = BillingCycle.MONTHLY,
        notes: String = ""
    ) = Subscription(
        id = name,
        name = name,
        category = "Streaming",
        amount = amount,
        currency = currency,
        cycle = cycle,
        firstBillDate = "2026-06-14",
        nextBillDate = "2026-10-14",
        notes = notes
    )

    private fun csv(vararg subs: Subscription, home: String = "USD") =
        ExportUtils.buildCsv(subs.toList(), home)

    /** Data rows only: the BOM and the header line stripped off. */
    private fun rows(text: String) =
        text.removePrefix(ExportUtils.UTF8_BOM).trimEnd('\n').split('\n').drop(1)

    // ------------------------------------------------------------- encoding

    @Test
    fun `the file opens with a UTF-8 byte-order mark`() {
        // What makes Excel read it as UTF-8 instead of the system code page.
        val out = csv(sub())
        assertTrue(out.startsWith(ExportUtils.UTF8_BOM))

        val bytes = out.toByteArray(Charsets.UTF_8)
        assertEquals(0xEF.toByte(), bytes[0])
        assertEquals(0xBB.toByte(), bytes[1])
        assertEquals(0xBF.toByte(), bytes[2])
    }

    @Test
    fun `there is exactly one mark, and the header follows it`() {
        val out = csv(sub(), sub(name = "Spotify"))
        assertEquals(1, out.count { it == '﻿' })
        assertTrue(out.removePrefix(ExportUtils.UTF8_BOM).startsWith("Name,Category,"))
    }

    @Test
    fun `names that are not Latin come through unchanged`() {
        val out = csv(sub(name = "爱奇艺 VIP"), sub(name = "Café Crème", notes = "家族で共有"))
        assertTrue(out.contains("\"爱奇艺 VIP\""))
        assertTrue(out.contains("\"Café Crème\""))
        assertTrue(out.contains("\"家族で共有\""))
    }

    // -------------------------------------------------------------- quoting

    @Test
    fun `commas and quotes inside a name are escaped, not split`() {
        val row = rows(csv(sub(name = "Netflix, \"Premium\""))).single()
        assertTrue(row.startsWith("\"Netflix, \"\"Premium\"\"\","))
    }

    @Test
    fun `a line break in the notes stays inside its quotes`() {
        val out = csv(sub(notes = "shared with\nthe family"))
        assertTrue(out.contains("\"shared with\nthe family\""))
    }

    // -------------------------------------------------------------- numbers

    @Test
    fun `amounts use a dot even where the language writes a comma`() {
        // A German phone would otherwise write 15,49 and split the column.
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val row = rows(csv(sub(amount = 1234.5))).single()
            assertTrue(row.contains(",1234.50,USD,"))
            assertFalse(row.contains("1234,50"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `a yearly plan is written at its charge and its monthly equivalent`() {
        val row = rows(csv(sub(name = "Notion Plus", amount = 96.0, cycle = BillingCycle.ANNUALLY))).single()
        assertTrue(row.contains(",96.00,USD,annually,8.00,8.00,"))
    }
}
