/*
 * Copyright (C) 2024 Lyrion
 * Perfil médico del usuario para informes de salud
 */

package io.orabel.orabelandroid.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import java.util.*

@Entity
data class UserMedicalProfile(
    @Id var id: Long = 0,
    
    // Datos personales
    var fullName: String = "",
    var birthDate: Date = Date(),
    var age: Int = 0,
    var gender: String = "", // "Masculino", "Femenino", "Otro"
    
    // Datos físicos
    var height: Double = 0.0, // en metros (ej: 1.75)
    var weight: Double = 0.0, // en kilogramos
    var bmi: Double = 0.0, // calculado automáticamente
    
    // Información médica crítica
    var bloodType: String = "", // "O+", "A-", etc.
    var allergies: String = "", // Lista separada por comas
    var currentMedications: String = "", // Lista de medicamentos actuales
    var chronicConditions: String = "", // Condiciones crónicas
    
    // Información de emergencia
    var emergencyContactName: String = "",
    var emergencyContactPhone: String = "",
    var emergencyContactRelation: String = "", // "Esposa", "Hijo", etc.
    
    // Historial médico
    var previousSurgeries: String = "", // Cirugías previas
    var importantMedicalHistory: String = "", // Historial médico relevante
    var notes: String = "", // Notas adicionales
    
    // Metadatos
    var createdAt: Date = Date(),
    var lastUpdated: Date = Date()
) {
    
    /**
     * Calcula el IMC basado en altura y peso
     */
    fun calculateBMI(): Double {
        return if (height > 0 && weight > 0) {
            weight / (height * height)
        } else {
            0.0
        }
    }
    
    /**
     * Calcula la edad basada en la fecha de nacimiento
     */
    fun calculateAge(): Int {
        val today = Calendar.getInstance()
        val birth = Calendar.getInstance().apply { time = birthDate }
        
        var age = today.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
        
        if (today.get(Calendar.DAY_OF_YEAR) < birth.get(Calendar.DAY_OF_YEAR)) {
            age--
        }
        
        return age
    }
    
    /**
     * Actualiza campos calculados
     */
    fun updateCalculatedFields() {
        age = calculateAge()
        bmi = calculateBMI()
        lastUpdated = Date()
    }
    
    /**
     * Verifica si el perfil está completo (campos básicos)
     */
    fun isBasicProfileComplete(): Boolean {
        return fullName.isNotBlank() && 
               height > 0 && 
               weight > 0 && 
               age > 0
    }
    
    /**
     * Obtiene categoría del IMC
     */
    fun getBMICategory(): String {
        return when {
            bmi < 18.5 -> "Bajo peso"
            bmi < 25.0 -> "Peso normal"
            bmi < 30.0 -> "Sobrepeso"
            else -> "Obesidad"
        }
    }
    
    companion object {
        /**
         * Calcula el BMI dados la altura y peso
         */
        fun calculateBMI(height: Double, weight: Double): Double {
            return if (height > 0) {
                weight / (height * height)
            } else {
                0.0
            }
        }
    }
}
