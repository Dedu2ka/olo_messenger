package com.example.messenger.service

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Service

@Service
class DatabaseAdvisoryLockService(
    private val databaseClient: DatabaseClient
) {
    suspend fun lock(key: String) {
        databaseClient.sql("""
            WITH lock_acquired AS (
                SELECT pg_advisory_xact_lock(hashtextextended(:lockKey, 0))
            )
            SELECT TRUE AS locked FROM lock_acquired
        """)
            .bind("lockKey", key)
            .fetch()
            .first()
            .awaitSingle()
    }
}
