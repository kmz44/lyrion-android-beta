-- ============================================
-- HABILITAR REALTIME PARA MENSAJES DIRECTOS
-- ============================================
-- Ejecutar este script en el SQL Editor de Supabase
-- Este script es idempotente - se puede ejecutar múltiples veces

-- 1. Función helper para agregar tabla a publicación de forma segura
CREATE OR REPLACE FUNCTION add_table_to_publication(table_name text)
RETURNS void AS $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_publication_tables 
    WHERE pubname = 'supabase_realtime' 
    AND schemaname = 'public'
    AND tablename = table_name
  ) THEN
    EXECUTE format('ALTER PUBLICATION supabase_realtime ADD TABLE %I', table_name);
    RAISE NOTICE 'Tabla % agregada a supabase_realtime', table_name;
  ELSE
    RAISE NOTICE 'Tabla % ya está en supabase_realtime', table_name;
  END IF;
END;
$$ LANGUAGE plpgsql;

-- 2. Habilitar Realtime para las tablas
SELECT add_table_to_publication('direct_messages');
SELECT add_table_to_publication('typing_indicators');
SELECT add_table_to_publication('users');

-- 4. Crear constraint único para typing_indicators (requerido para UPSERT)
drop index if exists typing_indicators_unique_pair;
create unique index typing_indicators_unique_pair on typing_indicators(user_id, partner_id);

-- 5. Crear índices para mejorar rendimiento de queries realtime
create index if not exists direct_messages_sender_receiver_idx on direct_messages(sender_id, receiver_id, created_at desc);
create index if not exists direct_messages_created_at_idx on direct_messages(created_at desc);
create index if not exists typing_indicators_partner_idx on typing_indicators(partner_id, user_id);
create index if not exists users_status_idx on users(status, is_active);

-- 6. Crear función para actualizar last_active_at automáticamente
create or replace function update_user_last_active()
returns trigger as $$
begin
  update users 
  set last_active_at = now()
  where id = NEW.sender_id;
  return NEW;
end;
$$ language plpgsql;

-- 7. Crear trigger para actualizar last_active_at cuando se envía mensaje
drop trigger if exists update_last_active_on_message on direct_messages;
create trigger update_last_active_on_message
  after insert on direct_messages
  for each row
  execute function update_user_last_active();

-- 8. Verificar que las publicaciones están activas
-- Ejecutar esta query para confirmar:
-- SELECT * FROM pg_publication_tables WHERE pubname = 'supabase_realtime';

-- 9. Políticas RLS para permitir lectura de mensajes en tiempo real

-- Política para leer mensajes donde soy sender o receiver
drop policy if exists "Users can read their direct messages realtime" on direct_messages;
create policy "Users can read their direct messages realtime" on direct_messages
  for select using (
    auth.uid() = sender_id or auth.uid() = receiver_id
  );

-- Política para insertar mensajes (ya debería existir)
drop policy if exists "Users can send direct messages" on direct_messages;
create policy "Users can send direct messages" on direct_messages
  for insert with check (
    auth.uid() = sender_id
  );

-- Política para typing indicators - SELECT
drop policy if exists "Users can read typing indicators" on typing_indicators;
create policy "Users can read typing indicators" on typing_indicators
  for select using (
    auth.uid() = user_id or auth.uid() = partner_id
  );

-- Política para typing indicators - INSERT
drop policy if exists "Users can insert typing indicators" on typing_indicators;
create policy "Users can insert typing indicators" on typing_indicators
  for insert with check (
    auth.uid() = user_id
  );

-- Política para typing indicators - UPDATE
drop policy if exists "Users can update typing indicators" on typing_indicators;
create policy "Users can update typing indicators" on typing_indicators
  for update using (
    auth.uid() = user_id
  );

-- Política para typing indicators - DELETE (para limpiar)
drop policy if exists "Users can delete typing indicators" on typing_indicators;
create policy "Users can delete typing indicators" on typing_indicators
  for delete using (
    auth.uid() = user_id
  );

-- Política para leer status de usuarios
drop policy if exists "Users can read user status" on users;
create policy "Users can read user status" on users
  for select using (true);

-- Política para actualizar propio status
drop policy if exists "Users can update their own status" on users;
create policy "Users can update their own status" on users
  for update using (
    auth.uid() = id
  );

-- 10. Habilitar Row Level Security en todas las tablas
alter table direct_messages enable row level security;
alter table typing_indicators enable row level security;
alter table users enable row level security;

-- 11. Configurar réplica para Realtime (IMPORTANTE)
-- Esto asegura que los cambios se propagan correctamente
alter table direct_messages replica identity full;
alter table typing_indicators replica identity full;
alter table users replica identity default;

-- ============================================
-- VERIFICACIÓN
-- ============================================
-- Ejecutar estas queries para verificar que todo está correcto:

-- Ver tablas con Realtime habilitado:
-- SELECT schemaname, tablename FROM pg_publication_tables WHERE pubname = 'supabase_realtime';

-- Ver políticas RLS:
-- SELECT tablename, policyname FROM pg_policies WHERE tablename IN ('direct_messages', 'typing_indicators', 'users');

-- ============================================
-- RESULTADO ESPERADO
-- ============================================
-- Al ejecutar este script, Realtime estará habilitado para:
-- ✅ direct_messages - Mensajes llegarán en tiempo real
-- ✅ typing_indicators - Indicador de "escribiendo..." en tiempo real  
-- ✅ users - Estado online/offline en tiempo real
-- ✅ Índices optimizados para queries rápidas
-- ✅ UPSERT habilitado para typing_indicators (merge-duplicates)
-- ✅ RLS habilitado para seguridad
-- ✅ Trigger para actualizar last_active_at automáticamente
-- ✅ Réplica configurada correctamente para Realtime
