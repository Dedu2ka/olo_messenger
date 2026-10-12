package com.example.messenger.dto

import com.example.messenger.domain.Message
import com.fasterxml.jackson.annotation.JsonProperty

data class MessageDto(
    val type: String = "NEW_MESSAGE",
    val id: String,
    val chatId: String,
    val senderId: String,
    val content: String,
    val status: String,
    val clientMessageId: String? = null,
    @JsonProperty("timestamp")
    val createdAt: String
) {
    companion object {
        fun from(message: Message) = MessageDto(
            id = message.id?.toString() ?: "",
            chatId = message.chatId.toString(),
            senderId = message.senderId.toString(),
            content = message.content,
            status = message.status,
            clientMessageId = message.clientMessageId?.toString(),
            createdAt = (message.sentAt ?: message.createdAt)?.toInstant()?.toString() ?: ""
        )
    }
}
