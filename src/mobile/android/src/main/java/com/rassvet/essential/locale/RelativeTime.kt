package com.rassvet.essential.locale

import android.content.Context
import com.rassvet.essential.R
import java.text.DateFormat
import java.util.Date

fun formatRelativeTime(context: Context, ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000 -> context.getString(R.string.time_just_now)
        diff < 60 * 60_000 -> context.getString(R.string.time_minutes_ago, diff / 60_000)
        diff < 24 * 60 * 60_000 -> context.getString(R.string.time_hours_ago, diff / (60 * 60_000))
        diff < 7L * 24 * 60 * 60_000 -> context.getString(R.string.time_days_ago, diff / (24 * 60 * 60_000))
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ts))
    }
}


