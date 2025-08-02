<<<<<<< HEAD
# LYRION - AI Assistant Android App
=======
# CMABIOS - Orabel Android App
>>>>>>> 4218796b74cd301551974ada1f498ea4a5439254

Una aplicación Android que implementa llama.cpp para inferencia de modelos de lenguaje localmente en dispositivos móviles.

## 📋 Requisitos Previos

- **Android Studio** (versión 4.2 o superior)
- **Android NDK** (versión 21 o superior)
- **CMake** (versión 3.18 o superior)
- **Git**
- **Java JDK 8 o superior**
<<<<<<< HEAD
- Dispositivo Android con **API level 35+** (Android 15+)
- Al menos **4GB de RAM** disponible en el dispositivo
- **2GB de almacenamiento libre** para modelos

## 🔐 Firmado de APK - IMPORTANTE

**Este proyecto ya incluye todo lo necesario para firmar la APK correctamente:**

### ✅ Archivos incluidos:
- `upload-keystore.jks` - Keystore para firmado
- `keystore.properties` - Credenciales de firmado
- `lyrion_encrypted_key.zip` - Keystore encriptado para Google Play

### 🔑 Credenciales automáticas:
- **Password**: `LyrionIA2024#`
- **Alias**: `upload`
- **Configuración**: Automática en `keystore.properties`

**¡Ya no necesitas el keystore original!** Todo está configurado para funcionar inmediatamente.

=======
- Dispositivo Android con **API level 21+** (Android 5.0+)
- Al menos **4GB de RAM** disponible en el dispositivo
- **2GB de almacenamiento libre** para modelos

>>>>>>> 4218796b74cd301551974ada1f498ea4a5439254
## 🚀 Instalación y Configuración

### Paso 1: Clonar el Repositorio

```bash
<<<<<<< HEAD
git clone https://github.com/kmz44/Lyrion.git
cd Lyrion
```

### Paso 2: Verificar Keystore (Automático)

**El proyecto ya incluye todo configurado:**
- ✅ `upload-keystore.jks` - Keystore listo para usar
- ✅ `keystore.properties` - Credenciales configuradas
- ✅ Google Play App Signing - Configurado

**No necesitas hacer nada más para el firmado.**

### Paso 3: Configurar llama.cpp

La aplicación depende de llama.cpp para la inferencia de modelos. Sigue estos pasos:

1. **llama.cpp ya está incluido** en el repositorio

2. **Verificar la estructura del proyecto**:
```
Lyrion/
├── llama.cpp/          # Repositorio de llama.cpp (incluido)
├── orabel/             # Módulo principal de la app
├── app/                # Aplicación Android
├── upload-keystore.jks # Keystore para firmado
├── keystore.properties # Credenciales automáticas
└── ...
```

