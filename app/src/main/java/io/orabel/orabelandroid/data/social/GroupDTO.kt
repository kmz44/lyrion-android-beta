package io.orabel.orabelandroid.data.social

import java.util.UUID
import java.util.Date

/**
 * DTO para grupos - Copiado de iOS GroupDTO
 */
data class GroupDTO(
    val id: UUID,
    val nombre: String,
    val descripcion: String? = null,
    val tipo: String = "public", // public, friends_only, followers_only, private
    val creadorId: UUID,
    val imagenUrl: String? = null,
    val creadoEn: Date? = null
)
