package com.rassvet.essential.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.net.Uri
import com.rassvet.essential.data.chat.ChatAttachment
import com.rassvet.essential.data.chat.PersistedAttachment
import com.rassvet.essential.data.chat.ChatSourceNote
import com.rassvet.essential.ui.PendingNoteContext


class HomeChatState {
    val messages = mutableStateListOf<ChatLine>()
    var nextId by mutableLongStateOf(0L)
    var currentSessionId by mutableLongStateOf(0L)
    var draftText by mutableStateOf("")
    val attachments = mutableStateListOf<ChatAttachment>()
    var activeNoteContext by mutableStateOf<PendingNoteContext?>(null)

    fun startNewChat() {
        messages.clear()
        currentSessionId = 0L
        draftText = ""
        attachments.clear()
        activeNoteContext = null
        typewriterDoneIds = emptySet()
    }

    var typewriterDoneIds by mutableStateOf<Set<Long>>(emptySet())

    fun markTypewriterDone(id: Long) {
        if (id !in typewriterDoneIds) {
            typewriterDoneIds = typewriterDoneIds + id
        }
    }
}

data class ChatLine(
    val id: Long,
    val isUser: Boolean,
    val text: String,
    val attachments: List<PersistedAttachment> = emptyList(),
    val noteSuggestion: NoteEditSuggestion? = null,
    val noteCreateSuggestion: NoteCreateSuggestion? = null,
    val createdNoteUri: Uri? = null,
    val createdNoteTitle: String? = null,
    val sources: List<ChatSourceNote> = emptyList(),

    val streamComplete: Boolean = true,

    val animateEnter: Boolean = false,
)

data class ChatSessionUi(
    val id: Long,
    val title: String,
    val lastUpdatedAt: Long,
)


