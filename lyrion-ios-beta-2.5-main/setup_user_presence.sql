-- ============================================
-- SETUP USER PRESENCE SYSTEM
-- Script para implementar sistema de presencia/heartbeat
-- ============================================
-- Ejecutar este script en la consola SQL de Supabase

-- 1. Función que marca usuarios como inactivos si no han tenido actividad en 3 minutos
CREATE OR REPLACE FUNCTION mark_inactive_users()
RETURNS void AS $$
DECLARE
  affected_count INTEGER;
BEGIN
  UPDATE public.users
  SET is_active = false, status = 'offline'
  WHERE is_active = true
    AND last_active_at < NOW() - INTERVAL '3 minutes';
  
  GET DIAGNOSTICS affected_count = ROW_COUNT;
  
  IF affected_count > 0 THEN
    RAISE NOTICE 'Marked % users as inactive', affected_count;
  END IF;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 2. Comentario sobre la función
COMMENT ON FUNCTION mark_inactive_users() IS 'Marca usuarios como inactivos si no han tenido actividad en los últimos 3 minutos';

-- 3. Grant para que pueda ser llamada por el servicio
GRANT EXECUTE ON FUNCTION mark_inactive_users() TO service_role;

-- ============================================
-- OPCIÓN A: Si tienes pg_cron habilitado (Supabase Pro)
-- ============================================
-- Descomenta las siguientes líneas si tienes pg_cron:

-- SELECT cron.schedule(
--   'mark-inactive-users',          -- nombre del job
--   '*/3 * * * *',                  -- cada 3 minutos
--   'SELECT mark_inactive_users()'  -- comando a ejecutar
-- );

-- Para verificar los jobs programados:
-- SELECT * FROM cron.job;

-- Para eliminar el job:
-- SELECT cron.unschedule('mark-inactive-users');

-- ============================================
-- OPCIÓN B: Si NO tienes pg_cron (Supabase Free)
-- ============================================
-- Puedes llamar la función manualmente o configurar 
-- un cron externo (ej: GitHub Actions, Vercel Cron, etc.)
-- que haga una llamada HTTP a tu Edge Function
-- O simplemente ejecutar periódicamente desde tu app

-- Ejemplo de Edge Function (deploy en Supabase Functions):
-- 
-- import { createClient } from 'https://esm.sh/@supabase/supabase-js'
-- 
-- Deno.serve(async () => {
--   const supabase = createClient(
--     Deno.env.get('SUPABASE_URL')!,
--     Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!
--   )
--   await supabase.rpc('mark_inactive_users')
--   return new Response('OK')
-- })

-- ============================================
-- VERIFICACIÓN
-- ============================================
-- Para probar que la función funciona:

-- 1. Ver usuarios activos actuales:
-- SELECT id, username, is_active, last_active_at FROM public.users WHERE is_active = true;

-- 2. Ejecutar la función manualmente:
-- SELECT mark_inactive_users();

-- 3. Verificar que usuarios inactivos fueron marcados:
-- SELECT id, username, is_active, last_active_at FROM public.users ORDER BY last_active_at DESC;
