/*
 * Copyright (C) 2024 Lyrion
 * Base de Datos de Contexto Personal - PRIVADA (no compartida)
 * Guarda información personal del usuario para respuestas personalizadas
 */

package io.orabel.orabelandroid.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

@Entity
data class PersonalContext(
    @Id var id: Long = 0,
    
    // Tipo de contexto: "user_info", "relationship", "preferences", "daily_life", "hobbies"
    @Index var contextType: String = "",
    
    // Clave del dato: "name", "girlfriend_name", "favorite_color", etc.
    @Index var key: String = "",
    
    // Valor del dato
    var value: String = "",
    
    // Contexto adicional o notas
    var notes: String = "",
    
    // Timestamp de cuando se guardó
    var timestamp: Long = System.currentTimeMillis(),
    
    // Última vez que se usó este contexto
    var lastUsed: Long = 0,
    
    // Score de relevancia (0-100) - más alto = más importante
    var relevanceScore: Int = 50,
    
    // Contador de cuántas veces se ha usado
    var useCount: Int = 0,
    
    // Categoría para organización: "personal", "romantic", "social", "work", "study"
    var category: String = "personal",
    
    // Emoción asociada: "happy", "sad", "excited", "neutral"
    var emotion: String = "neutral",
    
    // Si está activo o archivado
    var isActive: Boolean = true
)
