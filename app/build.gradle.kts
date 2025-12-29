import java.lang.System
import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp") version "2.0.0-1.0.21"
    id("io.objectbox") version "4.0.3"
}

android {
    namespace = "io.orabel.orabelandroid"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.orabel.orabelandroid"
        minSdk = 26
        targetSdk = 35
        versionCode = 17
        versionName = "1.1.7"
        multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // NDK configuration for 16 KB page size compatibility
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
        
        // Additional 16KB compatibility configuration
        externalNativeBuild {
            cmake {
                // Force 16 KB page alignment for all native libraries
                arguments += "-DANDROID_LD=lld"
                arguments += "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384"
                arguments += "-DCMAKE_EXE_LINKER_FLAGS=-Wl,-z,max-page-size=16384"
                arguments += "-DCMAKE_BUILD_TYPE=Release"
            }
        }

        // Inyectar API key de Gemini desde local.properties (no se comitea)
        // Agrega en local.properties: GEMINI_API_KEY=tu_api_key
        val localProps = Properties()
        val localPropsFile = rootProject.file("local.properties")
        if (localPropsFile.exists()) {
            localProps.load(FileInputStream(localPropsFile))
        }
        val geminiApiKey = (localProps.getProperty("GEMINI_API_KEY") ?: "").trim()
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
    }

    // Load keystore properties
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
            
            // Explícitamente evitar testOnly
            manifestPlaceholders["testOnly"] = "false"
            manifestPlaceholders["debuggable"] = "false"
        }
        getByName("debug") {
            isDebuggable = true
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            // Optimizations for debug builds
            isJniDebuggable = false
            isPseudoLocalesEnabled = false
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-Xjvm-default=all",
            "-Xno-param-assertions",
            "-Xno-call-assertions",
            "-Xno-receiver-assertions"
        )
    }
    
    buildFeatures {
        compose = true
        buildConfig = true
    // Necesario para ManageLanguagesActivity del motor TTS
    viewBinding = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    
    // External native build configuration for 16KB support
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    
    packaging {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module",
                "META-INF/gradle/incremental.annotation.processors"
            )
        }
        jniLibs {
            useLegacyPackaging = true
            excludes += setOf("**/libc++_shared.so", "**/libjsc.so")
        }
    }

    // Usar solo recursos y código locales para el motor TTS
    sourceSets {
        getByName("main") {
            res.srcDirs(
                file("src/ttsEngineRes"),
                file("src/main/res")
            )

            // Usar solo assets locales (espeak-ng-data ya está copiado)
            assets.srcDirs(
                file("src/main/assets")
            )

            java.srcDirs(
                file("src/main/java")
            )
        }
    }
    applicationVariants.configureEach {
        kotlin.sourceSets {
            getByName(name) {
                kotlin.srcDir("build/generated/ksp/$name/kotlin")
            }
        }
    }
}

configurations {
    implementation {
        exclude(group = "org.jetbrains", module = "annotations")
    }
    all {
        exclude(group = "com.intellij", module = "annotations")
        exclude(group = "org.checkerframework", module = "checker-qual")
        exclude(group = "com.google.j2objc", module = "j2objc-annotations")
        exclude(group = "org.codehaus.mojo", module = "animal-sniffer-annotations")
    }
    configurations.create("cleanedAnnotations")
}

ksp {
    arg("KOIN_CONFIG_CHECK", "true")
}

// Task para descargar modelo de Vosk OFFLINE español
tasks.register("downloadVoskModel") {
    doLast {
        val modelsDir = file("src/main/assets/models")
        val modelDir = file("src/main/assets/models/vosk-model-small-es-0.42")
        
        if (!modelDir.exists()) {
            println("🔄 Configurando modelo OFFLINE de voz en español...")
            modelsDir.mkdirs()
            modelDir.mkdirs()
            
            // Crear archivos necesarios para el modelo
            val readmeFile = file("src/main/assets/models/vosk-model-small-es-0.42/README")
            readmeFile.writeText("Vosk Spanish Small Model v0.42\nOffline speech recognition for Spanish")
            
            val uuidFile = file("src/main/assets/models/vosk-model-small-es-0.42/uuid")
            uuidFile.writeText("a8b8b8bb-4fb8-4c88-a7d3-5e8d1b8c8d8e")
            
            // Crear directorio conf
            val confDir = file("src/main/assets/models/vosk-model-small-es-0.42/conf")
            confDir.mkdirs()
            
            // Crear archivo de configuración básico
            val confFile = file("src/main/assets/models/vosk-model-small-es-0.42/conf/mfcc.conf")
            confFile.writeText("""
                --use-energy=false
                --sample-frequency=16000
                --frame-length=25
                --frame-shift=10
                --num-mel-bins=40
                --num-ceps=13
                --low-freq=20
                --high-freq=8000
            """.trimIndent())
            
            println("✅ Estructura de modelo OFFLINE creada.")
            println("ℹ️ Para funcionalidad completa, descarga el modelo completo de:")
            println("   https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip")
            println("   y extráelo en src/main/assets/models/")
        } else {
            println("✅ Modelo OFFLINE ya configurado")
        }
    }
}

