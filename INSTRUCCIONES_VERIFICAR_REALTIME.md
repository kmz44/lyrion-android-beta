# 🔧 Instrucciones para Verificar Realtime en Supabase

## ⚠️ IMPORTANTE: Ejecutar Script SQL Primero

Antes de usar la app, **DEBES ejecutar** el script SQL en tu base de datos de Supabase:

### 1️⃣ Abrir Supabase Dashboard
1. Ve a https://app.supabase.com
2. Selecciona tu proyecto de Lyrion
3. En el menú lateral, click en **SQL Editor**

### 2️⃣ Ejecutar el Script
1. Abre el archivo `enable_realtime_messages.sql` en VS Code
2. **Copia TODO el contenido** del archivo
3. Pégalo en el SQL Editor de Supabase
4. Click en el botón **RUN** (esquina inferior derecha)
5. Espera a que aparezca "Success" (puede decir "No rows returned" o mostrar NOTICE)

**NOTA:** Si ves el error `relation "direct_messages" is already member of publication`, es normal. Significa que Realtime ya está parcialmente habilitado. El script actualizado maneja esto automáticamente.

### 3️⃣ Verificar que Realtime Está Habilitado

Ejecuta esta query en el SQL Editor para confirmar:

```sql
SELECT schemaname, tablename 
FROM pg_publication_tables 
WHERE pubname = 'supabase_realtime';
```

**Resultado esperado:**
Deberías ver 3 filas:
- `public | direct_messages`
- `public | typing_indicators`
- `public | users`

Si no ves las 3 tablas, vuelve a ejecutar el script `enable_realtime_messages.sql`.

### 4️⃣ Verificar Políticas RLS

Ejecuta esta query:

```sql
SELECT tablename, policyname 
FROM pg_policies 
WHERE tablename IN ('direct_messages', 'typing_indicators', 'users')
ORDER BY tablename, policyname;
```

Deberías ver políticas como:
- `Users can read their direct messages realtime`
- `Users can read typing indicators`
- `Users can update their own status`
- etc.

---

## 📱 Cómo Probar el Realtime

### Sistema Híbrido Implementado

La app ahora usa un **sistema híbrido**:
- ✅ **Realtime** como método principal (mensajes instantáneos)
- ✅ **Polling de respaldo** cada 3 segundos (por si Realtime falla)
- ✅ **Estado online/offline** con polling cada 10 segundos

### Probar Mensajes en Tiempo Real

#### Opción 1: Dos Dispositivos/Emuladores
1. Instala la app en dos dispositivos (o un dispositivo + emulador)
2. Inicia sesión con dos cuentas diferentes
3. Abre el chat entre ambas cuentas
4. **Dispositivo A**: Envía un mensaje
5. **Dispositivo B**: El mensaje debería aparecer en **máximo 3 segundos**

#### Opción 2: Navegador + App
1. Abre Supabase Dashboard → Database → Tables → direct_messages
2. En la app, abre un chat
3. En el navegador, inserta manualmente un mensaje:
   ```sql
   INSERT INTO direct_messages (sender_id, receiver_id, content, status)
   VALUES (
     'uuid-del-otro-usuario',
     'uuid-del-usuario-actual',
     'Hola desde el navegador!',
     'sent'
   );
   ```
4. El mensaje debería aparecer en la app en **máximo 3 segundos**

### Probar Indicador "Escribiendo..."

1. **Dispositivo A**: Comienza a escribir un mensaje (no lo envíes aún)
2. **Dispositivo B**: Debería aparecer "Usuario está escribiendo..." con puntos animados
3. **Dispositivo A**: Deja de escribir por 3 segundos
4. **Dispositivo B**: El indicador debería desaparecer

### Probar Estado Online/Offline

1. **Dispositivo A**: Abre el chat
2. **Dispositivo B**: Abre la app (en cualquier pantalla)
3. **Dispositivo A**: En el header del chat, debería mostrar:
   - 🟢 **Verde** si el usuario está activo
   - 🟠 **Naranja** si está ocupado/chateando
   - 🔴 **Rojo** si está ausente
   - ⚪ **Gris** si está offline
