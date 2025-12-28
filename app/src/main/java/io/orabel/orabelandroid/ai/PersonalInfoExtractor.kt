/*
 * Copyright (C) 2024 Lyrion
 * Extractor Inteligente de Información Personal usando LLM
 * Sin patrones fijos - funciona en cualquier idioma y forma de expresión
 */

package io.orabel.orabelandroid.ai

import android.content.Context
import android.util.Log
import io.orabel.orabelandroid.data.PersonalContext
import io.orabel.orabelandroid.data.ObjectBoxStore
import io.objectbox.kotlin.boxFor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import org.json.JSONObject

/**
 * Resultado de la extracción inteligente con LLM
 */
data class PersonalInfoExtraction(
    val hasPersonalInfo: Boolean,
    val userName: String? = null,
    val partnerName: String? = null,
    val partnerStatus: String? = null, // "current", "ex", "crush", "interested"
    val likes: List<String> = emptyList(),
    val dislikes: List<String> = emptyList(),
    val familyMembers: Map<String, String> = emptyMap(), // "madre" -> "Ana", "hermano" -> "Luis"
    val location: String? = null,
    val occupation: String? = null,
    val age: String? = null,
    val importantDates: Map<String, String> = emptyMap(), // "cumpleaños" -> "15 mayo"
    val otherInfo: Map<String, String> = emptyMap()
)

/**
 * Extractor inteligente que usa el LLM para detectar información personal
 * Sin patrones fijos - el LLM analiza el mensaje del usuario
 */
class PersonalInfoExtractor(private val context: Context) {
    
    private val personalBox = ObjectBoxStore.store.boxFor<PersonalContext>()
    private val TAG = "PersonalInfoExtractor"
    
    /**
     * Genera un prompt para que el LLM extraiga información personal
     */
    fun createExtractionPrompt(userMessage: String): String {
        return """
Analiza el siguiente mensaje del usuario y extrae TODA la información personal mencionada.

Mensaje del usuario: "$userMessage"

INSTRUCCIONES:
1. Identifica si el usuario menciona su nombre, edad, ubicación, trabajo, estudios
2. Identifica relaciones: pareja actual, ex-pareja, crush, familia
3. Identifica gustos y preferencias
4. Identifica fechas importantes

Responde ÚNICAMENTE en formato JSON estricto, sin texto adicional:

{
  "has_personal_info": true/false,
  "user_name": "nombre del usuario o null",
  "partner_name": "nombre de pareja/ex/crush o null",
  "partner_status": "current/ex/crush/interested o null",
  "likes": ["cosas que le gustan"],
  "dislikes": ["cosas que no le gustan"],
  "family_members": {"relación": "nombre"},
  "location": "ciudad/país o null",
  "occupation": "trabajo/estudio o null",
  "age": "edad o null",
  "important_dates": {"evento": "fecha"},
  "other_info": {"clave": "valor"}
}

EJEMPLOS:
- "me llamo Carlos" -> {"has_personal_info": true, "user_name": "Carlos"}
- "mi novia se llama Ana" -> {"has_personal_info": true, "partner_name": "Ana", "partner_status": "current"}
- "mi ex se llama María, me dejó" -> {"has_personal_info": true, "partner_name": "María", "partner_status": "ex"}
- "me gusta el chocolate" -> {"has_personal_info": true, "likes": ["chocolate"]}
- "my name is John" -> {"has_personal_info": true, "user_name": "John"}

Responde SOLO el JSON, nada más.
""".trimIndent()
    }
    
