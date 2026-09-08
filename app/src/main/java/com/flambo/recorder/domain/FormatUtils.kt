package com.flambo.recorder.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    else String.format(Locale.getDefault(), "%02d:%02d", m, s)
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
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(epochMs))
}

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.getDefault(), "%.1f MB", mb)
}