4. El estado se actualiza en **máximo 10 segundos**

---

## 🐛 Debugging: Ver Logs en Logcat

Para ver si Realtime está funcionando:

1. Abre **Logcat** en Android Studio
2. Filtra por: `DirectChatScreen`
3. Busca estos mensajes:

### Mensajes de Realtime Funcionando ✅
```
📨 Realtime message received: [contenido del mensaje]
🟢 Status changed to: available
👤 Usuario está escribiendo...
```

### Mensajes de Polling de Respaldo 🔄
```
🔄 Polling found 1 new messages
🔄 Status polling update: available
```

### Mensajes de Error ❌
```
❌ Error collecting messages: [detalle del error]
❌ Error subscribing to typing indicator: [detalle]
❌ Error fetching status: [detalle]
```

---

## ⚡ Comportamiento Esperado

### Si Realtime Funciona Correctamente
- Mensajes aparecen **instantáneamente** (< 1 segundo)
- Indicador "escribiendo..." aparece **inmediatamente**
- Estado online/offline cambia **al instante**
- En logs verás emojis 📨 🟢 👤

### Si Realtime NO Funciona (Polling de Respaldo)
- Mensajes aparecen en **máximo 3 segundos**
- Estado online/offline actualiza en **máximo 10 segundos**
- En logs verás emojis 🔄
- La app funciona perfectamente, solo con un pequeño delay

### Si Nada Funciona ❌
1. Verifica que ejecutaste el script SQL
2. Verifica las queries de verificación en Supabase
3. Revisa los logs en Logcat para ver errores
4. Verifica que tengas conexión a internet
5. Asegúrate de que las credenciales de Supabase sean correctas

---

## 🔧 Solución de Problemas

### Problema: "relation is already member of publication"
**Esto es NORMAL** ✅ - Significa que Realtime ya está parcialmente configurado. El script actualizado maneja esto automáticamente. Solo ejecuta el script completo de nuevo.

### Problema: "Error: relation is not part of publication"
**Solución**: Ejecuta el script SQL nuevamente. Verifica que no haya errores de sintaxis.

### Problema: "Error: permission denied"
**Solución**: Las políticas RLS no están configuradas. Ejecuta el script SQL que las crea.

### Problema: Mensajes no aparecen ni con polling
**Solución**: Hay un problema con las queries. Verifica:
1. Las credenciales de Supabase en `SupabaseClient.kt`
2. Que las tablas existen en la base de datos
3. Los logs en Logcat para ver el error exacto

### Problema: Typing indicator no funciona
**Solución**: 
1. Verifica que existe la tabla `typing_indicators`
2. Ejecuta el script SQL que crea el índice único
3. Verifica las políticas RLS

---

## ✅ Checklist de Verificación

Antes de reportar problemas, verifica:

- [ ] Ejecuté el script `enable_realtime_messages.sql` en Supabase
- [ ] Verifiqué que las 3 tablas están en la publicación de Realtime
- [ ] Las políticas RLS están creadas correctamente
- [ ] La app compila sin errores
- [ ] Tengo conexión a internet estable
- [ ] Los logs en Logcat no muestran errores de autenticación
- [ ] Esperé al menos 10 segundos para ver actualizaciones (polling)
- [ ] Probé con dos dispositivos/cuentas diferentes

---

## 📊 Intervalos de Polling Configurados

- **Mensajes nuevos**: Cada **3 segundos**
- **Estado online/offline**: Cada **10 segundos**
- **Typing indicator**: Solo Realtime (no polling)

Si Realtime funciona, estos intervalos no se usan y todo es instantáneo.

---

## 🎯 Siguiente Paso

1. **Ejecuta el script SQL** en Supabase Dashboard
2. **Instala la app** en dos dispositivos
3. **Abre un chat** entre dos usuarios
4. **Envía un mensaje** desde un dispositivo
5. **Observa** si aparece en el otro dispositivo en máximo 3 segundos

Si todo funciona, ¡Realtime está configurado correctamente! 🎉
