package com.shfileexplorer.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "—"
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (value >= 100) {
        String.format(Locale.getDefault(), "%.0f %s", value, units[unit])
    } else {
        String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
    }
}

fun formatDate(timestamp: Long): String {
    if (timestamp <= 0L) return "Unknown"
    return SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestamp))
}

fun formatCount(count: Int, singular: String, plural: String = "${singular}s"): String =
    "$count ${if (count == 1) singular else plural}"
