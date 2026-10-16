package com.flambo.recorder.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

fun formatDuration(ms: Long): String {
    // Hand-rolled rather than String.format: this runs inside composables that
    // recompose on every playback tick, and String.format allocates a Formatter
    // (plus boxing) on every call.
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        buildString {
            append(h)
            append(':')
            appendTwoDigits(m)
            append(':')
            appendTwoDigits(s)
        }
    } else {
        buildString {
            appendTwoDigits(m)
            append(':')
            appendTwoDigits(s)
        }
    }
}

private fun StringBuilder.appendTwoDigits(value: Long) {
    if (value < 10) append('0')
    append(value)
}

fun formatRelativeTime(epochMs: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - epochMs
    val mins = TimeUnit.MILLISECONDS.toMinutes(diff)
    if (mins < 1) return "Just now"
    if (mins < 60) return "$mins min ago"
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    if (hours < 24) return "$hours hr ago"
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    if (days < 7) return "$days day${if (days > 1) "s" else ""} ago"
    return dateFormatFor(Locale.getDefault()).format(Date(epochMs))
}

// Cached per thread + locale: the previous code allocated a SimpleDateFormat on
// every call, and this runs once per recording card.
private val dateFormatHolder = ThreadLocal<Pair<Locale, SimpleDateFormat>>()

private fun dateFormatFor(locale: Locale): SimpleDateFormat {
    val current = dateFormatHolder.get()
    if (current != null && current.first == locale) return current.second
    val created = SimpleDateFormat("MMM d, yyyy", locale)
    dateFormatHolder.set(locale to created)
    return created
}

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.getDefault(), "%.1f MB", mb)
}
