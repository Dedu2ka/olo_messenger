
package com.example.messenger.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.OffsetDateTime
import java.util.UUID

@Table("users")
data class User(
    @Id
    val id: UUID? = null,

    val username: String,

    @Column("password_hash")
    val passwordHash: String,

    @Column("display_name")
    val displayName: String? = null,

    @Column("avatar_url")
    val avatarUrl: String? = null,

    @Column("created_at")
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)