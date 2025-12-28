package io.orabel.orabelandroid.data.social

/**
 * DTO para las estadísticas del usuario.
 */
data class UserStatsDTO(
    val posts: Int = 0,
    val followers: Int = 0,
    val following: Int = 0,
    val friends: Int = 0
)
