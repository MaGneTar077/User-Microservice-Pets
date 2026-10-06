# db/scripts

Scripts SQL manuales para Supabase (este proyecto usa `hibernate.ddl-auto: update` solo para columnas simples sin riesgo; cualquier cambio estructural relevante — columnas NOT NULL, constraints, tablas nuevas — se versiona aquí y se corre a mano).

Convención: `NNN_descripcion.sql`, en orden de aplicación. Cada script debe ser idempotente (se puede volver a correr sin romper nada) y llevar un comentario con la tarea y la fecha.

## Regla obligatoria: calificar siempre el esquema

Supabase tiene **dos tablas `users` en el mismo proyecto**: `auth.users` (de Supabase Auth, administrada por Supabase — **nunca se toca desde estos scripts ni desde el código**) y `public.users` (la tabla propia de este microservicio). Un `ALTER TABLE users` o un `SELECT ... FROM users` sin calificar puede ejecutarse contra la tabla equivocada según el `search_path` de la conexión.

Por eso, en todo script de esta carpeta:
- Usar siempre `public.users`, `public.password_reset_tokens`, etc. — nunca el nombre de tabla sin esquema.
- Al consultar catálogos (`pg_constraint`, `pg_class`, etc.), filtrar explícitamente por el esquema correcto (p. ej. `conrelid = 'public.users'::regclass`), no solo por nombre de constraint o de tabla.

El código Java (JPA/Hibernate) no fija ningún `schema` explícito en las entidades ni `hibernate.default_schema`/`currentSchema` en `application.yml`; depende del `search_path` por defecto de la conexión (`"$user", public` en Postgres/Supabase), que resuelve a `public.users`. Funciona hoy porque nunca hay una tabla con el mismo nombre en el esquema del rol de conexión, pero por eso es aún más importante que los scripts manuales sean explícitos: un error aquí sí podría tocar el esquema equivocado si algún día cambia esa configuración.

## Regla obligatoria: toda tabla nueva en `public` se crea con RLS activado

`public.users` y `public.password_reset_tokens` tenían Row Level Security **desactivado**, lo que las dejaba expuestas por la API REST de Supabase (PostgREST) sin ninguna restricción. Se corrigió en `003_enable_rls.sql`. La conexión JDBC de este servicio (`DB_USERNAME`) es el *owner* de las tablas y no queda sujeta a RLS salvo que se use `FORCE ROW LEVEL SECURITY` (no se usa), así que activar RLS no afecta al propio microservicio — solo cierra el acceso anónimo vía REST. Verificado: login y recuperación de contraseña siguen funcionando con RLS activado.

Por eso, de ahora en adelante: **todo `CREATE TABLE` nuevo en `public` debe incluir `ALTER TABLE ... ENABLE ROW LEVEL SECURITY` en el mismo script** (ver `002_refresh_tokens.sql` para el patrón).

## Scripts

| Script | Tarea | Aplicado en Supabase | Notas |
|---|---|---|---|
| `001_platform_role.sql` | USER-02 | 2026-10-05 | Verificado con `SELECT` sobre `public.users`: todos los usuarios quedaron en `USER`; el usuario del equipo se marcó manualmente como `PLATFORM_ADMIN` con el `UPDATE` de ejemplo. |
| `002_refresh_tokens.sql` | USER-02 / USER-04 | 2026-10-05 | Crea `public.refresh_tokens` con RLS activado desde su creación. ⚠️ Se aplicó **sin** `ON DELETE CASCADE` en la FK `user_id` (quedó como FK simple) — corregido por `004_refresh_tokens_fk_cascade.sql`. |
| `003_enable_rls.sql` | Hardening de seguridad | 2026-10-05 | Activa RLS en `public.users` y `public.password_reset_tokens`, que estaban expuestas sin restricción por la API REST de Supabase. Verificado: login y recuperación de contraseña siguen funcionando. |
| `004_refresh_tokens_fk_cascade.sql` | Corrección de `002` | 2026-10-05 | Recrea la FK `refresh_tokens_user_id_fkey` con `ON DELETE CASCADE` (`DROP CONSTRAINT IF EXISTS` + `ADD CONSTRAINT`). Verificado en Supabase. |
