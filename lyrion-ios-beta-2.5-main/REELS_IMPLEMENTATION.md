# Implementación de Reels - Resumen de Cambios

## ✅ Cambios Completados

### 1. Base de Datos (SQL)
**Archivo:** `add_reels_support.sql`

Se agregaron las siguientes columnas a la tabla `posts`:
- `content_type`: TEXT - Indica si es 'post', 'reel', o 'both'
- `duration_seconds`: INTEGER - Duración del video en segundos
- `thumbnail_url`: TEXT - URL del thumbnail/preview del video

También se crearon:
- Índices para búsquedas rápidas
- Vistas (`reels_view` y `posts_view`) para facilitar consultas

### 2. Modelos Swift (PostDTO)
**Archivo:** `lyrion/SupabaseClient.swift`

**PostDTO actualizado con:**
```swift
let content_type: String? // "post", "reel", o "both"
let duration_seconds: Int? // Duración del video
let thumbnail_url: String? // URL del thumbnail

// Computed properties para facilitar el uso
var isVideo: Bool
var isReel: Bool
var isPost: Bool
```

### 3. Métodos de Backend (SupabaseClient)
**Archivo:** `lyrion/SupabaseClient.swift`

#### Métodos agregados/actualizados:

1. **`createPost()`** - Actualizado para soportar `contentType`
   - Ahora acepta parámetro `contentType: String = "post"`
   - Puede crear posts, reels, o ambos

2. **`createVideoPost()`** - NUEVO método para subir videos
   - Sube video a bucket `reel_videos`
   - Opcional: sube thumbnail a bucket `video_thumbnails`
   - Guarda duración del video
   - Soporta `content_type` para definir dónde aparece

3. **`fetchReels()`** - NUEVO método para obtener solo reels
   - Filtra por `media_type = 'video'`
   - Filtra por `content_type IN ('reel', 'both')`
   - Retorna PostDTO con todos los datos necesarios

## 📋 Próximos Pasos (Pendientes)

### Paso 1: Configurar Supabase Storage
**Seguir instrucciones en:** `SUPABASE_STORAGE_SETUP.md`

1. Ejecutar script SQL en Supabase SQL Editor
2. Crear buckets `reel_videos` y `video_thumbnails`
3. Configurar políticas RLS (Row Level Security)

### Paso 2: Actualizar UI de SocialFeedView
**Archivo a modificar:** `lyrion/Views/SocialFeedView.swift`

Agregar:
- [ ] Botón "Reels" en la barra de filtros (junto a "For You", "Mi Red", "Mensajes")
- [ ] Nueva vista `ReelsView` para mostrar reels en formato vertical (estilo TikTok/Instagram)
- [ ] Estado para manejar reels: `@State private var reels: [PostDTO] = []`
- [ ] Lógica para cargar reels cuando se seleccione el filtro

**Ejemplo de cambios necesarios:**
```swift
// En SocialFeedView
let filters = ["For You", "Reels", "Mi Red", "Mensajes"]  // Agregar "Reels"

// En loadTabData()
case "Reels":
    let fetchedReels = try await SupabaseClient.shared.fetchReels()
    await MainActor.run { self.reels = fetchedReels }

// En body
case "Reels":
    ReelsView(reels: reels, isLoading: isLoadingTab)
```

### Paso 3: Crear Vista de Reels
**Nuevo archivo:** `lyrion/Views/ReelsView.swift`

Crear vista estilo TikTok/Instagram Reels:
- [ ] ScrollView vertical con paginación
- [ ] Videos en pantalla completa
- [ ] Autoplay al aparecer en pantalla
- [ ] Controles de play/pause
- [ ] Mostrar usuario, caption, reacciones, comentarios

### Paso 4: Actualizar CreatePostView
**Archivo a modificar:** `lyrion/Views/CreatePostView.swift` (o crear si no existe)

Agregar:
- [ ] Opción para seleccionar Foto o Video
- [ ] Picker de video de la galería
- [ ] Selector de `content_type` para videos:
  - Solo Reels
  - Solo Posts
  - Ambos (por defecto)
