package com.example.messenger.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.UUID

@Table("web_push_subscriptions")
data class WebPushSubscription(
    @Id val endpoint: String,
    val userId: UUID,
    val p256dhKey: String,
    val authSecret: String,
    val updatedAt: OffsetDateTime? = null
)
