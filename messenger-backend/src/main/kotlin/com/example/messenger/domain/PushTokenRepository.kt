package com.example.messenger.domain

import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Modifying
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface PushTokenRepository : CoroutineCrudRepository<PushToken, String> {
    @Modifying
    @Query("INSERT INTO push_tokens (token, user_id, updated_at) VALUES (:token, :userId::uuid, CURRENT_TIMESTAMP) ON CONFLICT (token) DO UPDATE SET user_id = EXCLUDED.user_id, updated_at = CURRENT_TIMESTAMP")
    suspend fun registerToken(token: String, userId: UUID): Int

    @Query("SELECT token FROM push_tokens WHERE user_id = :userId::uuid")
    fun findTokensByUserId(userId: UUID): Flow<String>

    @Modifying
    @Query("DELETE FROM push_tokens WHERE token = :token AND user_id = :userId::uuid")
    suspend fun deleteToken(token: String, userId: UUID): Int
}
