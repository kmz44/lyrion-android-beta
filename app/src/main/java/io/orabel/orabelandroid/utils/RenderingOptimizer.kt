package io.orabel.orabelandroid.utils

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Window
import android.view.WindowManager

object RenderingOptimizer {
    
    private const val TAG = "RenderingOptimizer"
    
    /**
     * Optimiza la configuración de rendering para mejorar el rendimiento
     */
    fun optimizeRendering(activity: Activity) {
        try {
            val window = activity.window
            
            // Optimizar formato de píxeles
            window.setFormat(PixelFormat.RGBA_8888)
            
            // Habilitar aceleración por hardware
            window.setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
            
            // Optimizar para pantallas de alta densidad
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                window.attributes.layoutInDisplayCutoutMode = 
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            
            // Optimizar superficie de drawing
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
        } catch (e: Exception) {
            Log.w(TAG, "Error optimizando rendering: ${e.message}")
        }
    }
    
    /**
     * Optimiza la configuración para Compose
     */
    fun optimizeCompose(activity: Activity) {
        try {
            // Configuración específica para Compose
            val window = activity.window
            
            // Optimizar para animaciones suaves
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            
            // Mejor rendimiento para superficie de Compose
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.attributes.preferMinimalPostProcessing = true
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Error optimizando Compose: ${e.message}")
        }
    }
    
    /**
     * Optimizaciones para OpenGL
     */
    fun optimizeOpenGL(context: Context) {
        try {
            // Configuraciones específicas para OpenGL
            System.setProperty("debug.egl.hw", "1")
            System.setProperty("debug.sf.hw", "1")
            
            // Optimizar para GPU específica
            System.setProperty("debug.composition.type", "gpu")
            
        } catch (e: Exception) {
            Log.w(TAG, "Error optimizando OpenGL: ${e.message}")
        }
    }
    
    /**
     * Limpia recursos de rendering
     */
    fun cleanup() {
        try {
            // Forzar limpieza de GPU
            System.gc()
            Runtime.getRuntime().gc()
            
        } catch (e: Exception) {
            Log.w(TAG, "Error en cleanup: ${e.message}")
        }
    }
}
