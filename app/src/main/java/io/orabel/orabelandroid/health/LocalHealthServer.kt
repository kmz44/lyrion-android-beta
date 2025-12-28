/*
 * Copyright (C) 2024 Lyrion
 * Servidor HTTP local temporal para compartir informes de salud de forma segura
 */

package io.orabel.orabelandroid.health

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.*
import org.koin.core.annotation.Single
import java.io.*
import java.net.*
import java.text.SimpleDateFormat
import java.util.*

@Single
class LocalHealthServer(private val context: Context) {
    
    companion object {
        private const val TAG = "LocalHealthServer"
        private const val DEFAULT_PORT = 8080
    }
    
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private var serverJob: Job? = null
    private var accessToken: String = ""
    private var healthReportHtml: String = ""
    
    /**
     * Inicia el servidor local temporal
     */
    suspend fun startServer(reportHtml: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                if (isRunning) {
                    Log.w(TAG, "⚠️ Servidor ya está ejecutándose")
                    return@withContext generateAccessUrl()
                }
                
                Log.e(TAG, "🚀 === INICIANDO SERVIDOR LOCAL ===")
                Log.e(TAG, "📄 HTML RECIBIDO - TAMAÑO: ${reportHtml.length} caracteres")
                Log.e(TAG, "🔍 HTML PREVIEW (primeros 1000 chars):")
                Log.e(TAG, reportHtml.take(1000))
                Log.e(TAG, "=== FIN PREVIEW HTML ===")
                
                // Generar token de acceso único
                accessToken = generateUniqueToken()
                healthReportHtml = reportHtml
                
                Log.e(TAG, "✅ HTML GUARDADO EN SERVIDOR - TAMAÑO: ${healthReportHtml.length}")
                
                // Encontrar puerto disponible
                val port = findAvailablePort()
                serverSocket = ServerSocket(port)
                isRunning = true
                
                Log.i(TAG, "✅ Servidor iniciado en puerto $port")
                Log.i(TAG, "🔑 Token de acceso: $accessToken")
                
                val fullUrl = generateAccessUrl()
                Log.e(TAG, "🎯 === URL COMPLETA DEL QR ===")
                Log.e(TAG, "📱 URL COMPLETA: $fullUrl")
                Log.e(TAG, "🔗 COPIA ESTA URL EXACTA EN TU NAVEGADOR:")
                Log.e(TAG, "$fullUrl")
                Log.e(TAG, "=== FIN URL QR ===")
                
                // Iniciar servidor en corrutina separada
                serverJob = CoroutineScope(Dispatchers.IO).launch {
                    handleClientConnections()
                }
                
                // Auto-destrucción después de 10 minutos
                CoroutineScope(Dispatchers.IO).launch {
                    delay(10 * 60 * 1000L) // 10 minutos
                    stopServer()
                    Log.i(TAG, "🔒 Servidor auto-destruido por seguridad")
                }
                
                generateAccessUrl()
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error iniciando servidor: ${e.message}", e)
                stopServer()
                null
            }
        }
    }
    
    /**
     * Detiene el servidor
     */
    fun stopServer() {
        try {
            Log.i(TAG, "🛑 Deteniendo servidor...")
            
            isRunning = false
            serverJob?.cancel()
            
            serverSocket?.close()
            serverSocket = null
            
            // Limpiar datos sensibles
            accessToken = ""
            healthReportHtml = ""
            
            Log.i(TAG, "✅ Servidor detenido correctamente")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error deteniendo servidor: ${e.message}", e)
        }
    }
    
    /**
     * Maneja las conexiones de clientes
     */
    private suspend fun handleClientConnections() {
        while (isRunning && serverSocket?.isClosed == false) {
            try {
                val clientSocket = serverSocket?.accept() ?: break
                
                // Procesar petición en corrutina separada
                CoroutineScope(Dispatchers.IO).launch {
                    handleClientRequest(clientSocket)
                }
                
            } catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "❌ Error aceptando conexión: ${e.message}")
                }
                break
            }
        }
    }
    
    /**
     * Procesa petición del cliente
     */
    private fun handleClientRequest(clientSocket: Socket) {
        try {
            val input = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
            val output = PrintWriter(clientSocket.getOutputStream(), true)
            
            // Leer línea de petición HTTP
            val requestLine = input.readLine()
            if (requestLine == null) {
                clientSocket.close()
                return
            }
            
            Log.d(TAG, "📨 Petición recibida: $requestLine")
            
            // Parsear petición
            val parts = requestLine.split(" ")
            if (parts.size < 3) {
                sendBadRequest(output)
                clientSocket.close()
                return
            }
            
            val method = parts[0]
            val path = parts[1]
            
            // Leer headers
            val headers = mutableMapOf<String, String>()
            var line = input.readLine()
            while (line != null && line.isNotEmpty()) {
                val colonIndex = line.indexOf(":")
                if (colonIndex > 0) {
                    val key = line.substring(0, colonIndex).trim().lowercase()
                    val value = line.substring(colonIndex + 1).trim()
                    headers[key] = value
                }
                line = input.readLine()
            }
            
            // Procesar petición según la ruta
            when {
                method == "GET" && (path == "/" || path.startsWith("/health-report")) -> {
                    // SIEMPRE servir el informe de salud sin verificar token
                    sendHealthReport(output)
                }
                method == "GET" && path.startsWith("/favicon.ico") -> {
                    sendNotFound(output)
                }
                else -> {
                    sendHealthReport(output) // Por defecto, servir informe
                }
            }
            
            clientSocket.close()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error procesando petición del cliente: ${e.message}", e)
            try {
                clientSocket.close()
            } catch (closeException: Exception) {
                Log.e(TAG, "❌ Error cerrando socket del cliente: ${closeException.message}")
            }
        }
    }
    
    /**
     * Envía el informe de salud directamente SIN verificar token
     */
    private fun sendHealthReport(output: PrintWriter) {
        try {
            Log.e(TAG, "✅ === ENVIANDO INFORME DIRECTO SIN SEGURIDAD ===")
            Log.e(TAG, "📄 HTML A ENVIAR - TAMAÑO: ${healthReportHtml.length} caracteres")
            
            // Enviar informe de salud directamente
            output.println("HTTP/1.1 200 OK")
            output.println("Content-Type: text/html; charset=UTF-8")
            output.println("Connection: close")
            output.println()
            output.println(healthReportHtml)
            
            Log.e(TAG, "✅ INFORME ENVIADO EXITOSAMENTE")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error enviando informe: ${e.message}", e)
            sendInternalError(output)
        }
    }

    /**
     * Maneja petición del informe de salud (OBSOLETO - AHORA SIN SEGURIDAD)
     */
    private fun handleHealthReportRequest(path: String, output: PrintWriter) {
        try {
            // Verificar token en la URL
            val tokenParam = extractTokenFromPath(path)
            if (tokenParam != accessToken) {
                Log.w(TAG, "⚠️ Acceso denegado - Token inválido")
                sendUnauthorized(output)
                return
            }
            
            Log.e(TAG, "✅ === ENVIANDO INFORME DE SALUD ===")
            Log.e(TAG, "📄 HTML A ENVIAR - TAMAÑO: ${healthReportHtml.length} caracteres")
            Log.e(TAG, "🔍 CONTENIDO HTML (primeros 1000 chars):")
            Log.e(TAG, healthReportHtml.take(1000))
            Log.e(TAG, "=== FIN ENVÍO ===")
            
            // Enviar informe de salud
            output.println("HTTP/1.1 200 OK")
            output.println("Content-Type: text/html; charset=UTF-8")
            output.println("Connection: close")
            output.println()
            output.println(healthReportHtml)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error enviando informe de salud: ${e.message}", e)
            sendInternalError(output)
        }
    }
    
    /**
     * Envía página de inicio
     */
    private fun sendLandingPage(output: PrintWriter) {
        val html = """
            <!DOCTYPE html>
            <html lang="es">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Lyrion Health Server</title>
                <style>
                    body { 
                        font-family: Arial, sans-serif; 
                        text-align: center; 
                        margin: 50px; 
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        color: white;
                    }
                    .container { 
                        background: rgba(255,255,255,0.1); 
                        padding: 30px; 
                        border-radius: 15px; 
                        backdrop-filter: blur(10px);
                    }
                    .logo { font-size: 2.5em; margin-bottom: 20px; }
                    .description { font-size: 1.2em; margin-bottom: 30px; opacity: 0.9; }
                    .warning { 
                        background: rgba(255,193,7,0.2); 
                        padding: 15px; 
                        border-radius: 10px; 
                        margin: 20px 0;
                        border-left: 4px solid #ffc107;
                    }
                    a { 
                        color: #ffc107; 
                        text-decoration: none; 
                        font-weight: bold;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="logo">🏥 Lyrion Health Server</div>
                    <div class="description">
                        Servidor temporal para compartir informes médicos de forma segura
                    </div>
                    <div class="warning">
                        ⚠️ Este servidor es temporal y se auto-destruirá en 10 minutos por seguridad
                    </div>
                    <p>Para acceder al informe de salud, necesita el enlace con token de acceso válido.</p>
                    <p><small>Generado por Lyrion Health Assistant</small></p>
                </div>
            </body>
            </html>
        """.trimIndent()
        
        output.println("HTTP/1.1 200 OK")
        output.println("Content-Type: text/html; charset=UTF-8")
        output.println("Connection: close")
        output.println()
        output.println(html)
    }
    
    /**
     * Extrae token de la ruta
     */
    private fun extractTokenFromPath(path: String): String {
        return try {
            val tokenParam = path.substringAfter("token=").substringBefore("&")
            if (tokenParam.isNotEmpty() && tokenParam != path) tokenParam else ""
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * Envía respuesta de acceso no autorizado
     */
    private fun sendUnauthorized(output: PrintWriter) {
        output.println("HTTP/1.1 401 Unauthorized")
        output.println("Content-Type: text/html; charset=UTF-8")
        output.println("Connection: close")
        output.println()
        output.println("<h1>401 - Acceso Denegado</h1><p>Token de acceso inválido o expirado.</p>")
    }
    
    /**
     * Envía respuesta de petición incorrecta
     */
    private fun sendBadRequest(output: PrintWriter) {
        output.println("HTTP/1.1 400 Bad Request")
        output.println("Content-Type: text/html; charset=UTF-8")
        output.println("Connection: close")
        output.println()
        output.println("<h1>400 - Petición Incorrecta</h1>")
    }
    
    /**
     * Envía respuesta de error interno
     */
    private fun sendInternalError(output: PrintWriter) {
        output.println("HTTP/1.1 500 Internal Server Error")
        output.println("Content-Type: text/html; charset=UTF-8")
        output.println("Connection: close")
        output.println()
        output.println("<h1>500 - Error Interno del Servidor</h1>")
    }
    
    /**
     * Envía respuesta de no encontrado
     */
    private fun sendNotFound(output: PrintWriter) {
        output.println("HTTP/1.1 404 Not Found")
        output.println("Content-Type: text/html; charset=UTF-8")
        output.println("Connection: close")
        output.println()
        output.println("<h1>404 - No Encontrado</h1>")
    }
    
    /**
     * Genera token único de acceso
     */
    private fun generateUniqueToken(): String {
        val timestamp = System.currentTimeMillis()
        val random = UUID.randomUUID().toString().replace("-", "").substring(0, 8)
        return "lyrion_${timestamp}_$random"
    }
    
    /**
     * Encuentra puerto disponible
     */
    private fun findAvailablePort(): Int {
        for (port in DEFAULT_PORT..DEFAULT_PORT + 100) {
            try {
                val testSocket = ServerSocket(port)
                testSocket.close()
                return port
            } catch (e: Exception) {
                continue
            }
        }
        throw Exception("No se pudo encontrar un puerto disponible")
    }
    
    /**
     * Genera la URL de acceso SIN TOKEN
     */
    private fun generateAccessUrl(): String {
        val localIp = getLocalIpAddress()
        val port = serverSocket?.localPort ?: DEFAULT_PORT
        return "http://$localIp:$port"
    }
    
    /**
     * Obtiene la dirección IP local del dispositivo
     */
    private fun getLocalIpAddress(): String {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val ip = wifiInfo.ipAddress
            
            return String.format(
                Locale.getDefault(),
                "%d.%d.%d.%d",
                ip and 0xff,
                ip shr 8 and 0xff,
                ip shr 16 and 0xff,
                ip shr 24 and 0xff
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error obteniendo IP local: ${e.message}")
            return "127.0.0.1"
        }
    }
    
    /**
     * Verifica si el servidor está ejecutándose
     */
    fun isServerRunning(): Boolean = isRunning
    
    /**
     * Obtiene información del servidor
     */
    fun getServerInfo(): Map<String, String> {
        return if (isRunning) {
            mapOf(
                "status" to "running",
                "url" to generateAccessUrl(),
                "token" to accessToken,
                "port" to (serverSocket?.localPort?.toString() ?: "unknown")
            )
        } else {
            mapOf("status" to "stopped")
        }
    }
}
