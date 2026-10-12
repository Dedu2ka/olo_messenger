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
        WHERE POSITION(LOWER(:query) IN LOWER(username)) > 0
           OR POSITION(LOWER(:query) IN LOWER(display_name)) > 0
        ORDER BY username
        LIMIT :limit OFFSET :offset
    """)
    fun searchByUsernameOrDisplayName(query: String, limit: Int, offset: Int): Flow<User>
}
