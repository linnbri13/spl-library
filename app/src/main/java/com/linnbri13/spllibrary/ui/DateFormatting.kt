package com.linnbri13.spllibrary.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.text.Regex

internal fun parseLibraryLocalDate(raw: String?): LocalDate? {
    val trimmed = raw?.trim() ?: return null
    if (trimmed.isEmpty()) return null

    val isoRegex = Regex("^(\\d{4}-\\d{2}-\\d{2})")
    val match = isoRegex.find(trimmed)
    if (match != null) {
        val isoPart = match.groupValues[1]
        return try {
            LocalDate.parse(isoPart, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: Exception) {
            null
        }
    }

    return try {
        LocalDate.parse(trimmed, DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US))
    } catch (_: Exception) {
        null
    }
}

fun formatLibraryDate(raw: String?, locale: Locale = Locale.getDefault()): String {
    val parsed = parseLibraryLocalDate(raw) ?: return raw?.trim().orEmpty()
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", locale)
    return parsed.format(formatter)
}

enum class DueUrgency {
    OVERDUE,
    TODAY,
    SOON,
    NORMAL,
    UNKNOWN
}

data class DueStatus(
    val label: String,
    val urgency: DueUrgency
)

fun computeDueStatus(
    dueDate: String?,
    today: LocalDate = LocalDate.now(),
    locale: Locale = Locale.getDefault()
): DueStatus {
    val trimmed = dueDate?.trim()
    if (trimmed.isNullOrEmpty()) {
        return DueStatus("Due date unavailable", DueUrgency.UNKNOWN)
    }

    val parsed = parseLibraryLocalDate(dueDate)
    if (parsed == null) {
        return DueStatus("Due ${trimmed.take(10)}", DueUrgency.NORMAL)
    }

    val shortFormatter = DateTimeFormatter.ofPattern("MMM d", locale)
    val fullFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", locale)
    val formatted = if (parsed.year == today.year) {
        parsed.format(shortFormatter)
    } else {
        parsed.format(fullFormatter)
    }

    val days = ChronoUnit.DAYS.between(today, parsed)
    return when {
        days < 0 -> DueStatus("Overdue (${-days}d) • $formatted", DueUrgency.OVERDUE)
        days == 0L -> DueStatus("Due TODAY • $formatted", DueUrgency.TODAY)
        days <= 3L -> DueStatus("Due in ${days}d • $formatted", DueUrgency.SOON)
        else -> DueStatus("Due $formatted (${days}d)", DueUrgency.NORMAL)
    }
}
