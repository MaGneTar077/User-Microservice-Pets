-- Tarea: corrige 002_refresh_tokens.sql — ese script se aplico en Supabase SIN
-- ON DELETE CASCADE en la FK user_id (quedo como FK simple, sin ON DELETE).
-- Fecha: 2026-10-05
-- Idempotente: DROP CONSTRAINT IF EXISTS no falla si la FK ya fue corregida o no existe.

ALTER TABLE public.refresh_tokens DROP CONSTRAINT IF EXISTS refresh_tokens_user_id_fkey;

ALTER TABLE public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;
