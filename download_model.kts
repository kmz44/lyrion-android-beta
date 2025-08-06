import java.io.*
import java.net.URL
import java.util.zip.ZipInputStream

fun downloadVoskModel() {
    val modelsDir = File("app/src/main/assets/models")
    if (!modelsDir.exists()) {
        modelsDir.mkdirs()
    }
    
    val modelDir = File(modelsDir, "vosk-model-small-es-0.42")
    if (!modelDir.exists()) {
        println("Descargando modelo de voz español...")
        
        val url = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip"
        val zipFile = File(modelsDir, "vosk-model-small-es-0.42.zip")
        
        try {
            URL(url).openStream().use { input ->
                FileOutputStream(zipFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            // Extraer el zip
            ZipInputStream(FileInputStream(zipFile)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val file = File(modelsDir, entry.name)
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        FileOutputStream(file).use { output ->
                            zip.copyTo(output)
                        }
                    }
                    entry = zip.nextEntry
                }
            }
            
            zipFile.delete()
            println("Modelo descargado y extraído correctamente")
            
        } catch (e: Exception) {
            println("Error descargando modelo: ${e.message}")
        }
    } else {
        println("Modelo ya existe")
    }
}

downloadVoskModel()
