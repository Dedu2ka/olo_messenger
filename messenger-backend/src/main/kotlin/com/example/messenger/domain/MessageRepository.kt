package com.example.messenger.domain

import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.data.r2dbc.repository.Modifying
import java.util.UUID

interface MessageRepository : CoroutineCrudRepository<Message, UUID> {
    @Query("""
        SELECT m.*
        FROM messages m
        WHERE m.chat_id = :chatId::uuid
          AND (
              :beforeMessageId IS NULL
              OR (m.sent_at, m.id) < (
                  SELECT cursor.sent_at, cursor.id
                  FROM messages cursor
                  WHERE cursor.id = CAST(:beforeMessageId AS UUID)
                    AND cursor.chat_id = :chatId::uuid
              )
          )
        ORDER BY m.sent_at DESC, m.id DESC
        LIMIT :limit
    """)
    fun findMessagePage(chatId: UUID, beforeMessageId: UUID?, limit: Int): Flow<Message>

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
