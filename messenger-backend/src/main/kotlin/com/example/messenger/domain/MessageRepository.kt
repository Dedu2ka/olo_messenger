package com.example.messenger.domain

import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.data.r2dbc.repository.Modifying
import java.util.UUID

interface MessageRepository : CoroutineCrudRepository<Message, UUID> {
    @Query("SELECT * FROM messages WHERE chat_id = :chatId ORDER BY sent_at ASC, id ASC")
    fun findAllByChatIdOrderBySentAtAsc(chatId: UUID): Flow<Message>

    @Query("SELECT * FROM messages WHERE client_message_id = :clientMessageId")
    suspend fun findByClientMessageId(clientMessageId: UUID): Message?

    @Query("SELECT * FROM messages WHERE content NOT LIKE 'enc:v1:%'")
    fun findLegacyUnencryptedMessages(): Flow<Message>

    @Query("SELECT * FROM messages WHERE chat_id = :chatId::uuid AND sender_id <> :userId::uuid AND status <> 'READ'")
    fun findIncomingMessagesNotRead(chatId: UUID, userId: UUID): Flow<Message>

    @Modifying
    @Query("UPDATE messages SET status = 'READ' WHERE chat_id = :chatId::uuid AND sender_id <> :userId::uuid AND status <> 'READ'")
    suspend fun markIncomingMessagesRead(chatId: UUID, userId: UUID): Int

    @Modifying
    @Query("UPDATE messages SET status = 'DELIVERED' WHERE id = :messageId::uuid AND chat_id = :chatId::uuid AND sender_id <> :recipientId::uuid AND status = 'SENT'")
    suspend fun markMessageDelivered(messageId: UUID, chatId: UUID, recipientId: UUID): Int
}
