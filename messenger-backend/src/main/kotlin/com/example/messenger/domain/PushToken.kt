package com.example.messenger.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.UUID

@Table("push_tokens")
data class PushToken(
    @Id
    val token: String,
    @Column("user_id")
    val userId: UUID,
    @Column("updated_at")
    val updatedAt: OffsetDateTime? = null
)
