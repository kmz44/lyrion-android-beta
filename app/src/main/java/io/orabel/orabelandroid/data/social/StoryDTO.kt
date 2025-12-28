package io.orabel.orabelandroid.data.social

import java.util.Date
import java.util.UUID

/**
 * DTO para historias/estados.
 * Corresponde a la tabla 'stories' en Supabase.
 */
data class StoryDTO(
    val id: UUID,
    val userId: UUID,
    val mediaUrl: String,
    val mediaType: String, // "image" o "video"
    val thumbnailUrl: String? = null,
    val caption: String? = null,
    val durationSeconds: Int = 5,
    val createdAt: Date,
    val expiresAt: Date,
    val viewsCount: Int = 0,
    val isActive: Boolean = true,
    val visibility: String = "followers", // "followers" o "friends"
    
    // Datos del usuario
    val username: String? = null,
    val avatarUrl: String? = null,
    val nombre: String? = null,
    val apellido: String? = null
) {
    val displayName: String
        get() = when {
            !nombre.isNullOrBlank() && !apellido.isNullOrBlank() -> "$nombre $apellido"
            !nombre.isNullOrBlank() -> nombre
            !username.isNullOrBlank() -> username
            else -> "Usuario"
        }
    
    val isExpired: Boolean
        get() = Date().after(expiresAt)
}

/**
 * Request para crear una nueva historia.
 */
data class StoryUploadRequest(
    val mediaUrl: String,
    val mediaType: String, // "image" o "video"
    val thumbnailUrl: String? = null,
    val caption: String? = null,
    val durationSeconds: Int = 5,
    val visibility: String = "followers" // "followers" o "friends"
)

/**
 * Wrapper para agrupar las historias de un usuario.
 */
data class UserStoriesGroup(
    val userId: UUID,
    val username: String?,
    val avatarUrl: String?,
    val displayName: String,
    val stories: List<StoryDTO>,
    val hasUnviewedStory: Boolean = false
)
