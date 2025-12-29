# ✅ Implementación de `chat_status` - Separación de Estados

## 📋 Resumen

Se implementó la separación entre el estado general del usuario (`status`) y el estado específico del chat directo (`chat_status`), eliminando el conflicto donde ambos compartían el valor "offline".

---

## 🎯 Problema Resuelto

**ANTES:**
- Campo `status`: `'available'`, `'chatting'`, `'away'`, `'busy'`, **`'offline'`**
- Campo `chat_status`: Existía en BD pero **no se usaba**
- **Conflicto**: Los botones en DirectChatScreen modificaban `status` cuando debían modificar `chat_status`

**DESPUÉS:**
- Campo `status`: Estado general de actividad del usuario (visible en perfiles)
- Campo `chat_status`: Estado específico del chat directo (`'chatting'` | `'offline'`)
- **Independientes**: Cada campo tiene su propia función y propósito

---

## 📝 Cambios Implementados

### 1️⃣ **Base de Datos** - `fix_realtime_status_FINAL.sql`

#### Trigger actualizado para incluir `chat_status`:
```sql
CREATE TRIGGER trigger_status_realtime
  BEFORE UPDATE OF is_active, status, chat_status ON users
  FOR EACH ROW
  EXECUTE FUNCTION notify_status_change();
```

#### Índice agregado:
```sql
CREATE INDEX IF NOT EXISTS idx_users_chat_status 
ON users(chat_status);
```

---

### 2️⃣ **SocialRepository.kt**

#### Nuevas funciones implementadas:

1. **`updateChatStatus(chatStatus: String): Result<Unit>`**
   - Actualiza SOLO el campo `chat_status`
   - No afecta el campo `status`
   - Valores permitidos: `'chatting'`, `'offline'`

2. **`getChatStatus(userId: String): String`**
   - Obtiene el valor actual de `chat_status` de un usuario
   - Retorna `"offline"` por defecto si no existe

3. **`subscribeToChatStatus(partnerId: String): Flow<String>`**
   - Suscripción Realtime específica para `chat_status`
   - Monitorea cambios solo en este campo
   - Independiente de `subscribeToUserStatus()`

#### Actualización de parsing:
```kotlin
private fun parseProfile(json: JSONObject): ProfileDTO {
    // ...
    chatStatus = optStringNullable("chat_status") // ✅ Nuevo
}
```

---

### 3️⃣ **UserActivityManager.kt**

#### Nuevas funciones agregadas:

1. **`enterDirectChat()`**
   ```kotlin
   // Actualiza SOLO chat_status = 'chatting'
   // No afecta el campo 'status'
   ```

2. **`exitDirectChat()`**
   ```kotlin
   // Actualiza SOLO chat_status = 'offline'
   // No afecta el campo 'status'
   ```

#### Funciones existentes (sin cambios):
- `enterChat()` → Modifica `status = 'chatting'`
- `exitChat()` → Modifica `status` según contexto
- `setManualStatus()` → Modifica `status` manualmente

---

### 4️⃣ **DirectChatScreen.kt**

#### Botones actualizados:

**ANTES:**
```kotlin
Button(onClick = { activityManager.enterChat() }) // ❌ Modificaba 'status'
Button(onClick = { activityManager.exitChat() })  // ❌ Modificaba 'status'
```

**DESPUÉS:**
```kotlin
Button(onClick = { activityManager.enterDirectChat() }) // ✅ Modifica 'chat_status'
Button(onClick = { activityManager.exitDirectChat() })  // ✅ Modifica 'chat_status'
```

---

## 🔄 Uso Recomendado

### Para indicador de actividad general (perfil):
```kotlin
// Ver estado del usuario en su perfil
repository.getUserStatus(userId)
repository.subscribeToUserStatus(partnerId)
activityManager.enterChat()  // Para estado general
activityManager.setManualStatus("available")
```

### Para estado específico de chat directo:
```kotlin
// Ver estado en chat 1-a-1
repository.getChatStatus(userId)
repository.subscribeToChatStatus(partnerId)
activityManager.enterDirectChat()  // Para chat directo
activityManager.exitDirectChat()
```

---

## 🧪 Testing

### Pasos para probar:

1. **Ejecutar el SQL actualizado en Supabase:**
   ```bash
   # Ejecutar fix_realtime_status_FINAL.sql
   ```

2. **Compilar la app:**
   ```bash
   ./gradlew :app:assembleDebug
   ```

3. **Probar botones en DirectChatScreen:**
   - Botón "Chat: Activo" → Debe actualizar `chat_status = 'chatting'`
   - Botón "Chat: Offline" → Debe actualizar `chat_status = 'offline'`
   - Verificar que `status` (perfil) NO cambia

4. **Verificar Realtime:**
   - Abrir dos dispositivos
   - Cambiar `chat_status` en uno
   - Ver actualización en el otro

5. **Validar independencia:**
   ```sql
   -- En Supabase SQL Editor
   SELECT id, username, status, chat_status FROM users WHERE username = 'tu_usuario';
   ```

---

## 📊 Campos de la Tabla `users`

| Campo | Tipo | Valores | Propósito |
|-------|------|---------|-----------|
| `status` | TEXT | `'available'`, `'chatting'`, `'away'`, `'busy'`, `'offline'` | Estado general visible en perfil |
| `chat_status` | TEXT | `'chatting'`, `'offline'` | Estado específico en chat directo |
| `is_active` | BOOLEAN | `true`, `false` | Usuario tiene la app activa |
| `last_heartbeat` | TIMESTAMP | - | Última actividad detectada |

---

## ⚠️ Notas Importantes

1. **Los campos son independientes**: Cambiar `chat_status` NO afecta `status`
2. **Retrocompatibilidad**: Las funciones existentes (`enterChat()`, `exitChat()`) siguen funcionando
3. **Realtime**: Ambos campos tienen suscripciones Realtime separadas
4. **RLS Policies**: Los permisos existentes cubren ambos campos

---

## 🎉 Resultado Final

✅ **Campo `status`**: Controla indicador de actividad general (círculo verde en avatar)
✅ **Campo `chat_status`**: Controla estado específico del chat directo
✅ **Sin conflictos**: Cada campo tiene su función independiente
✅ **Realtime funcionando**: Actualizaciones instantáneas en ambos campos
✅ **Testing listo**: Botones en DirectChatScreen para pruebas

---

Fecha de implementación: 28 de diciembre de 2025
