package com.example.messenger.domain

import org.springframework.data.r2dbc.repository.Modifying
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.data.relational.core.mapping.Column
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import java.time.OffsetDateTime

data class ChatSummary(
    val id: UUID,
    val type: String,
    @Column("partner_name")
    val partnerName: String?,
    @Column("partner_avatar_url")
    val partnerAvatarUrl: String?,
    @Column("last_message")
    val lastMessage: String?,
    @Column("last_message_at")
    val lastMessageAt: OffsetDateTime?,
    @Column("unread_count")
    val unreadCount: Long
)

interface ChatRepository : CoroutineCrudRepository<Chat, UUID> {

    @Query("""
        SELECT c.* FROM chats c
        JOIN chat_members cp1 ON c.id = cp1.chat_id
        JOIN chat_members cp2 ON c.id = cp2.chat_id
        WHERE c.type = 'DIRECT' 
          AND cp1.user_id = :user1::uuid 
          AND cp2.user_id = :user2::uuid
        LIMIT 1
    """)
    suspend fun findDirectChat(user1: UUID, user2: UUID): Chat?

    @Query("""
        SELECT c.id AS id,
               c.type AS type,
               COALESCE(NULLIF(BTRIM(p.display_name), ''), p.username, 'Чат') AS partner_name,
               p.avatar_url AS partner_avatar_url,
               latest.content AS last_message,
               latest.created_at AS last_message_at,
               COALESCE((
                   SELECT COUNT(*)
                   FROM messages unread
                   WHERE unread.chat_id = c.id
                     AND unread.sender_id <> :userId::uuid
                     AND unread.created_at > current_member.last_read_at
               ), 0)::BIGINT AS unread_count
        FROM chats c
        JOIN chat_members current_member
          ON current_member.chat_id = c.id AND current_member.user_id = :userId::uuid
        LEFT JOIN LATERAL (
            SELECT u.display_name, u.username, u.avatar_url
            FROM chat_members other_member
            JOIN users u ON u.id = other_member.user_id
            WHERE other_member.chat_id = c.id AND other_member.user_id <> :userId::uuid
            ORDER BY other_member.user_id
            LIMIT 1
        ) p ON TRUE
        LEFT JOIN LATERAL (
            SELECT m.content, m.sent_at AS created_at
            FROM messages m
            WHERE m.chat_id = c.id
            ORDER BY m.sent_at DESC, m.id DESC
            LIMIT 1
        ) latest ON TRUE
        WHERE EXISTS (
            SELECT 1 FROM messages first_message WHERE first_message.chat_id = c.id
        )
        ORDER BY COALESCE(latest.created_at, c.created_at) DESC, c.id
    """)
    fun findChatSummariesByUserId(userId: UUID): Flow<ChatSummary>

    // Добавили этот метод для получения списка user_id участников чата
    @Query("""
        SELECT user_id FROM chat_members WHERE chat_id = :chatId::uuid
    """)
    fun findUserIdsByChatId(chatId: UUID): Flow<UUID>

    @Modifying
    @Query("INSERT INTO chat_members (chat_id, user_id) VALUES (:chatId::uuid, :userId::uuid)")
    suspend fun addUserToChat(chatId: UUID, userId: UUID): Int

    @Modifying
    @Query("UPDATE chat_members SET last_read_at = CURRENT_TIMESTAMP WHERE chat_id = :chatId::uuid AND user_id = :userId::uuid")
    suspend fun markChatRead(chatId: UUID, userId: UUID): Int
}
