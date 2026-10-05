-- Tarea: hardening de seguridad — RLS estaba desactivado en public.users y
-- public.password_reset_tokens, quedando expuestas por la API REST de Supabase (PostgREST).
-- Fecha: 2026-10-05
-- Idempotente: ENABLE ROW LEVEL SECURITY no falla si ya estaba activado.
-- No afecta a este microservicio: la conexión JDBC (DB_USERNAME) es el owner de las tablas,
-- y el owner de una tabla no queda sujeto a RLS salvo que se fuerce explícitamente
-- (ALTER TABLE ... FORCE ROW LEVEL SECURITY, que NO se usa aquí). Login y recuperación de
-- contraseña se verificaron funcionando tras activar RLS en estas dos tablas.

ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.password_reset_tokens ENABLE ROW LEVEL SECURITY;