    /**
     * Parsea la respuesta JSON del LLM y extrae la información
     */
    fun parseExtractionResponse(llmResponse: String): PersonalInfoExtraction? {
        try {
            // Buscar el JSON en la respuesta (puede venir con texto antes/después)
            val jsonStart = llmResponse.indexOf('{')
            val jsonEnd = llmResponse.lastIndexOf('}') + 1
            
            if (jsonStart == -1 || jsonEnd == 0) {
                Log.w(TAG, "No se encontró JSON en la respuesta del LLM")
                return null
            }
            
            val jsonString = llmResponse.substring(jsonStart, jsonEnd)
            val json = JSONObject(jsonString)
            
            val hasInfo = json.optBoolean("has_personal_info", false)
            if (!hasInfo) {
                return PersonalInfoExtraction(hasPersonalInfo = false)
            }
            
            // Parsear likes
            val likesArray = json.optJSONArray("likes")
            val likes = mutableListOf<String>()
            if (likesArray != null) {
                for (i in 0 until likesArray.length()) {
                    likes.add(likesArray.getString(i))
                }
            }
            
            // Parsear dislikes
            val dislikesArray = json.optJSONArray("dislikes")
            val dislikes = mutableListOf<String>()
            if (dislikesArray != null) {
                for (i in 0 until dislikesArray.length()) {
                    dislikes.add(dislikesArray.getString(i))
                }
            }
            
            // Parsear family_members
            val familyJson = json.optJSONObject("family_members")
            val family = mutableMapOf<String, String>()
            if (familyJson != null) {
                familyJson.keys().forEach { key ->
                    family[key] = familyJson.getString(key)
                }
            }
            
            // Parsear important_dates
            val datesJson = json.optJSONObject("important_dates")
            val dates = mutableMapOf<String, String>()
            if (datesJson != null) {
                datesJson.keys().forEach { key ->
                    dates[key] = datesJson.getString(key)
                }
            }
            
            // Parsear other_info
            val otherJson = json.optJSONObject("other_info")
            val other = mutableMapOf<String, String>()
            if (otherJson != null) {
                otherJson.keys().forEach { key ->
                    other[key] = otherJson.getString(key)
                }
            }
            
            return PersonalInfoExtraction(
                hasPersonalInfo = true,
                userName = json.optString("user_name").takeIf { it.isNotBlank() && it != "null" },
                partnerName = json.optString("partner_name").takeIf { it.isNotBlank() && it != "null" },
                partnerStatus = json.optString("partner_status").takeIf { it.isNotBlank() && it != "null" },
                likes = likes,
                dislikes = dislikes,
                familyMembers = family,
                location = json.optString("location").takeIf { it.isNotBlank() && it != "null" },
                occupation = json.optString("occupation").takeIf { it.isNotBlank() && it != "null" },
                age = json.optString("age").takeIf { it.isNotBlank() && it != "null" },
                importantDates = dates,
                otherInfo = other
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parseando respuesta JSON: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Guarda la información extraída en la base de datos PersonalContext
     */
    fun saveExtractedInfo(extraction: PersonalInfoExtraction, originalMessage: String) {
        try {
            val timestamp = System.currentTimeMillis()
            
            // Guardar nombre de usuario
            extraction.userName?.let { name ->
                saveOrUpdateContext(
                    contextType = "user_info",
                    key = "name",
                    value = name,
                    notes = "Extraído de: $originalMessage",
                    relevanceScore = 100,
                    timestamp = timestamp
                )
                Log.i(TAG, "✅ Nombre guardado: $name")
            }
            
            // Guardar información de pareja/ex
            extraction.partnerName?.let { partnerName ->
                val key = when (extraction.partnerStatus) {
                    "ex" -> "ex_name"
                    "crush" -> "crush_name"
                    "interested" -> "interest_name"
                    else -> "partner_name"
                }
                
                saveOrUpdateContext(
                    contextType = "relationship",
                    key = key,
                    value = partnerName,
                    notes = "Estado: ${extraction.partnerStatus ?: "current"}. Mensaje: $originalMessage",
                    relevanceScore = if (extraction.partnerStatus == "ex") 60 else 90,
                    timestamp = timestamp
                )
                Log.i(TAG, "✅ Relación guardada: $partnerName (${extraction.partnerStatus})")
            }
            
            // Guardar gustos
            extraction.likes.forEach { like ->
                saveOrUpdateContext(
                    contextType = "preferences",
                    key = "likes",
                    value = like,
                    notes = "Le gusta: $like",
                    relevanceScore = 70,
                    timestamp = timestamp
                )
                Log.i(TAG, "✅ Gusto guardado: $like")
            }
            
            // Guardar disgustos
            extraction.dislikes.forEach { dislike ->
                saveOrUpdateContext(
                    contextType = "preferences",
                    key = "dislikes",
                    value = dislike,
                    notes = "No le gusta: $dislike",
                    relevanceScore = 70,
                    timestamp = timestamp
                )
            }
            
            // Guardar familia
            extraction.familyMembers.forEach { (relation, name) ->
                saveOrUpdateContext(
                    contextType = "family",
                    key = relation,
                    value = name,
                    notes = "Familia: $relation -> $name",
                    relevanceScore = 85,
                    timestamp = timestamp
                )
                Log.i(TAG, "✅ Familia guardada: $relation -> $name")
            }
            
            // Guardar ubicación
            extraction.location?.let { loc ->
                saveOrUpdateContext(
                    contextType = "user_info",
                    key = "location",
                    value = loc,
                    notes = "Ubicación mencionada",
                    relevanceScore = 75,
                    timestamp = timestamp
                )
            }
            
            // Guardar ocupación
            extraction.occupation?.let { occ ->
                saveOrUpdateContext(
                    contextType = "user_info",
                    key = "occupation",
                    value = occ,
                    notes = "Trabajo/estudios mencionados",
                    relevanceScore = 80,
                    timestamp = timestamp
                )
            }
            
            // Guardar edad
            extraction.age?.let { age ->
                saveOrUpdateContext(
                    contextType = "user_info",
                    key = "age",
                    value = age,
                    notes = "Edad mencionada",
                    relevanceScore = 75,
                    timestamp = timestamp
                )
            }
            
            // Guardar fechas importantes
            extraction.importantDates.forEach { (event, date) ->
                saveOrUpdateContext(
                    contextType = "important_dates",
                    key = event,
                    value = date,
                    notes = "Fecha importante: $event",
                    relevanceScore = 85,
                    timestamp = timestamp
                )
            }
            
            // Guardar otra información
            extraction.otherInfo.forEach { (key, value) ->
                saveOrUpdateContext(
                    contextType = "other",
                    key = key,
                    value = value,
                    notes = "Otra info: $key -> $value",
                    relevanceScore = 65,
                    timestamp = timestamp
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando información extraída: ${e.message}", e)
        }
    }
    
    /**
     * Guarda o actualiza un contexto personal
     */
    private fun saveOrUpdateContext(
        contextType: String,
        key: String,
        value: String,
        notes: String,
        relevanceScore: Int,
        timestamp: Long
    ) {
        // Buscar si ya existe
        val existing = personalBox.all.find { 
            it.contextType == contextType && it.key == key 
        }
        
        if (existing != null) {
            // Actualizar existente
            existing.value = value
            existing.notes = notes
            existing.relevanceScore = relevanceScore
            existing.timestamp = timestamp
            existing.useCount += 1
            personalBox.put(existing)
        } else {
            // Crear nuevo
            val newContext = PersonalContext(
                contextType = contextType,
                key = key,
                value = value,
                notes = notes,
                timestamp = timestamp,
                relevanceScore = relevanceScore,
                category = when (contextType) {
                    "relationship" -> "romantic"
                    "user_info" -> "personal"
                    "preferences" -> "social"
                    "family" -> "personal"
                    else -> "personal"
                },
                isActive = true,
                useCount = 1
            )
            personalBox.put(newContext)
        }
    }
    
    /**
     * Obtiene el nombre del usuario si está guardado
     */
    fun getUserName(): String? {
        return personalBox.all.find { it.key == "name" }?.value
    }
    
    /**
     * Obtiene contexto personal relevante
     */
    fun getPersonalContext(): List<PersonalContext> {
        return personalBox.all
            .filter { it.isActive }
            .sortedByDescending { it.relevanceScore }
            .take(10)
    }
}
