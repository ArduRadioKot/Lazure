package com.example.speech

import android.app.Application
import com.example.speech.data.SpeechRepository

class SpeechApp : Application() {
    val repository by lazy { SpeechRepository(this) }
}
