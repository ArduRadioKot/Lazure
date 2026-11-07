package com.example.speech.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.speech.data.SpeechRepository
import com.example.speech.domain.Segment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TranscriptViewModel(private val repo: SpeechRepository) : ViewModel() {

    val transcript: StateFlow<List<Segment>> =
        repo.segments
            .runningFold(emptyList<Segment>()) { acc, s -> acc + s }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _status = MutableStateFlow("Остановлено")
    val status: StateFlow<String> = _status

    fun setStatus(s: String) { _status.value = s }

    fun start() {
        viewModelScope.launch(Dispatchers.Default) {
            _status.value = if (repo.start()) "Запись…" else "Ошибка: проверьте модели в assets/models"
        }
    }

    fun stop() {
        viewModelScope.launch(Dispatchers.Default) { repo.stop(); _status.value = "Остановлено" }
    }

    class Factory(private val repo: SpeechRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TranscriptViewModel(repo) as T
    }
}
