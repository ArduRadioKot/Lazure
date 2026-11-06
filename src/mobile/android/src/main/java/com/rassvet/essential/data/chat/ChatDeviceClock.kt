package com.rassvet.essential.data.chat

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ChatDeviceClock {
    fun systemDateTimeLine(): String {
        val locale = Locale.forLanguageTag("ru")
        val tz = TimeZone.getDefault()
        val fmt = SimpleDateFormat("EEEE, d MMMM yyyy, HH:mm", locale)
        fmt.timeZone = tz
        val formatted = fmt.format(Date())
        return "Текущие дата и время на устройстве пользователя: $formatted (${tz.id}). " +
            "Используй их, если вопрос про «сегодня», «сейчас», день недели или время суток."
    }
}


