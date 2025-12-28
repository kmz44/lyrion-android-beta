package io.orabel.orabelandroid.ui.screens.chat

/**
 * Data class para manejar imágenes adjuntas (sin auto-envío)
 */
data class ImageAttachment(
    val uri: android.net.Uri,
    val base64: String,
    val mimeType: String
)
