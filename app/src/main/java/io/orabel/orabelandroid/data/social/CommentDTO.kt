package io.orabel.orabelandroid.data.social

import java.util.UUID
import java.util.Date

/**
 * DTO para comentarios - Copiado de iOS CommentDTO
 * Soporta hilos de comentarios (parent_id)
 */
data class CommentDTO(
    val id: UUID,
    val postId: UUID,
    val userId: UUID,
    val content: String,
    val parentId: UUID? = null, // Para hilos de comentarios
    val likesCount: Int? = 0,
    val dislikesCount: Int? = 0,
    val createdAt: Date,
    val user: CommentUserDTO? = null
)

/**
 * Usuario simplificado para comentarios
 */
data class CommentUserDTO(
    val username: String?,
    val nombre: String?,
    val apellido: String?,
    val avatarUrl: String?
)

/**
 * Nodo para árbol de comentarios
 */
data class CommentNode(
    val id: UUID,
    val comment: CommentDTO,
    val children: List<CommentNode> = emptyList()
)

/**
 * DTO para reacciones de posts
 */
data class ReactionDTO(
    val id: UUID,
    val postId: UUID,
    val userId: UUID,
    val emoji: String, // ❤️, 😂, 🔥, etc.
    val createdAt: Date,
    val user: CommentUserDTO? = null
)

/**
 * DTO para reacciones de comentarios
 */
data class CommentReactionDTO(
    val id: UUID,
    val commentId: UUID,
    val userId: UUID,
    val reactionType: String, // "like" o "dislike"
    val createdAt: Date
)
