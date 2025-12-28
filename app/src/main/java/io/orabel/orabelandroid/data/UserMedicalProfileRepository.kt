/*
 * Copyright (C) 2024 Lyrion
 * Repositorio para el perfil médico del usuario
 */

package io.orabel.orabelandroid.data

import android.util.Log
import io.objectbox.Box
import io.objectbox.kotlin.flow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
class UserMedicalProfileRepository(
    private val userMedicalProfileBox: Box<UserMedicalProfile>
) {
    
    companion object {
        private const val TAG = "UserMedicalProfileRepo"
    }
    
    /**
     * Obtiene el perfil médico del usuario (solo debe haber uno)
     */
    fun getUserProfile(): Flow<UserMedicalProfile?> {
        return userMedicalProfileBox.query().build().flow().map { profiles ->
            Log.d(TAG, "📋 Perfiles encontrados: ${profiles.size}")
            profiles.firstOrNull()
        }
    }
    
    /**
     * Obtiene el perfil del usuario de forma síncrona (para uso en generación de reportes)
     */
    fun getUserProfileSync(): UserMedicalProfile? {
        return try {
            val profiles = userMedicalProfileBox.all
            Log.d(TAG, "📋 Obteniendo perfil síncronamente - perfiles encontrados: ${profiles.size}")
            profiles.firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error obteniendo perfil síncronamente", e)
            null
        }
    }
    
    /**
     * Guarda o actualiza el perfil médico del usuario
     */
    suspend fun saveUserProfile(profile: UserMedicalProfile): Long {
        return try {
            // Actualizar campos calculados
            profile.updateCalculatedFields()
            
            // Si ya existe un perfil, actualizarlo
            val existingProfile = userMedicalProfileBox.all.firstOrNull()
            if (existingProfile != null) {
                profile.id = existingProfile.id
                profile.createdAt = existingProfile.createdAt
            }
            
            val savedId = userMedicalProfileBox.put(profile)
            Log.i(TAG, "✅ Perfil médico guardado con ID: $savedId")
            Log.i(TAG, "👤 Nombre: ${profile.fullName}")
            Log.i(TAG, "📏 IMC: ${profile.bmi} (${profile.getBMICategory()})")
            
            savedId
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error guardando perfil médico: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Verifica si existe un perfil médico
     */
    suspend fun hasUserProfile(): Boolean {
        return userMedicalProfileBox.count() > 0
    }
    
    /**
     * Elimina el perfil médico (para casos de emergencia)
     */
    suspend fun deleteUserProfile(): Boolean {
        return try {
            userMedicalProfileBox.removeAll()
            Log.i(TAG, "🗑️ Perfil médico eliminado")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error eliminando perfil médico: ${e.message}", e)
            false
        }
    }
    
    /**
     * Crea un perfil básico vacío
     */
    fun createEmptyProfile(): UserMedicalProfile {
        return UserMedicalProfile().apply {
            fullName = ""
            gender = "No especificado"
            bloodType = "No especificado"
            allergies = "Ninguna conocida"
            currentMedications = "Ninguno"
            chronicConditions = "Ninguna"
            emergencyContactName = ""
            emergencyContactPhone = ""
            emergencyContactRelation = ""
            previousSurgeries = "Ninguna"
            importantMedicalHistory = ""
            notes = ""
        }
    }
}
