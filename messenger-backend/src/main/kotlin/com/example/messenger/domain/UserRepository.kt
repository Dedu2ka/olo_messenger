package com.example.messenger.domain

import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserRepository : CoroutineCrudRepository<User, UUID> {
    suspend fun findByUsername(username: String): User?

    @Query("""
        SELECT * FROM users
        WHERE id <> :excludeUserId::uuid
          AND (
              POSITION(:query IN REPLACE(LOWER(username), 'ё', 'е')) > 0
              OR POSITION(:query IN REPLACE(LOWER(display_name), 'ё', 'е')) > 0
          )
        ORDER BY username
        LIMIT :limit OFFSET :offset
    """)
    fun searchByUsernameOrDisplayName(query: String, excludeUserId: UUID, limit: Int, offset: Int): Flow<User>
}
