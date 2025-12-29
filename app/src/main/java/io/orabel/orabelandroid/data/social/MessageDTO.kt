package io.orabel.orabelandroid.data.social

import java.util.UUID
import java.util.Date

/**
 * DTO para mensajes directos entre usuarios.
 * Ahora con soporte para patrón efímero y reply context.
 */
data class MessageDTO(
    val id: Long,  // Changed to Long to match Supabase bigint
    val senderId: UUID,
    val receiverId: UUID,
    val content: String,
    val isTemporary: Boolean = true,  // Ephemeral pattern
    val deliveredAt: Long? = null,  // Timestamp cuando fue entregado
    val seenAt: Long? = null,  // Timestamp cuando fue leído (antes era Date?)
    val createdAt: Date,
    val status: String? = null,  // "sent", "delivered", "read"
    
    // Reply context fields
    val replyToId: Long? = null,
    val replyContextContent: String? = null,
    val replyContextSenderUsername: String? = null,
    
    // Sender info (for display)
    val sender: PostCreator? = null,
    val receiver: PostCreator? = null,
    
    // Nested reply message (for display)
    val replyToMessage: BoxedMessageDTO? = null
)

/**
 * Simple wrapper to avoid circular reference in MessageDTO
 */
data class BoxedMessageDTO(
    val message: MessageDTO
)

/**
 * Simplified creator for messages
 */
data class PostCreator(
    val username: String?,
    val nombre: String?,
    val apellido: String?,
    val avatar_url: String?
)
