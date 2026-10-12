package com.example.messenger.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Service
class MessageEncryptionService(
    @Value("\${app.message-encryption-key-base64}") encodedKey: String
) {
    private val keyBytes = try {
        Base64.getDecoder().decode(encodedKey)
    } catch (exception: IllegalArgumentException) {
        throw IllegalStateException("MESSAGE_ENCRYPTION_KEY_BASE64 must be valid Base64", exception)
    }

    private val key = SecretKeySpec(keyBytes, "AES")
    private val random = SecureRandom()

    init {
        require(keyBytes.size == 32) {
            "MESSAGE_ENCRYPTION_KEY_BASE64 must decode to exactly 32 bytes"
        }
    }

    fun encrypt(plaintext: String): String {
        val iv = ByteArray(IV_LENGTH).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val payload = Base64.getEncoder().encodeToString(iv + ciphertext)
        return "$PREFIX$payload"
    }

    fun decrypt(value: String): String {
        if (!isEncrypted(value)) return value

        val payload = try {
            Base64.getDecoder().decode(value.removePrefix(PREFIX))
        } catch (exception: IllegalArgumentException) {
            throw IllegalStateException("Stored message has an invalid encrypted payload", exception)
        }
        require(payload.size > IV_LENGTH) { "Stored message has an incomplete encrypted payload" }

        val iv = payload.copyOfRange(0, IV_LENGTH)
        val ciphertext = payload.copyOfRange(IV_LENGTH, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    fun isEncrypted(value: String): Boolean = value.startsWith(PREFIX)

    private companion object {
        const val PREFIX = "enc:v1:"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
    }
}
