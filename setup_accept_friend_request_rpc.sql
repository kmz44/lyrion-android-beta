-- Función RPC para aceptar solicitudes de amistad bidireccionalmente
-- Esta función:
-- 1. Inserta la relación A -> B en friends
-- 2. Inserta la relación B -> A en friends
-- 3. Borra la solicitud de amistad
-- Se ejecuta con SECURITY DEFINER para tener permisos de insertar filas para ambos usuarios.

CREATE OR REPLACE FUNCTION public.accept_friend_request(request_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  v_sender_id uuid;
  v_receiver_id uuid;
BEGIN
  -- Obtener IDs de la solicitud
  SELECT sender_id, receiver_id INTO v_sender_id, v_receiver_id
  FROM public.friend_requests
  WHERE id = request_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Request not found';
  END IF;

  -- Verificar que el usuario que ejecuta es el receptor
  IF auth.uid() <> v_receiver_id THEN
    RAISE EXCEPTION 'Not authorized to accept this request';
  END IF;

  -- Insertar conexiones (ignorar si ya existen para evitar errores duplicados)
  INSERT INTO public.friends (user_id, friend_id, status)
  VALUES (v_sender_id, v_receiver_id, 'active')
  ON CONFLICT DO NOTHING;

  INSERT INTO public.friends (user_id, friend_id, status)
  VALUES (v_receiver_id, v_sender_id, 'active')
  ON CONFLICT DO NOTHING;

  -- Borrar solicitud
  DELETE FROM public.friend_requests
  WHERE id = request_id;
END;
$$;

-- Grant execute permissions
GRANT EXECUTE ON FUNCTION public.accept_friend_request(uuid) TO authenticated;
