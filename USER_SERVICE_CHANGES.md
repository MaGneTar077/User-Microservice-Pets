# Cambios en user-service para MyAnimaLogVet

> Leer junto con `CONTRATOS_COMPARTIDOS.md`. Este servicio es el **único emisor de tokens**.
> No se tuvo acceso al `CLAUDE.md` de este repo: los ejemplos asumen Spring Boot 3 / Java 17 como Pets y Veterinary. Si el stack es otro, mantener el contrato y adaptar la implementación.
> Es el primer repo que se toca: **todo lo demás depende de USER-01 y USER-02.**

---

## USER-01 — Firma RS256 y JWKS

1. Generar un par de llaves RSA 2048 por ambiente. Guardar la privada como secreto (`JWT_PRIVATE_KEY_PEM`, en Secret Manager en Cloud Run; en local, archivo fuera del repo). La pública se deriva.
2. Asignar un `kid` (ej. `2026-10-key-1`).
3. Exponer `GET /.well-known/jwks.json` público, con `Cache-Control: public, max-age=600`:
   ```json
   { "keys": [ { "kty": "RSA", "kid": "2026-10-key-1", "use": "sig", "alg": "RS256", "n": "…", "e": "AQAB" } ] }
   ```
   Con Spring: `com.nimbusds:nimbus-jose-jwt` → `new JWKSet(rsaKey.toPublicJWK()).toJSONObject()`.
4. Firmar todos los tokens con RS256 y header `kid`.
5. Rotación: el JWKS puede publicar dos llaves a la vez (la vieja hasta que venzan sus tokens).

*Listo cuando:* un token emitido se valida en jwt.io pegando la llave pública, y el endpoint JWKS responde sin autenticación.

## USER-02 — Claims del token base y refresh

1. Agregar columna `platform_role VARCHAR(20) NOT NULL DEFAULT 'USER'` en la tabla de usuarios. Script para marcar a los admins del equipo como `PLATFORM_ADMIN` (no hay endpoint para auto-asignarse ese rol).
2. Token base con exactamente los claims de `CONTRATOS_COMPARTIDOS.md §1.2`, vida 15 min, `iss`, `aud`, `jti`.
3. Asegurar que existe verificación de email y que el claim `email_verified` refleja el estado real.
4. Refresh tokens:
   ```sql
   CREATE TABLE refresh_tokens (
     id UUID PRIMARY KEY,
     user_id UUID NOT NULL,
     token_hash VARCHAR(64) NOT NULL UNIQUE,     -- SHA-256 del token opaco
     context VARCHAR(20) NOT NULL,              -- USER, VETERINARY
     veterinary_id UUID,                        -- solo si context = VETERINARY
     expires_at TIMESTAMPTZ NOT NULL,
     revoked_at TIMESTAMPTZ,
     replaced_by UUID,
     created_at TIMESTAMPTZ NOT NULL DEFAULT now()
   );
   ```
5. `POST /auth/refresh` con rotación: el refresh usado se revoca y se emite uno nuevo. Si llega un refresh ya revocado (reutilización) → revocar toda la cadena de ese usuario (posible robo).
6. `POST /auth/logout` revoca el refresh actual.

*Listo cuando:* login devuelve `{ accessToken, refreshToken, expiresIn: 900 }` y refresh rota correctamente.

## USER-03 — Token de contexto de clínica *(requiere VET-11)*

`POST /auth/context/veterinary/{veterinaryId}` — requiere token base válido.

1. Llamar `GET {VETERINARY_SERVICE_URL}/internal/veterinaries/{veterinaryId}/members/{sub}` con `X-Internal-Api-Key`.
2. Si `member = false` o `active = false` → `403`.
3. Si `veterinaryStatus` es `REJECTED` → `403`. Cualquier otro estado sí recibe token (`PENDING_DOCUMENTS`, `UNDER_REVIEW`, `NEEDS_CORRECTION` y `SUSPENDED` lo necesitan para ver su estado y corregir; los permisos los limita Veterinary).
4. Emitir access token con los claims de `CONTRATOS_COMPARTIDOS.md §1.3` y un refresh con `context = VETERINARY`, `veterinary_id`.
5. Si Veterinary no responde → `503` (nunca emitir token sin confirmar).

Respuesta: `{ accessToken, refreshToken, expiresIn, veterinary: { id, role, status } }`.

## USER-04 — Refresh de contexto de clínica

En `POST /auth/refresh`, si el refresh es `context = VETERINARY`, repetir los pasos 1–3 de USER-03 antes de emitir. Si ya no es miembro activo → revocar el refresh y responder `401` con código `VETERINARY_MEMBERSHIP_REVOKED` (la web debe mandar al selector de clínica).

## USER-05 — Cambiar de clínica / volver a modo usuario

- Cambiar de clínica = llamar de nuevo USER-03 con otro `veterinaryId` (revoca el refresh de contexto anterior).
- `POST /auth/context/user` devuelve un token base nuevo (para la app móvil o para salir del modo clínica).

## USER-06 — Endpoints internos de usuarios *(requerido por VET-20)*

Todos bajo `/internal/**` con `X-Internal-Api-Key`.

| Método | Ruta | Respuesta |
|---|---|---|
| GET | `/internal/users/{id}` | `{ id, fullName, email, phone, emailVerified }` |
| POST | `/internal/users/batch` | body `{ ids: [] }` (máx. 100) → lista con los mismos campos |
| GET | `/internal/users/by-email?email=` | el usuario o `404` |

No exponer contraseñas, hashes, ni datos de autenticación.

## USER-07 — Proteger los endpoints propios con el nuevo JWT

1. Validar los propios tokens del servicio con el mismo mecanismo que los demás (resource server con JWKS local o el decoder directo).
2. Quitar cualquier `userId` que hoy venga en body/params para operaciones "sobre mí" (perfil, cambio de contraseña) y usar `sub`.
3. Rutas públicas: registro, login, refresh, verificación de email, recuperación de contraseña, JWKS.

## USER-08 — Eventos (opcional, recomendado)

Publicar `USER_EMAIL_VERIFIED` y `USER_REGISTERED` con el sobre común. Notifications puede usarlos para el correo de bienvenida.

---

## Tests mínimos

- Firma y verificación con la llave pública del JWKS.
- Token base trae todos los claims y `exp` de 15 min.
- Token de clínica: miembro activo → 200; no miembro → 403; clínica `REJECTED` → 403; Veterinary caído → 503.
- Refresh de clínica tras desactivar al empleado → 401 `VETERINARY_MEMBERSHIP_REVOKED`.
- Reutilización de refresh revocado → revoca la cadena.
- `/internal/**` sin API key → 401.
