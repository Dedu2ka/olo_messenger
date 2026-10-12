package com.example.messenger.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.UUID

@Table("chats")
data class Chat(
    @Id
    @Column("id")
    val id: UUID? = null,

    @Column("type")
    val type: String = "DIRECT",

    @Column("created_at")
    val createdAt: OffsetDateTime? = OffsetDateTime.now()
)