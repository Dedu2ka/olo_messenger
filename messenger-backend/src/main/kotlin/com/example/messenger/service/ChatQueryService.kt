package com.example.messenger.service

import com.example.messenger.domain.ChatRepository
import com.example.messenger.domain.Message
import com.example.messenger.domain.MessageRepository
import com.example.messenger.domain.UserRepository
import com.example.messenger.dto.ChatDto
import com.example.messenger.dto.MessageDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ChatQueryService(
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository,
    private val chatService: ChatService,
    private val messageEncryptionService: MessageEncryptionService
) {
    suspend fun findDirectChat(userId: UUID, partnerId: UUID) = chatService.findDirectChat(userId, partnerId)

    suspend fun getPartnerDetails(userId: UUID): PartnerDetails? {
        val partner = userRepository.findById(userId) ?: return null
        return PartnerDetails(
            name = partner.displayName?.takeIf(String::isNotBlank) ?: partner.username,
            avatarUrl = partner.avatarUrl
        )
    }

    suspend fun getChats(userId: UUID): List<ChatDto> =
        chatRepository.findChatSummariesByUserId(userId).toList().map { chat ->
            ChatDto(
                id = chat.id.toString(),
                type = chat.type,
                partnerName = chat.partnerName ?: "Чат",
                partnerAvatarUrl = chat.partnerAvatarUrl,
                lastMessage = chat.lastMessage?.let(messageEncryptionService::decrypt),
                lastMessageAt = chat.lastMessageAt?.toString(),
                unreadCount = chat.unreadCount
            )
        }

    suspend fun getMessages(chatId: UUID, userId: UUID): Flow<MessageDto> {
        chatService.ensureChatMember(chatId, userId)
        return messageRepository.findAllByChatIdOrderBySentAtAsc(chatId).map { it.toDecryptedDto() }
    }

    private fun Message.toDecryptedDto(): MessageDto =
        MessageDto.from(copy(content = messageEncryptionService.decrypt(content)))
}

data class PartnerDetails(
    val name: String,
    val avatarUrl: String?
)
