package com.example.messenger.service

import com.example.messenger.domain.ChatRepository
import com.example.messenger.domain.Message
import com.example.messenger.domain.MessageRepository
import com.example.messenger.dto.MessageDto
import com.example.messenger.websocket.WebSocketSessionManager
import com.example.messenger.websocket.WsMessageStatusEvent
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.flow.toList
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.util.UUID

/** Coordinates persistence and delivery for messages received over REST or WebSocket. */
@Service
class MessageDeliveryService(
    private val messageService: MessageService,
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val webSocketSessionManager: WebSocketSessionManager,
    private val pushNotificationService: PushNotificationService
) {
    private val objectMapper = jacksonObjectMapper()

    suspend fun sendToChat(
        chatId: UUID,
        senderId: UUID,
        content: String,
        sentAt: OffsetDateTime? = null,
        clientMessageId: UUID? = null
    ): MessageDto {
        val result = messageService.sendToChat(chatId, senderId, content, sentAt, clientMessageId)
        return if (result.created) deliver(result.message) else MessageDto.from(result.message)
    }

    suspend fun sendDirect(
        senderId: UUID,
        recipientId: UUID,
        content: String,
        sentAt: OffsetDateTime? = null,
        clientMessageId: UUID? = null
    ): MessageDto {
        val result = messageService.sendDirect(senderId, recipientId, content, sentAt, clientMessageId)
        return if (result.created) deliver(result.message) else MessageDto.from(result.message)
    }

    private suspend fun deliver(message: Message): MessageDto {
        val senderId = message.senderId
        val recipients = chatRepository.findUserIdsByChatId(message.chatId)
            .toList()
            .filter { it != senderId }
        val payload = objectMapper.writeValueAsString(MessageDto.from(message))

        webSocketSessionManager.sendToUser(senderId.toString(), payload)

        var deliveredToAnyRecipient = false
        for (recipientId in recipients) {
            val queuedForRecipient = webSocketSessionManager.sendToUser(recipientId.toString(), payload)
            if (queuedForRecipient) {
                message.id?.let { messageRepository.markMessageDelivered(it, message.chatId, recipientId) }
                deliveredToAnyRecipient = true
            }
            pushNotificationService.notifyUser(recipientId, message.chatId, senderId, message.content)
        }

        if (deliveredToAnyRecipient) {
            val messageId = message.id?.toString().orEmpty()
            webSocketSessionManager.sendToUser(
                senderId.toString(),
                objectMapper.writeValueAsString(
                    WsMessageStatusEvent(messageId, message.chatId.toString(), "DELIVERED")
                )
            )
        }

        return MessageDto.from(if (deliveredToAnyRecipient) message.copy(status = "DELIVERED") else message)
    }
}
