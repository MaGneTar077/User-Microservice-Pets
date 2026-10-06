-- Tarea: USER-02 / USER-04 (refresh tokens, ver CONTRATOS_COMPARTIDOS.md §1.4 y USER_SERVICE_CHANGES.md)
-- Fecha: 2026-10-05
-- Tabla de refresh tokens opacos (NO son JWT): se guardan solo como hash SHA-256, nunca el valor plano.
-- Vive en public (nunca auth.*). Idempotente: se puede correr más de una vez sin error.
-- NOTA: este script se aplicó en Supabase SIN el "ON DELETE CASCADE" de la línea de abajo
-- (quedó como FK simple). La corrección real se aplicó con 004_refresh_tokens_fk_cascade.sql.
-- Se deja el CASCADE aquí para que una ejecución nueva en otro ambiente ya nazca correcta.

CREATE TABLE IF NOT EXISTS public.refresh_tokens (
    id            UUID PRIMARY KEY,
    user_id       UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    token_hash    VARCHAR(64) NOT NULL UNIQUE,          -- SHA-256 (hex) del token opaco
    context       VARCHAR(20) NOT NULL,                 -- USER, VETERINARY (USER-03, aún no implementado)
    veterinary_id UUID,                                 -- solo si context = VETERINARY
    expires_at    TIMESTAMPTZ NOT NULL,
    revoked_at    TIMESTAMPTZ,
    replaced_by   UUID,                                 -- id del refresh que lo reemplazó en la rotación
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'refresh_tokens_context_check'
          AND conrelid = 'public.refresh_tokens'::regclass
    ) THEN
        ALTER TABLE public.refresh_tokens
            ADD CONSTRAINT refresh_tokens_context_check
            CHECK (context IN ('USER', 'VETERINARY'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON public.refresh_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at ON public.refresh_tokens (expires_at);

-- Toda tabla nueva en public se crea con RLS activado (ver db/scripts/README.md).
ALTER TABLE public.refresh_tokens ENABLE ROW LEVEL SECURITY;
