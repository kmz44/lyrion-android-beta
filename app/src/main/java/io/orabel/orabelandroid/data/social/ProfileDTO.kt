package io.orabel.orabelandroid.data.social

import java.util.UUID
import java.util.Date

/**
 * DTO para perfiles de usuario en la red social.
 * Corresponde a la tabla 'profiles' en Supabase.
 */
data class ProfileDTO(
    val id: UUID,
    val username: String? = null,
    val email: String? = null,
    val nombre: String? = null,
    val apellido: String? = null,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val bio: String? = null,
    val occupation: String? = null,
    val pais: String? = null,
    val edad: Int? = null,
    val altura_cm: Int? = null,
    val peso_kg: Int? = null,
    val estadoCivil: String? = null,
    val estadoRegion: String? = null,
    val status: String? = null,
    val createdAt: Date? = null
) {
    val displayName: String
        get() = when {
            !nombre.isNullOrBlank() && !apellido.isNullOrBlank() -> "$nombre $apellido"
            !nombre.isNullOrBlank() -> nombre
            !username.isNullOrBlank() -> username
            else -> "Usuario"
        }
    
    val initials: String
        get() = username?.take(1)?.uppercase() ?: "U"
}
