/*
 * Copyright (C) 2024 Lyrion
 * Generador de informes de salud para compartir con médicos
 */

package io.orabel.orabelandroid.health

import android.content.Context
import android.util.Log
import io.orabel.orabelandroid.data.HealthDiaryRepository
import io.orabel.orabelandroid.data.HealthDiaryEntry
import io.orabel.orabelandroid.data.UserMedicalProfileRepository
import io.orabel.orabelandroid.data.UserMedicalProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.text.SimpleDateFormat
import java.util.*

@Single
class HealthReportGenerator(
    private val healthDiaryRepository: HealthDiaryRepository,
    private val userMedicalProfileRepository: UserMedicalProfileRepository
) {
    
    companion object {
        private const val TAG = "HealthReportGenerator"
    }

    /**
     * Genera un informe completo del historial de salud en formato HTML
     */
    suspend fun generateHealthReport(): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.e(TAG, "🏥 === INICIO GENERACIÓN INFORME DE SALUD ===")
                
                // Debugging EXTREMO para encontrar el problema
                Log.e(TAG, "🔍 Obteniendo todas las entradas del repositorio...")
                val entries = healthDiaryRepository.getAllEntries().first()
                Log.e(TAG, "📊 TOTAL ENTRADAS ENCONTRADAS: ${entries.size}")
                
                if (entries.isEmpty()) {
                    Log.e(TAG, "❌ NO SE ENCONTRARON ENTRADAS - INVESTIGANDO...")
                    
                    // Verificar directamente con el repositorio
                    try {
                        val allEntriesDebug = healthDiaryRepository.getAllEntries().first()
                        Log.e(TAG, "� DEBUG: Reintentar getAllEntries() = ${allEntriesDebug.size} entradas")
                        
                        // Intentar contar las entradas de otra forma si es posible
                        Log.e(TAG, "💡 GENERANDO REPORTE VACÍO PORQUE NO HAY DATOS")
                    } catch (debugE: Exception) {
                        Log.e(TAG, "❌ ERROR EN DEBUG: ${debugE.message}", debugE)
                    }
                    
                    return@withContext generateEmptyReport()
                }
                
                // Debug SÚPER detallado de las entradas encontradas
                Log.e(TAG, "✅ ENTRADAS ENCONTRADAS - DETALLE COMPLETO:")
                entries.forEachIndexed { index, entry ->
                    Log.e(TAG, "📝 === ENTRADA $index ===")
                    Log.e(TAG, "  📄 Texto reporte: '${entry.userReportText}'")
                    Log.e(TAG, "  📅 Fecha: ${entry.recordedAt}")
                    Log.e(TAG, "  🏷️ Categoría: '${entry.category}'")
                    Log.e(TAG, "  📋 Info extraída: '${entry.extractedInfo}'")
                    Log.e(TAG, "  ⚠️ Nivel preocupación: ${entry.concernLevel}")
                    Log.e(TAG, "  🆔 ID: ${entry.id}")
                    Log.e(TAG, "  💬 Chat ID: ${entry.chatId}")
                }
                
                Log.e(TAG, "🔄 Continuando con generación del HTML...")
                
                if (entries.isEmpty()) {
                    Log.e(TAG, "⚠️ EXTRAÑO: entries.isEmpty() después de verificar ${entries.size}")
                    return@withContext generateEmptyReport()
                }
                
                // Obtener perfil médico del usuario
                val userProfile = userMedicalProfileRepository.getUserProfileSync()
                Log.e(TAG, "👤 Perfil de usuario encontrado: ${userProfile?.fullName ?: "Sin perfil"}")
                
                val html = generateUltraSimpleReport(entries, userProfile)
                
                Log.e(TAG, "✅ HTML ULTRA-SIMPLE GENERADO - TAMAÑO: ${html.length} caracteres")
                Log.e(TAG, "🔍 HTML COMPLETO:")
                Log.e(TAG, html)
                Log.e(TAG, "=== FIN GENERACIÓN INFORME ===")
                
                html
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error generando informe: ${e.message}", e)
                generateErrorReport(e)
            }
        }
    }

    private fun getHtmlHeader(): String {
        val currentDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        return """
        <!DOCTYPE html>
        <html lang="es">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Informe de Salud - Lyrion</title>
            <style>
                body { 
                    font-family: 'Arial', sans-serif; 
                    margin: 20px; 
                    background-color: #f8f9fa; 
                    color: #333;
                }
                .header { 
                    background: linear-gradient(135deg, #6366f1, #8b5cf6); 
                    color: white; 
                    padding: 20px; 
                    border-radius: 10px; 
                    margin-bottom: 20px; 
                    text-align: center;
                }
                .summary { 
                    background: white; 
                    padding: 20px; 
                    border-radius: 10px; 
                    margin-bottom: 20px; 
                    box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                }
                .category-section { 
                    background: white; 
                    padding: 20px; 
                    border-radius: 10px; 
                    margin-bottom: 20px; 
                    box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                }
                .entry { 
                    border-left: 4px solid #6366f1; 
                    padding: 15px; 
                    margin: 10px 0; 
                    background: #f8f9fa; 
                    border-radius: 5px;
                }
                .symptom { border-left-color: #ef4444; }
                .emotional { border-left-color: #f59e0b; }
                .accident { border-left-color: #8b5cf6; }
                .general { border-left-color: #10b981; }
                .date { color: #6b7280; font-size: 0.9em; }
                .concern-high { background-color: #fef2f2; }
                .concern-urgent { background-color: #fee2e2; }
                h1, h2, h3 { color: #374151; }
                .stats { display: flex; gap: 20px; flex-wrap: wrap; }
                .stat-item { 
                    background: #f3f4f6; 
                    padding: 15px; 
                    border-radius: 8px; 
                    text-align: center; 
                    min-width: 120px;
                }
                .timeline { margin-top: 20px; }
                .footer { 
                    text-align: center; 
                    color: #6b7280; 
                    margin-top: 30px; 
                    padding: 20px; 
                    border-top: 1px solid #e5e7eb;
                }
            </style>
        </head>
        <body>
            <div class="header">
                <h1>📋 Informe de Salud</h1>
                <p>Generado automáticamente por Lyrion</p>
                <p><strong>Fecha:</strong> $currentDate</p>
            </div>
        """.trimIndent()
    }

    private fun generateSummarySection(entries: List<HealthDiaryEntry>): String {
        val totalEntries = entries.size
        val symptomCount = entries.count { it.category == "symptom" }
        val emotionalCount = entries.count { it.category == "emotional" }
        val accidentCount = entries.count { it.category == "accident" }
        val highConcernCount = entries.count { it.concernLevel >= 2 }
        
        val oldestEntry = entries.minByOrNull { it.recordedAt }
        val newestEntry = entries.maxByOrNull { it.recordedAt }
        val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        
        return """
        <div class="summary">
            <h2>📊 Resumen General</h2>
            <div class="stats">
                <div class="stat-item">
                    <h3>$totalEntries</h3>
                    <p>Total de Registros</p>
                </div>
                <div class="stat-item">
                    <h3>$symptomCount</h3>
                    <p>Síntomas Físicos</p>
                </div>
                <div class="stat-item">
                    <h3>$emotionalCount</h3>
                    <p>Estados Emocionales</p>
                </div>
                <div class="stat-item">
                    <h3>$accidentCount</h3>
                    <p>Accidentes/Lesiones</p>
                </div>
                <div class="stat-item">
                    <h3>$highConcernCount</h3>
                    <p>Alta Preocupación</p>
                </div>
            </div>
            ${if (oldestEntry != null && newestEntry != null) """
            <p><strong>Período de registro:</strong> ${dateFormatter.format(oldestEntry.recordedAt)} - ${dateFormatter.format(newestEntry.recordedAt)}</p>
            """ else ""}
        </div>
        """.trimIndent()
    }

    private fun generateEntriesByCategory(entries: List<HealthDiaryEntry>): String {
        val categorizedEntries = entries.groupBy { it.category }
        val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        
        Log.e(TAG, "🔨 === GENERANDO ENTRADAS POR CATEGORÍA ===")
        Log.e(TAG, "📊 Categorías encontradas: ${categorizedEntries.keys}")
        
        val builder = StringBuilder()
        
        categorizedEntries.forEach { (category, categoryEntries) ->
            Log.e(TAG, "📂 Procesando categoría: '$category' con ${categoryEntries.size} entradas")
            
            val categoryName = when (category) {
                "symptom" -> "🩺 Síntomas Físicos"
                "emotional" -> "🧠 Estados Emocionales"
                "accident" -> "🚨 Accidentes y Lesiones"
                else -> "📝 Registros Generales"
            }
            
            builder.append("\n<div class=\"category-section\">\n")
            builder.append("    <h2>$categoryName</h2>\n")
            
            categoryEntries.sortedByDescending { it.recordedAt }.forEach { entry ->
                Log.e(TAG, "📝 Agregando entrada: '${entry.userReportText}'")
                
                val concernClass = when (entry.concernLevel) {
                    3 -> "concern-urgent"
                    2 -> "concern-high"
                    else -> ""
                }
                
                val concernText = when (entry.concernLevel) {
                    3 -> "🔴 URGENTE"
                    2 -> "🟡 ALTO"
                    1 -> "🟢 MODERADO"
                    else -> "⚪ BAJO"
                }
                
                builder.append("    <div class=\"entry $category $concernClass\">\n")
                builder.append("        <div class=\"date\">${dateFormatter.format(entry.recordedAt)} - Nivel de preocupación: $concernText</div>\n")
                builder.append("        <p><strong>Reporte:</strong> ${entry.userReportText}</p>\n")
                if (entry.extractedInfo.isNotEmpty()) {
                    builder.append("        <p><strong>Información relevante:</strong> ${entry.extractedInfo}</p>\n")
                }
                builder.append("    </div>\n")
            }
            
            builder.append("</div>\n")
        }
        
        val result = builder.toString()
        Log.e(TAG, "✅ HTML de categorías generado - TAMAÑO: ${result.length} caracteres")
        Log.e(TAG, "🔍 PREVIEW CATEGORÍAS (primeros 1000 chars):")
        Log.e(TAG, result.take(1000))
        
        return result
    }

    private fun generateTimelineSection(entries: List<HealthDiaryEntry>): String {
        val recentEntries = entries.sortedByDescending { it.recordedAt }.take(10)
        val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        
        Log.e(TAG, "⏰ === GENERANDO TIMELINE ===")
        Log.e(TAG, "📊 Entradas recientes: ${recentEntries.size}")
        
        val builder = StringBuilder()
        builder.append("\n<div class=\"category-section\">\n")
        builder.append("    <h2>⏰ Cronología Reciente (Últimos 10 registros)</h2>\n")
        builder.append("    <div class=\"timeline\">\n")
        
        recentEntries.forEach { entry ->
            Log.e(TAG, "⏰ Agregando a timeline: '${entry.userReportText}'")
            
            val categoryIcon = when (entry.category) {
                "symptom" -> "🩺"
                "emotional" -> "🧠"
                "accident" -> "🚨"
                else -> "📝"
            }
            
            builder.append("        <div class=\"entry ${entry.category}\">\n")
            builder.append("            <div class=\"date\">$categoryIcon ${dateFormatter.format(entry.recordedAt)}</div>\n")
            builder.append("            <p>${entry.userReportText}</p>\n")
            builder.append("        </div>\n")
        }
        
        builder.append("    </div>\n")
        builder.append("</div>\n")
        
        val result = builder.toString()
        Log.e(TAG, "✅ Timeline generado - TAMAÑO: ${result.length} caracteres")
        Log.e(TAG, "🔍 PREVIEW TIMELINE (primeros 500 chars):")
        Log.e(TAG, result.take(500))
        
        return result
    }

    private fun getHtmlFooter(): String {
        return """
        <div class="footer">
            <p>📱 Este informe fue generado automáticamente por Lyrion</p>
            <p>🔒 Información confidencial - Solo para uso médico profesional</p>
            <p>⚠️ Este documento se autodestruirá al cerrar la sesión</p>
        </div>
        </body>
        </html>
        """.trimIndent()
    }

    private fun generateEmptyReport(): String {
        return """
        ${getHtmlHeader()}
        <div class="summary">
            <h2>📋 Sin Registros de Salud</h2>
            <p>Actualmente no hay registros de salud guardados en el dispositivo.</p>
            <p>Para generar un informe, primero debe usar el chat de Lyrion para reportar síntomas, estados emocionales o eventos de salud.</p>
        </div>
        ${getHtmlFooter()}
        """.trimIndent()
    }

    private fun generateErrorReport(error: Exception): String {
        return """
        ${getHtmlHeader()}
        <div class="summary">
            <h2>❌ Error Generando Informe</h2>
            <p>Ocurrió un error al generar el informe de salud:</p>
            <p><strong>Error:</strong> ${error.message}</p>
            <p>Por favor, intente nuevamente o contacte al soporte técnico.</p>
        </div>
        ${getHtmlFooter()}
        """.trimIndent()
    }

    /**
     * Genera un HTML ULTRA-SIMPLE que es IMPOSIBLE que falle
     */
    private fun generateUltraSimpleReport(entries: List<HealthDiaryEntry>, userProfile: UserMedicalProfile?): String {
        val currentDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        
        val html = StringBuilder()
        
        // HTML básico GARANTIZADO
        html.append("<!DOCTYPE html>\n")
        html.append("<html>\n")
        html.append("<head>\n")
        html.append("<meta charset='UTF-8'>\n")
        html.append("<title>INFORME MÉDICO - LYRION</title>\n")
        html.append("<style>\n")
        html.append("body { font-family: Arial; margin: 20px; background: #f0f0f0; }\n")
        html.append(".header { background: #4a90e2; color: white; padding: 20px; text-align: center; }\n")
        html.append(".patient-info { background: #e8f4f8; padding: 20px; margin: 20px 0; border: 2px solid #4a90e2; }\n")
        html.append(".entry { background: white; margin: 10px 0; padding: 15px; border: 2px solid #333; }\n")
        html.append(".symptom { border-color: red; }\n")
        html.append(".emotional { border-color: orange; }\n")
        html.append(".critical { background: #fff5f5; border-color: #ff4444; }\n")
        html.append(".medical-data { display: flex; flex-wrap: wrap; gap: 20px; }\n")
        html.append(".medical-item { flex: 1; min-width: 200px; }\n")
        html.append("</style>\n")
        html.append("</head>\n")
        html.append("<body>\n")
        
        // Header
        html.append("<div class='header'>\n")
        html.append("<h1>🏥 INFORME MÉDICO LYRION</h1>\n")
        html.append("<p>Generado: $currentDate</p>\n")
        html.append("<p>TOTAL ENTRADAS: ${entries.size}</p>\n")
        html.append("</div>\n")
        
        // INFORMACIÓN DEL PACIENTE - MOSTRAR SIEMPRE SI EXISTE
        if (userProfile != null) {
            html.append("<div class='patient-info'>\n")
            html.append("<h2>👤 INFORMACIÓN DEL PACIENTE</h2>\n")
            html.append("<div class='medical-data'>\n")
            
            // Datos básicos - mostrar lo que esté disponible
            html.append("<div class='medical-item'>\n")
            html.append("<h3>📋 Datos Personales</h3>\n")
            if (userProfile.fullName.isNotBlank()) {
                html.append("<p><strong>Nombre:</strong> ${userProfile.fullName}</p>\n")
            }
            if (userProfile.age > 0) {
                html.append("<p><strong>Edad:</strong> ${userProfile.age} años</p>\n")
            }
            if (userProfile.gender.isNotBlank() && userProfile.gender != "No especificado") {
                html.append("<p><strong>Género:</strong> ${userProfile.gender}</p>\n")
            }
            if (userProfile.bloodType.isNotBlank() && userProfile.bloodType != "No especificado") {
                html.append("<p><strong>Tipo de sangre:</strong> ${userProfile.bloodType}</p>\n")
            }
            html.append("</div>\n")
            
            // Datos físicos - mostrar si están disponibles
            if (userProfile.height > 0 || userProfile.weight > 0) {
                html.append("<div class='medical-item'>\n")
                html.append("<h3>📏 Datos Físicos</h3>\n")
                if (userProfile.height > 0) {
                    html.append("<p><strong>Estatura:</strong> ${String.format("%.2f", userProfile.height)}m</p>\n")
                }
                if (userProfile.weight > 0) {
                    html.append("<p><strong>Peso:</strong> ${String.format("%.1f", userProfile.weight)}kg</p>\n")
                }
                if (userProfile.height > 0 && userProfile.weight > 0 && userProfile.bmi > 0) {
                    html.append("<p><strong>IMC:</strong> ${String.format("%.1f", userProfile.bmi)} (${userProfile.getBMICategory()})</p>\n")
                }
                html.append("</div>\n")
            }
            
            // Información médica crítica - mostrar si está disponible
            val hasMedicalInfo = (userProfile.allergies.isNotBlank() && userProfile.allergies != "Ninguna conocida") ||
                                (userProfile.currentMedications.isNotBlank() && userProfile.currentMedications != "Ninguno") ||
                                (userProfile.chronicConditions.isNotBlank() && userProfile.chronicConditions != "Ninguna")
            
            if (hasMedicalInfo) {
                html.append("<div class='medical-item'>\n")
                html.append("<h3>⚠️ Información Médica Crítica</h3>\n")
                if (userProfile.allergies.isNotBlank() && userProfile.allergies != "Ninguna conocida") {
                    html.append("<p><strong>🚨 Alergias:</strong> ${userProfile.allergies}</p>\n")
                }
                if (userProfile.currentMedications.isNotBlank() && userProfile.currentMedications != "Ninguno") {
                    html.append("<p><strong>💊 Medicamentos actuales:</strong> ${userProfile.currentMedications}</p>\n")
                }
                if (userProfile.chronicConditions.isNotBlank() && userProfile.chronicConditions != "Ninguna") {
                    html.append("<p><strong>🏥 Condiciones crónicas:</strong> ${userProfile.chronicConditions}</p>\n")
                }
                html.append("</div>\n")
            }
            
            // Contacto de emergencia - mostrar si está disponible
            if (userProfile.emergencyContactName.isNotBlank()) {
                html.append("<div class='medical-item'>\n")
                html.append("<h3>📞 Contacto de Emergencia</h3>\n")
                html.append("<p><strong>Nombre:</strong> ${userProfile.emergencyContactName}</p>\n")
                if (userProfile.emergencyContactPhone.isNotBlank()) {
                    html.append("<p><strong>Teléfono:</strong> ${userProfile.emergencyContactPhone}</p>\n")
                }
                if (userProfile.emergencyContactRelation.isNotBlank()) {
                    html.append("<p><strong>Relación:</strong> ${userProfile.emergencyContactRelation}</p>\n")
                }
                html.append("</div>\n")
            }
            
            // Notas adicionales - mostrar si están disponibles
            if (userProfile.notes.isNotBlank()) {
                html.append("<div class='medical-item'>\n")
                html.append("<h3>📝 Notas Médicas Adicionales</h3>\n")
                html.append("<p>${userProfile.notes}</p>\n")
                html.append("</div>\n")
            }
            
            html.append("</div>\n") // Cierre medical-data
            html.append("</div>\n") // Cierre patient-info
        } else {
            html.append("<div style='background: #fff3cd; padding: 15px; margin: 20px 0; border: 1px solid #ffeaa7;'>\n")
            html.append("<h3>⚠️ Información del Paciente No Disponible</h3>\n")
            html.append("<p>Para un informe médico completo, complete su perfil médico en la aplicación.</p>\n")
            html.append("</div>\n")
        }
        
        // Estadísticas básicas
        val symptomCount = entries.count { it.category == "symptom" }
        val emotionalCount = entries.count { it.category == "emotional" }
        
        html.append("<div style='background: white; padding: 20px; margin: 20px 0;'>\n")
        html.append("<h2>📊 RESUMEN DE SÍNTOMAS</h2>\n")
        html.append("<p><strong>Total registros:</strong> ${entries.size}</p>\n")
        html.append("<p><strong>Síntomas físicos:</strong> $symptomCount</p>\n")
        html.append("<p><strong>Estados emocionales:</strong> $emotionalCount</p>\n")
        html.append("</div>\n")
        
        // TODAS LAS ENTRADAS - FORMATO ULTRA-SIMPLE
        html.append("<div style='background: white; padding: 20px; margin: 20px 0;'>\n")
        html.append("<h2>🩺 HISTORIAL DE SÍNTOMAS</h2>\n")
        
        entries.sortedByDescending { it.recordedAt }.forEachIndexed { index, entry ->
            val categoryIcon = when (entry.category) {
                "symptom" -> "🩺"
                "emotional" -> "🧠"
                "accident" -> "🚨"
                else -> "📝"
            }
            
            html.append("<div class='entry ${entry.category}'>\n")
            html.append("<h3>$categoryIcon SÍNTOMA ${index + 1}</h3>\n")
            html.append("<p><strong>Fecha:</strong> ${dateFormatter.format(entry.recordedAt)}</p>\n")
            html.append("<p><strong>Categoría:</strong> ${entry.category}</p>\n")
            html.append("<p><strong>Descripción:</strong> ${entry.userReportText}</p>\n")
            if (entry.extractedInfo.isNotEmpty()) {
                html.append("<p><strong>Info relevante:</strong> ${entry.extractedInfo}</p>\n")
            }
            html.append("<p><strong>Nivel preocupación:</strong> ${entry.concernLevel}</p>\n")
            html.append("</div>\n")
        }
        
        html.append("</div>\n")
        
        // Footer
        html.append("<div style='text-align: center; margin-top: 30px; color: #666;'>\n")
        html.append("<p>📱 Generado por Lyrion Health Assistant</p>\n")
        html.append("<p>🔒 Información médica confidencial</p>\n")
        html.append("<p>📅 Fecha de generación: $currentDate</p>\n")
        html.append("</div>\n")
        
        html.append("</body>\n")
        html.append("</html>\n")
        
        return html.toString()
    }
}
