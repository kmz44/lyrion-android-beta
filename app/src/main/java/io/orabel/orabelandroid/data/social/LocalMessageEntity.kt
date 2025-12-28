package io.orabel.orabelandroid.data.social

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id

/**
 * Local storage entity for read messages (deleted from server).
 * Implements the ephemeral messaging pattern from iOS using ObjectBox.
 */
@Entity
data class LocalMessageEntity(
    @Id var id: Long = 0,
    
    var messageId: Long = 0, // ID del mensaje de Supabase
    var senderId: String = "",
    var receiverId: String = "",
    var content: String = "",
    var createdAt: Long = 0,
    var readAt: Long = 0,
    
    // Cached sender info for offline display
    var senderUsername: String? = null,
    var senderAvatarUrl: String? = null,
    
    // Reply/Thread context (for maintaining reply UI even after server deletion)
    var replyToId: Long? = null,
    var replyContextContent: String? = null,
    var replyContextSenderUsername: String? = null
) {
    /**
     * Convert ObjectBox entity to DTO for UI display
     */
    fun toDTO(): MessageDTO {
        val sender = PostCreator(
            username = senderUsername,
            nombre = null,
            apellido = null,
            avatar_url = senderAvatarUrl
        )
        
        // Reconstruct reply context if available
        var replyToMessage: BoxedMessageDTO? = null
        if (replyToId != null && replyContextContent != null) {
            val ghostSender = PostCreator(
                username = replyContextSenderUsername ?: "Usuario",
                nombre = null,
                apellido = null,
                avatar_url = null
            )
            val ghostParent = MessageDTO(
                id = replyToId!!,
                senderId = java.util.UUID.randomUUID(),
                receiverId = java.util.UUID.randomUUID(),
                content = replyContextContent!!,
                isTemporary = true,
                seenAt = null,
                createdAt = java.util.Date(createdAt),
                status = null,
                replyToId = null,
                replyContextContent = null,
                replyContextSenderUsername = null,
                sender = ghostSender,
                receiver = null,
                replyToMessage = null
            )
            replyToMessage = BoxedMessageDTO(message = ghostParent)
        }
        
        return MessageDTO(
            id = messageId,
            senderId = java.util.UUID.fromString(senderId),
            receiverId = java.util.UUID.fromString(receiverId),
            content = content,
            isTemporary = true,
            seenAt = null,
            createdAt = java.util.Date(createdAt),
            status = "read",
            replyToId = replyToId,
            replyContextContent = replyContextContent,
            replyContextSenderUsername = replyContextSenderUsername,
            sender = sender,
            receiver = null,
            replyToMessage = replyToMessage
        )
    }
    
    companion object {
        /**
         * Create entity from DTO when saving read message locally
         */
        fun fromDTO(msg: MessageDTO, readAt: Long = System.currentTimeMillis()): LocalMessageEntity {
            return LocalMessageEntity(
                id = 0, // AutoID generado por ObjectBox
                messageId = msg.id.toLong(),
                senderId = msg.senderId.toString(),
                receiverId = msg.receiverId.toString(),
                content = msg.content,
                createdAt = msg.createdAt.time,
                readAt = readAt,
                senderUsername = msg.sender?.username,
                senderAvatarUrl = msg.sender?.avatar_url,
                replyToId = msg.replyToId?.toLong(),
                replyContextContent = msg.replyContextContent ?: msg.replyToMessage?.message?.content,
                replyContextSenderUsername = msg.replyContextSenderUsername ?: msg.replyToMessage?.message?.sender?.username
            )
        }
    }
}