- [ ] Toggle para publicar anónimamente
- [ ] Generar thumbnail del video automáticamente
- [ ] Mostrar preview del video antes de publicar

### Paso 5: Crear VideoPlayerView
**Nuevo archivo:** `lyrion/Views/VideoPlayerView.swift`

Crear componente reutilizable para reproducir videos:
- [ ] Usar AVPlayer de AVFoundation
- [ ] Controles personalizados (play/pause, seek, mute)
- [ ] Indicador de progreso
- [ ] Autoplay cuando aparece en pantalla
- [ ] Pause cuando sale de pantalla

### Paso 6: Actualizar UserProfileView
**Archivo a modificar:** `lyrion/Views/UserProfileView.swift`

- [ ] Agregar tab "Reels" (además de Posts y Videos)
- [ ] Filtrar posts del usuario por content_type
- [ ] Mostrar reels del usuario en formato grid o scroll

## 🎨 Componentes UI Necesarios

### 1. ReelsView
- Vista principal de reels
- Scroll vertical con snap
- Video en pantalla completa
- Overlay con información del creador

### 2. VideoPlayerView
- Reproductor de video reutilizable
- Controles personalizados
- Soporte para mute/unmute
- Progress indicator

### 3. CreateVideoPostView
- Selector de video
- Preview antes de publicar
- Generador de thumbnail
- Opciones de content_type

### 4. VideoPickerController
- UIViewControllerRepresentable para seleccionar videos
- Integración con PhotoKit
- Soporte para videos de la cámara

## 📦 Dependencias Necesarias

```swift
import AVFoundation  // Para reproducir videos
import AVKit         // Para controles de video
import PhotosUI      // Para seleccionar videos de la galería
```

## 🗂 Estructura de Archivos Propuesta

```
lyrion/
├── Views/
│   ├── SocialFeedView.swift (Actualizar)
│   ├── ReelsView.swift (Crear)
│   ├── VideoPlayerView.swift (Crear)
│   ├── CreatePostView.swift (Actualizar)
│   ├── CreateVideoPostView.swift (Crear)
│   └── UserProfileView.swift (Actualizar)
├── Helpers/
│   ├── VideoPickerController.swift (Crear)
│   └── ThumbnailGenerator.swift (Crear)
└── SupabaseClient.swift (✅ Ya actualizado)
```

## 🎯 Funcionalidades Clave

1. **Subir Videos:**
   - Seleccionar video de galería o grabar
   - Generar thumbnail automáticamente
   - Elegir si es reel, post, o ambos
   - Publicar anónimamente (opcional)

2. **Ver Reels:**
   - Scroll vertical estilo TikTok
   - Autoplay/autopause
   - Reacciones y comentarios
   - Ver perfil del creador

3. **Gestión de Contenido:**
   - Filtrar por tipo (posts, reels)
   - Ver mis reels en mi perfil
   - Eliminar reels propios

## 📝 Notas Importantes

1. **Límite de tamaño:** Considerar límite de 100MB por video
2. **Compresión:** Implementar compresión de video antes de subir
3. **Rendimiento:** Lazy loading de videos
4. **Caché:** Cachear thumbnails para mejor rendimiento
5. **Conectividad:** Manejar errores de red al subir videos grandes

## 🚀 Orden de Implementación Recomendado

1. ✅ Base de datos y backend (Completado)
2. Configurar Supabase Storage
3. Crear VideoPlayerView
4. Crear ReelsView básica
5. Actualizar SocialFeedView con botón Reels
6. Crear VideoPickerController
7. Actualizar/Crear CreatePostView con soporte de video
8. Agregar tab de Reels en UserProfileView
9. Testing y optimización
10. Pulir UI/UX

---

**Estado Actual:** Backend completo ✅
**Siguiente Paso:** Configurar Supabase Storage según `SUPABASE_STORAGE_SETUP.md`
