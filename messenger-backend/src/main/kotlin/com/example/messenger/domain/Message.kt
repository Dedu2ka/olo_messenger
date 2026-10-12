package com.example.messenger.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Table("messages")
data class Message(
    @Id
    val id: UUID? = null,
    @Column("chat_id")
    val chatId: UUID,
    @Column("sender_id")
    val senderId: UUID,
    @Column("content")
    val content: String,
    @Column("status")
    val status: String = "SENT",
    @Column("created_at")
    val createdAt: OffsetDateTime? = OffsetDateTime.now(ZoneOffset.UTC),
    @Column("sent_at")
    val sentAt: OffsetDateTime? = OffsetDateTime.now(ZoneOffset.UTC),
    @Column("client_message_id")
    val clientMessageId: UUID? = null
)
