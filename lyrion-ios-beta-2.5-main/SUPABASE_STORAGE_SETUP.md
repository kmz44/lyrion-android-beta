# Configuración de Supabase Storage para Videos (Reels)

## Paso 1: Ejecutar Script SQL
1. Ve a tu proyecto de Supabase
2. Abre el **SQL Editor**
3. Copia y pega el contenido de `add_reels_support.sql`
4. Ejecuta el script

## Paso 2: Crear Bucket de Storage para Videos

1. En Supabase, ve a **Storage** en el menú lateral
2. Haz clic en **Create a new bucket**
3. Configura el bucket para videos:

### Configuración del Bucket "reel_videos":
```
Nombre: reel_videos
Público: ✓ (Marcado)
Allowed MIME types: video/mp4, video/quicktime, video/mov, video/avi
Max file size: 100 MB (o el tamaño que prefieras)
```

### Configuración del Bucket "video_thumbnails":
```
Nombre: video_thumbnails
Público: ✓ (Marcado)
Allowed MIME types: image/jpeg, image/png, image/webp
Max file size: 5 MB
```

## Paso 3: Configurar Políticas de Storage (RLS)

### Para el bucket "reel_videos":

#### Política de INSERT (Subir videos):
```sql
CREATE POLICY "Usuarios autenticados pueden subir videos"
ON storage.objects FOR INSERT
TO authenticated
WITH CHECK (
    bucket_id = 'reel_videos' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);
```

#### Política de SELECT (Ver videos):
```sql
CREATE POLICY "Cualquiera puede ver videos públicos"
ON storage.objects FOR SELECT
TO public
USING (bucket_id = 'reel_videos');
```

#### Política de DELETE (Eliminar videos):
```sql
CREATE POLICY "Usuarios pueden eliminar sus propios videos"
ON storage.objects FOR DELETE
TO authenticated
USING (
    bucket_id = 'reel_videos' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);
```

### Para el bucket "video_thumbnails":

#### Política de INSERT:
```sql
CREATE POLICY "Usuarios autenticados pueden subir thumbnails"
ON storage.objects FOR INSERT
TO authenticated
WITH CHECK (
    bucket_id = 'video_thumbnails' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);
```

#### Política de SELECT:
```sql
CREATE POLICY "Cualquiera puede ver thumbnails"
ON storage.objects FOR SELECT
TO public
USING (bucket_id = 'video_thumbnails');
```

#### Política de DELETE:
```sql
CREATE POLICY "Usuarios pueden eliminar sus propios thumbnails"
ON storage.objects FOR DELETE
TO authenticated
USING (
    bucket_id = 'video_thumbnails' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);
```

## Paso 4: Verificar la Configuración

Ejecuta esta query en SQL Editor para verificar:

```sql
-- Ver columnas de la tabla posts
SELECT column_name, data_type, column_default
FROM information_schema.columns 
WHERE table_name = 'posts' 
AND table_schema = 'public';

-- Ver buckets de storage
SELECT * FROM storage.buckets;
```

## Estructura de Archivos en Storage

Los videos se guardarán con esta estructura:
```
reel_videos/
  └── {user_id}/
      └── {video_id}.mp4

video_thumbnails/
  └── {user_id}/
      └── {video_id}_thumb.jpg
```

## Notas Importantes

1. **Límite de tamaño**: Ajusta el tamaño máximo según tus necesidades
2. **MIME types**: Agrega más formatos de video si es necesario
3. **Compresión**: Considera implementar compresión de video del lado del servidor
4. **CDN**: Supabase Storage ya usa CDN global, los videos se servirán rápido
5. **Costos**: Verifica los límites de tu plan de Supabase

## Estructura de la Base de Datos Actualizada

Tabla `posts` ahora incluye:
- `content_type`: 'post', 'reel', o 'both'
- `media_type`: 'image' o 'video' (ya existía)
- `is_anonymous`: boolean (ya existía)
- `duration_seconds`: duración del video en segundos
- `thumbnail_url`: URL del thumbnail del video

## Próximos Pasos

Después de configurar Supabase:
1. Actualizar modelos en Swift
2. Crear UI para subir videos
3. Implementar vista de Reels
4. Agregar botón de filtro "Reels" en la UI
