package com.example.messenger.dto

data class CreateChatRequest(
    val currentUserId: String,
    val targetUserId: String
)

data class SendMessageRequest(
    val senderId: String,
    val content: String,
    val sentAt: String? = null,
    val clientMessageId: String? = null
)

data class SendDirectMessageRequest(
    val targetUserId: String,
    val content: String,
    val sentAt: String? = null,
    val clientMessageId: String? = null
)

data class ChatDto(
    val id: String,
    val type: String,
    val partnerName: String?,
    val partnerDisplayName: String? = null,
    val partnerUsername: String? = null,
    val partnerAvatarUrl: String? = null,
    val lastMessage: String? = null,
    val lastMessageAt: String? = null,
    val lastMessageSenderId: String? = null,
    val unreadCount: Long = 0
)
