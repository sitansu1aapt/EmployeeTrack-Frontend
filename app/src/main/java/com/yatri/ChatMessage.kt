package com.yatri

data class ChatMessage(
    val id: String,
    val text: String,
    val isSentByMe: Boolean,
    val timestamp: Long,
    val isDeleted: Boolean = false,
)
