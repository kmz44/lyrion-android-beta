package io.orabel.orabelandroid.data.social

import java.util.UUID
import java.util.Date

/**
 * DTO para publicaciones en el feed social.
 * Corresponde a la tabla 'posts' en Supabase.
 */
data class PostDTO(
    val id: UUID,
    val creatorId: UUID,
    val caption: String? = null,
    val title: String? = null, // Para reels
    val category: String? = null, // Para categorías
    val mediaUrl: String? = null,
    val mediaType: String = "image", // "image" o "video"
    val thumbnailUrl: String? = null,
    val contentType: String = "post", // "post", "reel", "both"
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val reactionCount: Int = 0, // Total de reacciones (like iOS)
    val isAnonymous: Boolean = false,
    val durationSeconds: Int? = null,
    val createdAt: Date? = null,
    val creator: PostCreatorDTO? = null,
    // Nuevos campos para interactividad como iOS
    val comments: List<CommentDTO>? = null,
    val postReactions: List<ReactionDTO>? = null
) {
    val hasMedia: Boolean
        get() = !mediaUrl.isNullOrBlank()
    
    val isReel: Boolean
        get() = contentType == "reel" || contentType == "both"
    
    val isPost: Boolean
        get() = contentType == "post" || contentType == "both"
    
    val isVideo: Boolean
        get() = mediaType == "video"
    
    // Aliases for backwards compatibility with UI components
    val content: String?
        get() = caption
    
    val imageUrl: String?
        get() = if (mediaType == "image") mediaUrl else thumbnailUrl
    
    val videoUrl: String?
        get() = if (mediaType == "video") mediaUrl else null
    
    val author: ProfileDTO?
        get() = creator?.let { 
            ProfileDTO(
                id = creatorId,
                username = it.username,
                nombre = it.nombre,
                apellido = it.apellido,
                avatarUrl = it.avatarUrl
            )
        }
}

/**
 * DTO para información básica del creador del post.
 */
data class PostCreatorDTO(
    val username: String? = null,
    val nombre: String? = null,
    val apellido: String? = null,
    val avatarUrl: String? = null
) {
    fun toProfileDTO(id: UUID): ProfileDTO {
        return ProfileDTO(
            id = id,
            username = username,
            nombre = nombre,
            apellido = apellido,
            avatarUrl = avatarUrl
        )
    }
}