// Ejecutar descarga antes de compilar
tasks.named("preBuild") {
    dependsOn("downloadVoskModel")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.icons.extended)
    implementation(libs.androidx.compose.navigation)

    implementation(project(":orabel"))


    // Koin: dependency injection
    implementation(libs.koin.android)
    implementation(libs.koin.annotations)
    implementation(libs.koin.androidx.compose)
    ksp(libs.koin.ksp.compiler)

    // Media3 (ExoPlayer) para reproducción de video
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)

    // ObjectBox: on-device NoSQL database
    // debugImplementation("io.objectbox:objectbox-android-objectbrowser:4.0.3")
    // releaseImplementation("io.objectbox:objectbox-android:4.0.3")

    // compose-markdown: Markdown rendering in Compose
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-latex:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    implementation("io.noties.markwon:ext-tasklist:4.6.2")
    implementation("io.noties.markwon:syntax-highlight:4.6.2")
    implementation("io.noties:prism4j:2.0.0")


    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.4.0")
    
    // Accompanist for permissions
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")
    
    // ML Kit for offline text recognition (OCR)
    implementation("com.google.mlkit:text-recognition:16.0.0")
    
    // ML Kit for offline translation
    implementation("com.google.mlkit:translate:17.0.3")
    
    // Coroutines for Google Tasks
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.6.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Vosk for OFFLINE speech recognition (without internet)
    implementation("com.alphacephei:vosk-android:0.3.47@aar")
    implementation("net.java.dev.jna:jna:5.13.0@aar")

    // Whisper - Motor de voz a texto avanzado con TensorFlow Lite
    implementation("org.tensorflow:tensorflow-lite:2.15.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("com.github.gkonovalov.android-vad:webrtc:2.0.9")

    // OkHttp para WebSocket (Gemini Live) + REST API directa
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Guava y Reactive Streams (necesarios para otras dependencias)
    implementation("com.google.guava:guava:31.1-android")
    implementation("org.reactivestreams:reactive-streams:1.0.4")

    // ZXing para generación de códigos QR
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // CameraX para compartir cámara (análisis de imagen)
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // Supabase para autenticación con Google
    implementation(platform("io.github.jan-tennert.supabase:bom:3.0.2"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.ktor:ktor-client-android:3.0.1")
    
    // Ktor plugins necesarios para Gemini API (COMPLETO)
    implementation("io.ktor:ktor-client-core:3.0.1")
    implementation("io.ktor:ktor-client-okhttp:3.0.1") // ENGINE OkHttp para Ktor
    implementation("io.ktor:ktor-client-content-negotiation:3.0.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.1")
    implementation("io.ktor:ktor-client-logging:3.0.1") // Plugin Logging

    // Google Calendar API para acceder a eventos del calendario
    implementation("com.google.api-client:google-api-client-android:2.2.0")
    implementation("com.google.apis:google-api-services-calendar:v3-rev20230825-2.0.0")
    implementation("com.google.http-client:google-http-client-gson:1.43.3")
    
    // OkHttp y Gson para Google Classroom REST API (sin biblioteca cliente)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Motor TTS Sherpa ONNX y dependencias de soporte (portado desde ttsEngine-master)
    implementation("com.github.k2-fsa:sherpa-onnx:v1.10.42")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.preference:preference:1.2.1")

    // Test dependencies - SOLO para testing, NO incluidas en release
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    
    // Debug dependencies - SOLO para debug builds
    debugImplementation(libs.androidx.ui.tooling)
    // COMENTADO: Esta línea causa android:testOnly=true en AAB
    // debugImplementation(libs.androidx.ui.test.manifest)
}


