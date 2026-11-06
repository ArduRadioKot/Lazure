package com.rassvet.essential.data.chat

data class ChatSourceNote(
    val uri: String,
    val title: String,
    val snippet: String,
    val kind: Kind = Kind.VAULT,
) {
    enum class Kind {
        VAULT,
        PINNED,
    }
}

data class VaultContextBundle(
    val text: String,
    val sources: List<ChatSourceNote>,
)

data class ChatStreamResult(
    val text: String,
    val sources: List<ChatSourceNote> = emptyList(),
)


