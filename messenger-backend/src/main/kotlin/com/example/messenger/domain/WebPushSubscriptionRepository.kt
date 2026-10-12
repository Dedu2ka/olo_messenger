package com.example.messenger.domain

import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Modifying
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import java.util.UUID

interface WebPushSubscriptionRepository : CoroutineCrudRepository<WebPushSubscription, String> {
    @Modifying
    @Query("""INSERT INTO web_push_subscriptions (endpoint, user_id, p256dh_key, auth_secret, updated_at)
        VALUES (:endpoint, :userId::uuid, :p256dhKey, :authSecret, CURRENT_TIMESTAMP)
        ON CONFLICT (endpoint) DO UPDATE SET user_id = EXCLUDED.user_id,
        p256dh_key = EXCLUDED.p256dh_key, auth_secret = EXCLUDED.auth_secret,
        updated_at = CURRENT_TIMESTAMP""")
    suspend fun saveSubscription(endpoint: String, userId: UUID, p256dhKey: String, authSecret: String): Int

    @Query("SELECT * FROM web_push_subscriptions WHERE user_id = :userId::uuid")
    fun findAllByUserId(userId: UUID): Flow<WebPushSubscription>

    @Modifying
    @Query("DELETE FROM web_push_subscriptions WHERE endpoint = :endpoint AND user_id = :userId::uuid")
    suspend fun deleteForUser(endpoint: String, userId: UUID): Int
}
