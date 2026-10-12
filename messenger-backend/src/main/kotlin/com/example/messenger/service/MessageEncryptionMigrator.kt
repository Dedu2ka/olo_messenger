package com.example.messenger.service

import com.example.messenger.domain.MessageRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class MessageEncryptionMigrator(
    private val messageRepository: MessageRepository,
    private val messageEncryptionService: MessageEncryptionService
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) = runBlocking {
        messageRepository.findLegacyUnencryptedMessages().collect { message ->
            messageRepository.save(
                message.copy(content = messageEncryptionService.encrypt(message.content))
            )
        }
    }
}
