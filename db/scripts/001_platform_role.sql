-- Tarea: USER-02 (migración JWT compartido MyAnimaLog, ver CONTRATOS_COMPARTIDOS.md §1.2 y USER_SERVICE_CHANGES.md)
-- Fecha: 2026-10-05
-- Agrega la columna platform_role a public.users, requerida para el claim "platform_role" del JWT
-- (USER por defecto; PLATFORM_ADMIN = equipo MyAnimaLog). Idempotente: se puede correr más de una vez sin error.
-- IMPORTANTE: Supabase también tiene auth.users (de Supabase Auth). Esta migración NUNCA toca ese esquema,
-- solo public.users (la tabla propia de este microservicio). Todas las referencias abajo califican el esquema.

ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS platform_role VARCHAR(20) NOT NULL DEFAULT 'USER';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'users_platform_role_check'
          AND conrelid = 'public.users'::regclass
    ) THEN
        ALTER TABLE public.users
            ADD CONSTRAINT users_platform_role_check
            CHECK (platform_role IN ('USER', 'PLATFORM_ADMIN'));
    END IF;
END $$;

-- Ejemplo (comentado): marcar un usuario existente como PLATFORM_ADMIN.
-- No hay endpoint para auto-asignarse este rol; se hace a mano en Supabase.
-- UPDATE public.users SET platform_role = 'PLATFORM_ADMIN' WHERE email = 'admin@myanimalog.com';
