package com.example.messenger.service

import com.example.messenger.domain.Message
import com.example.messenger.domain.MessageRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

data class MessageSendResult(val message: Message, val created: Boolean)

@Service
class MessageService(
    private val messageRepository: MessageRepository,
    private val chatService: ChatService,
    private val messageEncryptionService: MessageEncryptionService
) {
    @Transactional
    suspend fun sendToChat(
        chatId: UUID,
        senderId: UUID,
        content: String,
        sentAt: OffsetDateTime? = null,
        clientMessageId: UUID? = null
    ): MessageSendResult {
        validateContent(content)
        findExisting(clientMessageId, senderId)?.let { existing ->
            if (existing.chatId != chatId) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Client message ID was already used")
            }
            chatService.ensureChatMember(chatId, senderId)
            return MessageSendResult(existing, false)
        }
        chatService.ensureChatMember(chatId, senderId)
        return MessageSendResult(save(chatId, senderId, content, sentAt, clientMessageId), true)
    }

    @Transactional
    suspend fun sendDirect(
        senderId: UUID,
        recipientId: UUID,
        content: String,
        sentAt: OffsetDateTime? = null,
        clientMessageId: UUID? = null
    ): MessageSendResult {
        validateContent(content)
        findExisting(clientMessageId, senderId)?.let { existing ->
            chatService.ensureChatMember(existing.chatId, senderId)
            chatService.ensureChatMember(existing.chatId, recipientId)
            return MessageSendResult(existing, false)
        }
        val chat = chatService.getOrCreateDirectChat(senderId, recipientId)
        val chatId = chat.id ?: throw IllegalStateException("Chat ID was not generated after saving chat")
        return MessageSendResult(save(chatId, senderId, content, sentAt, clientMessageId), true)
    }

    private fun validateContent(content: String) {
        if (content.isBlank() || content.length > MAX_MESSAGE_LENGTH) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Message content must be between 1 and $MAX_MESSAGE_LENGTH characters"
            )
        }
    }

    private suspend fun findExisting(clientMessageId: UUID?, senderId: UUID): Message? {
        if (clientMessageId == null) return null
        val existing = messageRepository.findByClientMessageId(clientMessageId) ?: return null
        if (existing.senderId != senderId) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Client message ID was already used")
        }
        return existing.copy(content = messageEncryptionService.decrypt(existing.content))
    }

    private suspend fun save(
        chatId: UUID,
        senderId: UUID,
        content: String,
        sentAt: OffsetDateTime?,
        clientMessageId: UUID?
    ): Message {
        val encrypted = messageRepository.save(
            Message(
                chatId = chatId,
                senderId = senderId,
                content = messageEncryptionService.encrypt(content),
                status = "SENT",
                sentAt = sentAt?.withOffsetSameInstant(ZoneOffset.UTC) ?: OffsetDateTime.now(ZoneOffset.UTC),
                clientMessageId = clientMessageId
            )
        )
        return encrypted.copy(content = content)
    }

    private companion object {
        const val MAX_MESSAGE_LENGTH = 10_000
    }
}
