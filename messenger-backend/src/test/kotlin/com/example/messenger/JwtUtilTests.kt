package com.example.messenger

import com.example.messenger.security.JwtUtil
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JwtUtilTests {
    private val jwt = JwtUtil("test-secret-that-is-more-than-thirty-two-bytes-long", 900_000, 2_592_000_000)

    @Test
    fun `access and refresh tokens cannot be substituted for one another`() {
        val access = jwt.generateToken("user-1", "alice")
        val refresh = jwt.generateRefreshToken("user-1", "alice")

        assertTrue(jwt.validateAccessToken(access))
        assertFalse(jwt.validateRefreshToken(access))
        assertTrue(jwt.validateRefreshToken(refresh))
        assertFalse(jwt.validateAccessToken(refresh))
    }
}
