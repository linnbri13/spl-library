package com.linnbri13.spllibrary

import com.linnbri13.spllibrary.data.formatCodeToLabel
import com.linnbri13.spllibrary.ui.DueUrgency
import com.linnbri13.spllibrary.ui.computeDueStatus
import com.linnbri13.spllibrary.ui.formatLibraryDate
import com.linnbri13.spllibrary.ui.parseLibraryLocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class SplCoreTests {

    @Test
    fun parseAndFormatIsoDate() {
        val date = parseLibraryLocalDate("2026-11-30")
        assertEquals(LocalDate.of(2026, 11, 30), date)
        assertEquals("Nov 30, 2026", formatLibraryDate("2026-11-30", Locale.US))
    }

    @Test
    fun parseAndFormatIsoDateTimeUtc() {
        val date = parseLibraryLocalDate("2026-11-30T00:00:00Z")
        assertEquals(LocalDate.of(2026, 11, 30), date)
        assertEquals("Nov 30, 2026", formatLibraryDate("2026-11-30T00:00:00Z", Locale.US))
    }

    @Test
    fun parseAndFormatIsoDateTimeOffset() {
        val date = parseLibraryLocalDate("2026-11-30T00:00:00-08:00")
        assertEquals(LocalDate.of(2026, 11, 30), date)
        assertEquals("Nov 30, 2026", formatLibraryDate("2026-11-30T00:00:00-08:00", Locale.US))
    }

    @Test
    fun parseAndFormatUsDate() {
        val date = parseLibraryLocalDate("11/30/2026")
        assertEquals(LocalDate.of(2026, 11, 30), date)
        assertEquals("Nov 30, 2026", formatLibraryDate("11/30/2026", Locale.US))
    }

    @Test
    fun parseEmptyOrNullReturnsEmpty() {
        assertEquals("", formatLibraryDate("", Locale.US))
        assertEquals("", formatLibraryDate(null, Locale.US))
    }

    @Test
    fun parseTbdReturnsTbd() {
        assertEquals("TBD", formatLibraryDate("TBD", Locale.US))
    }

    @Test
    fun computeDueStatusOverdue() {
        val today = LocalDate.of(2026, 11, 24)
        val result = computeDueStatus("2026-11-22", today, Locale.US)
        assertEquals(DueUrgency.OVERDUE, result.urgency)
        assertTrue(result.label.contains("Overdue (2d)"))
    }

    @Test
    fun computeDueStatusToday() {
        val today = LocalDate.of(2026, 11, 24)
        val result = computeDueStatus("2026-11-24", today, Locale.US)
        assertEquals(DueUrgency.TODAY, result.urgency)
        assertTrue(result.label.contains("Due TODAY"))
    }

    @Test
    fun computeDueStatusSoon() {
        val today = LocalDate.of(2026, 11, 24)
        val result = computeDueStatus("2026-11-27", today, Locale.US)
        assertEquals(DueUrgency.SOON, result.urgency)
        assertTrue(result.label.contains("Due in 3d"))
    }

    @Test
    fun computeDueStatusNormal() {
        val today = LocalDate.of(2026, 11, 24)
        val result = computeDueStatus("2026-12-10", today, Locale.US)
        assertEquals(DueUrgency.NORMAL, result.urgency)
        assertTrue(result.label.contains("16d"))
    }

    @Test
    fun computeDueStatusNull() {
        val today = LocalDate.of(2026, 11, 24)
        val result = computeDueStatus(null, today, Locale.US)
        assertEquals(DueUrgency.UNKNOWN, result.urgency)
    }

    @Test
    fun formatCodeToLabelKnownCodes() {
        assertEquals("Book", formatCodeToLabel("BK"))
        assertEquals("eBook", formatCodeToLabel("EBOOK"))
        assertEquals("Large Print", formatCodeToLabel("LARGEPRINT"))
    }

    @Test
    fun formatCodeToLabelNullAndCustom() {
        assertEquals("Item", formatCodeToLabel(null))
        assertEquals("Custom format", formatCodeToLabel("CUSTOM_FORMAT"))
    }
}