### Paso 4: Configurar Android Studio
=======
git clone https://github.com/kmz44/cmabios.git
cd cmabios
```

### Paso 2: Configurar llama.cpp

La aplicación depende de llama.cpp para la inferencia de modelos. Sigue estos pasos:

1. **Clonar llama.cpp** (si no está incluido):
```bash
git clone https://github.com/ggerganov/llama.cpp.git
```

2. **Verificar la estructura del proyecto**:
```
cmabios/
├── llama.cpp/          # Repositorio de llama.cpp
├── orabel/             # Módulo principal de la app
├── app/                # Aplicación Android
└── ...
```

### Paso 3: Configurar Android Studio
>>>>>>> 4218796b74cd301551974ada1f498ea4a5439254

1. **Abrir el proyecto** en Android Studio
2. **Instalar Android NDK**:
   - Ve a `Tools > SDK Manager`
   - En la pestaña `SDK Tools`, marca `NDK (Side by side)`
   - Instala la versión más reciente

3. **Configurar CMake**:
   - En `SDK Tools`, marca también `CMake`
   - Instala la versión recomendada

<<<<<<< HEAD
### Paso 5: Configurar Variables de Entorno
=======
### Paso 4: Configurar Variables de Entorno
>>>>>>> 4218796b74cd301551974ada1f498ea4a5439254

Crea o edita el archivo `local.properties` en la raíz del proyecto:

```properties
sdk.dir=C\:\\Users\\TuUsuario\\AppData\\Local\\Android\\Sdk
ndk.dir=C\:\\Users\\TuUsuario\\AppData\\Local\\Android\\Sdk\\ndk\\[VERSION]
```

<<<<<<< HEAD
### Paso 6: Compilar el Proyecto

**Para compilación de desarrollo:**
```bash
./gradlew clean
./gradlew assembleDebug
```

**Para generar APK firmada para distribución:**
```bash
./gradlew assembleRelease
```

✅ **La APK se firmará automáticamente** con el keystore incluido.
✅ **Credenciales ya configuradas** en `keystore.properties`
✅ **Compatible con Google Play Store** 

**Ubicación de APKs generadas:**
- **Debug**: `app/build/outputs/apk/debug/app-debug.apk`
- **Release**: `app/build/outputs/apk/release/app-release.apk`

=======
### Paso 5: Compilar el Proyecto

1. **Limpiar el proyecto**:
```bash
./gradlew clean
```

2. **Compilar**:
```bash
./gradlew build
```

3. **Generar APK**:
```bash
./gradlew assembleDebug
```

>>>>>>> 4218796b74cd301551974ada1f498ea4a5439254
## 📱 Instalación de la APK

### Opción 1: Desde Android Studio
1. Conecta tu dispositivo Android
2. Habilita **Depuración USB** en las opciones de desarrollador
3. Ejecuta `Run > Run 'app'`

### Opción 2: Instalación Manual
<<<<<<< HEAD
1. Localiza el APK generado en: `app/build/outputs/apk/release/app-release.apk`
=======
1. Localiza el APK generado en: `app/build/outputs/apk/debug/app-debug.apk`
>>>>>>> 4218796b74cd301551974ada1f498ea4a5439254
2. Transfiere el archivo a tu dispositivo Android
3. Habilita **Fuentes desconocidas** en Configuración > Seguridad
4. Instala el APK tocándolo desde el explorador de archivos

<<<<<<< HEAD
### 📦 Descargar APK Precompilada
Puedes descargar la versión más reciente desde GitHub Releases:
```
https://github.com/kmz44/Lyrion/releases/latest
```

## 🔐 Información de Firmado

### ✅ Sistema de Firmado Configurado
- **Google Play App Signing**: Activado
- **Upload Key**: Incluido en el repositorio (`upload-keystore.jks`)
- **Credenciales**: Configuradas automáticamente
- **Compatibilidad**: Android 15+ (API 35)

### 🛡️ Seguridad del Keystore
- Keystore encriptado para Google Play (`lyrion_encrypted_key.zip`)
- Credenciales respaldadas en `CREDENCIALES_IMPORTANTES.txt`
- Sistema de doble keystore (signing + upload keys)

=======
>>>>>>> 4218796b74cd301551974ada1f498ea4a5439254
## 🧠 Configuración de Modelos

### Modelos Recomendados

La aplicación funciona mejor con modelos GGUF cuantizados. Modelos recomendados:

- **Modelos pequeños (1-3B parámetros)**:
  - `TinyLlama-1.1B-Chat-v1.0.Q4_K_M.gguf`
  - `phi-2.Q4_K_M.gguf`

- **Modelos medianos (7B parámetros)**:
  - `llama-2-7b-chat.Q4_K_M.gguf`
  - `mistral-7b-instruct-v0.1.Q4_K_M.gguf`

### Descarga e Instalación de Modelos

1. **Descargar modelos** desde [Hugging Face](https://huggingface.co/models?library=gguf)

2. **Transferir al dispositivo**:
   - Copia el archivo `.gguf` a la carpeta `Downloads` de tu dispositivo
   - O usa la carpeta `Android/data/io.orabel.orabelandroid/files/`

3. **Configurar en la app**:
   - Abre la aplicación
   - Ve a configuración de modelos
   - Selecciona la ruta del modelo descargado

## ⚙️ Configuración de Parámetros

### Parámetros de Inferencia

- **Temperature** (0.1 - 2.0): Controla la creatividad de las respuestas
  - `0.1-0.7`: Respuestas más conservadoras y predecibles
  - `0.8-1.2`: Balance entre creatividad y coherencia
  - `1.3-2.0`: Respuestas más creativas pero menos predecibles

- **Min-P** (0.0 - 1.0): Control de probabilidad mínima
  - `0.05-0.1`: Recomendado para la mayoría de casos

### Rendimiento Recomendado por Dispositivo

| Tipo de Dispositivo | RAM | Modelo Recomendado | Configuración |
|---------------------|-----|-------------------|---------------|
| Gama baja | 4-6GB | TinyLlama 1.1B Q4 | Temp: 0.7, Min-P: 0.05 |
| Gama media | 6-8GB | Phi-2 Q4 o Llama-2 7B Q4 | Temp: 0.8, Min-P: 0.05 |
| Gama alta | 8GB+ | Llama-2 7B Q4 o superior | Temp: 0.9, Min-P: 0.05 |

## 🔧 Solución de Problemas

### Errores Comunes

1. **`UnsatisfiedLinkError`**:
   - Asegúrate de que el NDK esté correctamente instalado
   - Verifica que todas las librerías nativas se hayan compilado

2. **Aplicación se cierra al cargar modelo**:
   - Verifica que el dispositivo tenga suficiente RAM libre
   - Prueba con un modelo más pequeño

3. **Modelo no se carga**:
   - Verifica que el archivo `.gguf` no esté corrupto
   - Asegúrate de que el path del archivo sea correcto

4. **Inferencia muy lenta**:
   - Reduce el tamaño del modelo
   - Cierra otras aplicaciones para liberar RAM
   - Verifica que el dispositivo no esté en modo de ahorro de energía

### Logs de Depuración

Para obtener logs detallados:
```bash
adb logcat | grep "io.orabel.orabelandroid"
```

## 🤝 Contribuciones

1. Fork el repositorio
2. Crea una rama para tu feature (`git checkout -b feature/NuevaCaracteristica`)
3. Commit tus cambios (`git commit -m 'Agregar nueva característica'`)
4. Push a la rama (`git push origin feature/NuevaCaracteristica`)
5. Abre un Pull Request

## 📄 Licencia

Este proyecto está bajo la licencia MIT. Ver el archivo `LICENSE` para más detalles.

## 🙏 Reconocimientos

- [llama.cpp](https://github.com/ggerganov/llama.cpp) - Por el motor de inferencia
- [Jetpack Compose](https://developer.android.com/jetpack/compose) - Para la interfaz de usuario
- Comunidad de desarrolladores de IA local

## 📞 Soporte

Si encuentras problemas o tienes preguntas:
<<<<<<< HEAD
- Abre un issue en GitHub: https://github.com/kmz44/Lyrion/issues
- Descarga la APK: https://github.com/kmz44/Lyrion/releases
- Revisa la documentación de [llama.cpp](https://github.com/ggerganov/llama.cpp)
- Consulta los logs de la aplicación para más detalles

## 🔐 Notas Importantes de Firmado

**Para desarrolladores:**
- ✅ Todo el sistema de keystores está configurado automáticamente
- ✅ Compatible con Google Play Store sin configuración adicional
- ✅ Credenciales seguras incluidas en el repositorio
- ✅ Sistema Google Play App Signing configurado

**Ya no necesitas:**
- ❌ Crear keystores manualmente
- ❌ Configurar credenciales de firmado
- ❌ Preocuparte por pérdida de keystores originales
- ❌ Configuración manual de Google Play signing

---

**Nota**: Esta aplicación está optimizada para Android 15+ y incluye sistema completo de firmado automático.
=======
- Abre un issue en GitHub
- Revisa la documentación de [llama.cpp](https://github.com/ggerganov/llama.cpp)
- Consulta los logs de la aplicación para más detalles

---

**Nota**: Esta aplicación está en desarrollo activo. Algunas características pueden estar incompletas o experimentales.
>>>>>>> 4218796b74cd301551974ada1f498ea4a5439254
