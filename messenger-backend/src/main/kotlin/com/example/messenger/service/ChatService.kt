package com.example.messenger.service

import com.example.messenger.domain.Chat
import com.example.messenger.domain.ChatRepository
import com.example.messenger.domain.UserRepository
import kotlinx.coroutines.flow.toList
import org.springframework.security.access.AccessDeniedException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class ChatService(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val advisoryLockService: DatabaseAdvisoryLockService
) {
    @Transactional
    suspend fun getOrCreateDirectChat(user1: UUID, user2: UUID): Chat {
        if (user1 == user2) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "A direct chat requires two different users")
        }
        if (userRepository.findById(user1) == null || userRepository.findById(user2) == null) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Chat participant not found")
        }
        val lockKey = listOf(user1.toString(), user2.toString()).sorted().joinToString(":")
        advisoryLockService.lock("direct-chat:$lockKey")

        val existing = chatRepository.findDirectChat(user1, user2)
        if (existing != null) {
            return existing
        }

        val savedChat = chatRepository.save(Chat(type = "DIRECT"))
        val savedChatId = savedChat.id
            ?: throw IllegalStateException("Chat ID was not generated after saving chat")
        chatRepository.addUserToChat(savedChatId, user1)
        chatRepository.addUserToChat(savedChatId, user2)

        return savedChat
    }

    suspend fun findDirectChat(user1: UUID, user2: UUID): Chat? {
        if (user1 == user2) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "A direct chat requires two different users")
        }
        if (userRepository.findById(user1) == null || userRepository.findById(user2) == null) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Chat participant not found")
        }
        return chatRepository.findDirectChat(user1, user2)
    }

    suspend fun ensureChatMember(chatId: UUID, userId: UUID) {
        val memberIds = chatRepository.findUserIdsByChatId(chatId).toList()
        if (userId !in memberIds) {
            throw AccessDeniedException("User is not a member of this chat")
        }
    }
}
