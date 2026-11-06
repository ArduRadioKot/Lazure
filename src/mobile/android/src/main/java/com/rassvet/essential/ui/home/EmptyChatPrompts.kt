package com.rassvet.essential.ui.home

import android.content.res.Resources
import com.rassvet.essential.R
import java.util.Calendar

fun randomEmptyChatPrompt(resources: Resources): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val arrayId =
        when (hour) {
            in 5..10 -> R.array.home_empty_chat_prompts_morning
            in 11..16 -> R.array.home_empty_chat_prompts_day
            in 17..21 -> R.array.home_empty_chat_prompts_evening
            else -> R.array.home_empty_chat_prompts_night
        }
    return resources.getStringArray(arrayId).random()
}

fun pickEmptyChatQuickPrompts(resources: Resources, count: Int = 3): List<String> {
    val pool = resources.getStringArray(R.array.home_empty_chat_quick_prompts)
    if (pool.size <= count) return pool.toList()
    return pool.toList().shuffled().take(count)
}


