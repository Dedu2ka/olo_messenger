package com.example.messenger.service

import com.example.messenger.domain.ChatRepository
import com.example.messenger.domain.MessageRepository
import com.example.messenger.websocket.WebSocketSessionManager
import com.example.messenger.websocket.WsMessageStatusEvent
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ChatReadService(
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val chatService: ChatService,
    private val webSocketSessionManager: WebSocketSessionManager
) {
    private val objectMapper = jacksonObjectMapper()

    suspend fun markRead(chatId: UUID, userId: UUID) {
        chatService.ensureChatMember(chatId, userId)
        val unreadMessages = messageRepository.findIncomingMessagesNotRead(chatId, userId).toList()
        chatRepository.markChatRead(chatId, userId)
        messageRepository.markIncomingMessagesRead(chatId, userId)
        unreadMessages.forEach { message ->
            message.id?.let { messageId ->
                webSocketSessionManager.sendToUser(
                    message.senderId.toString(),
                    objectMapper.writeValueAsString(
                        WsMessageStatusEvent(messageId.toString(), chatId.toString(), "READ")
                    )
                )
            }
        }
    }
}
