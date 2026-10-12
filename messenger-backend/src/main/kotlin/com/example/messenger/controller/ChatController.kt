package com.example.messenger.controller

import com.example.messenger.dto.ChatDto
import com.example.messenger.dto.CreateChatRequest
import com.example.messenger.dto.MessageDto
import com.example.messenger.dto.SendDirectMessageRequest
import com.example.messenger.dto.SendMessageRequest
import com.example.messenger.security.AuthenticatedUser
import com.example.messenger.service.ChatQueryService
import com.example.messenger.service.ChatReadService
import com.example.messenger.service.ChatService
import com.example.messenger.service.MessageDeliveryService
import kotlinx.coroutines.flow.Flow
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@RestController
@RequestMapping("/api/v1/chats")
class ChatController(
    private val chatService: ChatService,
    private val chatQueryService: ChatQueryService,
    private val chatReadService: ChatReadService,
    private val messageDeliveryService: MessageDeliveryService
) {
    @PostMapping("/direct")
    suspend fun getOrCreateChat(
        @RequestBody request: CreateChatRequest,
        @AuthenticationPrincipal principalId: String
    ): ChatDto {
        val userId = AuthenticatedUser.requireId(principalId, request.currentUserId)
        val partnerId = AuthenticatedUser.parseUuid(request.targetUserId)
        val chat = chatService.getOrCreateDirectChat(userId, partnerId)
        val partner = chatQueryService.getPartnerDetails(partnerId)
        return ChatDto(
            id = requireNotNull(chat.id) { "Chat ID is missing" }.toString(),
            type = chat.type,
            partnerName = partner?.name ?: "Чат",
            partnerAvatarUrl = partner?.avatarUrl
        )
    }

    @GetMapping("/direct/{targetUserId}")
    suspend fun findDirectChat(
        @PathVariable targetUserId: String,
        @AuthenticationPrincipal principalId: String
    ): ResponseEntity<ChatDto> {
        val userId = AuthenticatedUser.requireId(principalId)
        val partnerId = AuthenticatedUser.parseUuid(targetUserId)
        val chat = chatQueryService.findDirectChat(userId, partnerId)
            ?: return ResponseEntity.notFound().build()
        val partner = chatQueryService.getPartnerDetails(partnerId)
        return ResponseEntity.ok(
            ChatDto(
                id = requireNotNull(chat.id) { "Chat ID is missing" }.toString(),
                type = chat.type,
                partnerName = partner?.name ?: "Чат",
                partnerAvatarUrl = partner?.avatarUrl
            )
        )
    }

    @GetMapping
    suspend fun getUserChats(
        @RequestParam userId: String,
        @AuthenticationPrincipal principalId: String
    ): List<ChatDto> = chatQueryService.getChats(AuthenticatedUser.requireId(principalId, userId))

    @PostMapping("/{chatId}/read")
    suspend fun markChatRead(
        @PathVariable chatId: String,
        @AuthenticationPrincipal principalId: String
    ) {
        chatReadService.markRead(
            AuthenticatedUser.parseUuid(chatId),
            AuthenticatedUser.requireId(principalId)
        )
    }

    @GetMapping("/{chatId}/messages")
    suspend fun getMessages(
        @PathVariable chatId: String,
        @AuthenticationPrincipal principalId: String
    ): Flow<MessageDto> = chatQueryService.getMessages(
        AuthenticatedUser.parseUuid(chatId),
        AuthenticatedUser.requireId(principalId)
    )

    @PostMapping("/{chatId}/messages")
    suspend fun sendMessage(
        @PathVariable chatId: String,
        @RequestBody request: SendMessageRequest,
        @AuthenticationPrincipal principalId: String
    ): MessageDto = messageDeliveryService.sendToChat(
        chatId = AuthenticatedUser.parseUuid(chatId),
        senderId = AuthenticatedUser.requireId(principalId, request.senderId),
        content = request.content,
        sentAt = parseSentAt(request.sentAt),
        clientMessageId = parseClientMessageId(request.clientMessageId)
    )

    @PostMapping("/direct/messages")
    suspend fun sendDirectMessage(
        @RequestBody request: SendDirectMessageRequest,
        @AuthenticationPrincipal principalId: String
    ): MessageDto = messageDeliveryService.sendDirect(
        senderId = AuthenticatedUser.requireId(principalId),
        recipientId = AuthenticatedUser.parseUuid(request.targetUserId),
        content = request.content,
        sentAt = parseSentAt(request.sentAt),
        clientMessageId = parseClientMessageId(request.clientMessageId)
    )

    private fun parseSentAt(value: String?): OffsetDateTime? = value?.let {
        try {
            OffsetDateTime.parse(it).withOffsetSameInstant(ZoneOffset.UTC)
        } catch (exception: java.time.format.DateTimeParseException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "sentAt must be an ISO-8601 timestamp", exception)
        }
    }

    private fun parseClientMessageId(value: String?): UUID? = value?.let {
        try {
            UUID.fromString(it)
        } catch (exception: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "clientMessageId must be a UUID", exception)
        }
    }
}
