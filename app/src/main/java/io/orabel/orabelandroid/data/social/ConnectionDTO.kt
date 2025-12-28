package io.orabel.orabelandroid.data.social

import java.util.UUID
import java.util.Date

/**
 * DTO para conexiones entre usuarios (seguimientos, amistades, solicitudes).
 * Corresponde a la tabla 'connections' en Supabase.
 * 
 * Estados posibles:
 * - "siguiendo": El usuario sigue al otro
 * - "pendiente": Solicitud de amistad enviada
 * - "amigos": Amistad mutua aceptada
 */
data class ConnectionDTO(
    val id: UUID,
    val userId: UUID,
    val targetUserId: UUID,
    val status: String, // "siguiendo", "pendiente", "amigos"
    val createdAt: Date? = null
)

/**
 * Representa una solicitud de amistad con información del usuario.
 */
data class FriendRequestWithUser(
    val connectionId: UUID,
    val user: ProfileDTO
)
