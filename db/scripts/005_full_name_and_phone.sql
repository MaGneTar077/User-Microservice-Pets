-- Tarea: separar el nombre completo del username y agregar teléfono (sin romper clientes actuales).
-- Fecha: 2026-11-26
-- Agrega full_name y phone a public.users. El username no se toca (sigue sin espacios y único).
-- Idempotente: se puede correr más de una vez sin error.
-- IMPORTANTE: Supabase también tiene auth.users (de Supabase Auth). Esta migración NUNCA toca ese esquema,
-- solo public.users (la tabla propia de este microservicio).

ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS full_name VARCHAR(100) NULL;

ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS phone VARCHAR(20) NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'users_phone_e164_check'
          AND conrelid = 'public.users'::regclass
    ) THEN
        ALTER TABLE public.users
            ADD CONSTRAINT users_phone_e164_check
            CHECK (phone IS NULL OR phone ~ '^\+[1-9][0-9]{7,14}$');
    END IF;
END $$;

-- Sin índice único: full_name y phone explícitamente NO son únicos.

-- Verificación (correr después de aplicar):
-- SELECT column_name, data_type, character_maximum_length, is_nullable
-- FROM information_schema.columns
-- WHERE table_schema = 'public' AND table_name = 'users' AND column_name IN ('full_name', 'phone');
--
-- SELECT conname FROM pg_constraint
-- WHERE conrelid = 'public.users'::regclass AND conname = 'users_phone_e164_check';
