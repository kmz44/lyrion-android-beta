# 🚀 Lyrion IA - Configuración de Release para Google Play Store

## ✅ Archivos Generados para Google Play Store

### 📱 APK Firmado (app-release.apk)
- **Ubicación:** `app/build/outputs/apk/release/app-release.apk`
- **Tamaño:** ~69.6 MB
- **Firma:** Certificado propio con keystore `lyrion-release-key.keystore`
- **Versión:** 1.0.1 (versionCode: 2)

### 📦 Android App Bundle (app-release.aab) - **RECOMENDADO para Play Store**
- **Ubicación:** `app/build/outputs/bundle/release/app-release.aab`
- **Tamaño:** ~118.3 MB
- **Firma:** Certificado propio con keystore `lyrion-release-key.keystore`
- **Versión:** 1.0.1 (versionCode: 2)

## 🔐 Configuración de Keystore

### Información del Certificado:
```
Alias: lyrion
Nombre: CN=Lyrion IA, OU=Development, O=Lyrion, L=Mexico City, ST=Mexico, C=MX
Algoritmo: RSA 2048 bits
Validez: 10,000 días (hasta 2052)
SHA1: 89:83:8C:FA:CB:C8:8B:F0:45:4F:4F:22:AD:79:B5:75:C4:2E:1F:9F
SHA256: A3:AE:9E:26:84:86:4B:D8:92:E8:AD:DE:5F:E8:07:FE:AF:B5:82:B0:61:66:47:D6:CE:1E:57:1B:87:92:11:BA
```

### ⚠️ Archivos de Seguridad (NO subir a Git):
- `keystore.properties` - Contiene las credenciales del keystore
- `app/lyrion-release-key.keystore` - Archivo del keystore para firmar releases

## 🛠️ Comandos para Generar Releases

### Generar APK firmado:
```bash
./gradlew assembleRelease
```

### Generar Android App Bundle (AAB) - **Recomendado**:
```bash
./gradlew bundleRelease
```

## 📋 Información de la Aplicación

- **Package ID:** io.orabel.orabelandroid
- **App Name:** Lyrion IA
- **Version Name:** 1.0.1
- **Version Code:** 2
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 34 (Android 14)
- **Compile SDK:** 35

## 🎯 Funcionalidades Principales

### ✅ Implementadas:
- **Chat IA Offline** - Usando llama.cpp con modelos locales
- **OCR (Imagen a Texto)** - ML Kit Text Recognition offline
- **TTS (Texto a Voz)** - Android TextToSpeech nativo
- **Traducción** - ML Kit Translate offline (ES ↔ EN)
- **STT (Voz a Texto)** - Tecnologías nativas Android
- **UI Moderna** - Material 3 con Jetpack Compose
- **Gestión de Modelos** - Descarga y administración de modelos LLM
- **Gestión de Tareas** - Sistema de plantillas para IA
- **Temas** - Soporte para modo claro/oscuro

### 📚 Librerías y Créditos:
- **llama.cpp** (MIT License) - Motor de inferencia IA
- **ML Kit** (Google) - OCR y traducción offline
- **Android TextToSpeech** (Apache 2.0) - Síntesis de voz
- **Jetpack Compose** (Apache 2.0) - UI moderna
- **Kotlin** (Apache 2.0) - Lenguaje de programación

## 🚀 Pasos para Subir a Google Play Store

### 1. Preparar la Consola de Google Play:
- Crear cuenta de desarrollador en Google Play Console
- Pagar la tarifa única de registro ($25 USD)
- Configurar información del desarrollador

### 2. Crear Nueva Aplicación:
- Usar el **Android App Bundle (AAB)** en lugar del APK
- Subir: `app/build/outputs/bundle/release/app-release.aab`
- Completar información de la tienda (descripción, capturas, etc.)

### 3. Configurar Metadatos:
- **Título:** Lyrion IA
- **Descripción Corta:** Asistente IA offline con capacidades de chat, OCR, TTS y traducción
- **Descripción Completa:** [Crear descripción detallada de las funcionalidades]
- **Categoría:** Productividad o Herramientas
- **Clasificación de Contenido:** Para todas las edades

### 4. Configuraciones Importantes:
- **Política de Privacidad:** Requerida (crear URL pública)
- **Permisos:** Revisar y justificar permisos de cámara y almacenamiento
- **Distribución:** Seleccionar países/regiones

### 5. Información Técnica:
- **Firma de App:** Usar Google Play App Signing
- **Actualizaciones:** Incrementar versionCode para futuras versiones
- **Testing:** Configurar track de pruebas internas/cerradas antes del lanzamiento

## 🔄 Para Futuras Actualizaciones

1. Incrementar `versionCode` y `versionName` en `app/build.gradle.kts`
2. Generar nuevo AAB: `./gradlew bundleRelease`
3. Subir a Google Play Console en la sección "Gestión de versiones"

## 📞 Contacto

**Desarrollador:** Kevin Marquez Melendez  
**Email:** kevinmarquezmelendez10@gmail.com  
**Fecha de Release:** Julio 2025

---

**IMPORTANTE:** Mantener seguros los archivos `keystore.properties` y `lyrion-release-key.keystore`. Son necesarios para firmar futuras actualizaciones de la aplicación.
