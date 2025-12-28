/*
 * Copyright (C) 2024 Lyrion
 * Base de Datos de Conocimiento Educativo - Compartido automáticamente
 * Guarda fórmulas, conceptos, redacciones y apuntes académicos
 */

package io.orabel.orabelandroid.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

@Entity
data class StudyKnowledge(
    @Id var id: Long = 0,
    
    // Categoría académica: "matemáticas", "química", "física", "redacción", etc.
    @Index var category: String = "",
    
    // Tema específico: "ecuaciones cuadráticas", "tabla periódica", etc.
    var topic: String = "",
    
    // Contenido del conocimiento (texto explicativo)
    var content: String = "",
    
    // Fórmulas matemáticas/químicas en formato LaTeX
    var formulas: String = "",
    
    // Ejemplos prácticos
    var examples: String = "",
    
    // Timestamp de cuando se guardó
    var timestamp: Long = System.currentTimeMillis(),
    
    // Contador de veces que se ha usado este conocimiento
    var useCount: Int = 0,
    
    // Score de relevancia (0-100)
    var relevanceScore: Int = 50,
    
    // Palabras clave para búsqueda rápida
    var keywords: String = "",
    
    // Nivel educativo: "básico", "intermedio", "avanzado"
    var difficulty: String = "intermedio"
)
