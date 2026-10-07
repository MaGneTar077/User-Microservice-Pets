# CLAUDE.md — UserMicroservice (pets)

Este archivo documenta el microservicio de usuarios del backend de "Proyecto Mascotas" para que Claude Code (y cualquier desarrollador) entienda rápidamente qué hace, cómo está organizado y cómo funciona su seguridad.

## Resumen

Microservicio Spring Boot encargado de la gestión de usuarios: registro, login (local y con Google), verificación de email, recuperación de contraseña, logout con invalidación de tokens, perfil de usuario y foto de perfil. Publica eventos de autenticación a Google Pub/Sub para que otros microservicios (p. ej. notificaciones) reaccionen a ellos.

**Migración en curso (ver `CONTRATOS_COMPARTIDOS.md` y `USER_SERVICE_CHANGES.md`)**: este servicio se está convirtiendo en el **emisor único de JWT para todo el ecosistema MyAnimaLog** (Pets, Veterinary, Medical). Ya implementados: USER-01 (firma RS256 + JWKS), los claims de USER-02 (`platform_role`, `ctx`, `iss`, `aud`, `jti`, `email_verified`), los refresh tokens (USER-02/04), los endpoints internos (USER-06) y el token de contexto de clínica (USER-03/04/05). Ver la sección de deuda técnica al final para lo que sigue pendiente.

## Stack tecnológico

- **Java 17**, **Spring Boot 3.5.15**, Maven (con wrapper `mvnw`).
- **Spring Security** (stateless, sin sesiones) + **OAuth2 Client** (dependencia incluida, pero el login con Google se implementa verificando el ID Token directamente, no con el flujo `authorization_code` completo).
- **JWT** propio con `io.jsonwebtoken:jjwt` 0.11.5. Tokens nuevos firmados **RS256** (RSA-2048) vía `com.nimbusds:nimbus-jose-jwt` (solo para publicar el JWKS); tokens viejos HS256 se siguen aceptando temporalmente (ver sección JWT).
- **Spring Data JPA** + **PostgreSQL** (Supabase, vía pooler/PgBouncer).
- **Redis** (`spring-boot-starter-data-redis`) para blacklist de JWT (logout) y códigos/cooldowns de verificación de email.
- **Spring Mail** para correos transaccionales (verificación de email, reset de password).
- **Google API Client** para verificar ID Tokens de Google OAuth2.
- **Google Cloud Pub/Sub** (`spring-cloud-gcp-starter-pubsub`) para publicar eventos de dominio.
- **Supabase Storage** (vía `RestTemplate`) para las fotos de perfil.
- **springdoc-openapi** (Swagger UI).
- **Lombok**, **Bean Validation (Hibernate Validator)**.
- Tests: JUnit 5, Mockito, AssertJ, `spring-security-test`.

## Arquitectura

El proyecto sigue **arquitectura hexagonal (ports & adapters)** dentro de un único módulo:

```
user.microservice.pets/
├── domain/                 → núcleo de negocio, sin dependencias de frameworks
│   ├── model/               User, PasswordResetToken, RefreshToken, VeterinaryMembership (POJOs puros)
│   ├── enums/                AuthProvider (LOCAL, GOOGLE), PlatformRole (USER, PLATFORM_ADMIN), TokenContext (USER, VETERINARY)
│   ├── policies/             PasswordPolicy (reglas de contraseña)
│   ├── exceptions/           excepciones de negocio (RuntimeException)
│   └── ports/
│       ├── in/                interfaces de casos de uso (RegisterUserUseCase, LocalAuthUseCase, GoogleAuthUseCase, GetProfileUseCase, UpdateUserProfileUseCase, Upload/DeleteProfileImageUseCase, RequestPasswordResetUseCase, ResetPasswordUseCase, PublishAuthEventUseCase)
│       └── out/               interfaces hacia infraestructura (UserRepositoryPort, PasswordResetTokenRepositoryPort, RefreshTokenRepositoryPort, VeterinaryMembershipPort, EmailSenderPort, EventPublisherPort, StoragePort)
├── application/
│   ├── dto/                 DTOs de request/response
│   ├── services/             orquestación (EmailVerificationService, LogoutService, RefreshTokenService, GoogleTokenVerifierService, FileValidationService, ProfileImageService, PublishAuthEventService, RegisterService, RequestPasswordResetService, ResetPasswordService)
│   └── usecases/             implementaciones de los ports "in" (*UseCaseImpl)
└── infrastructure/
    ├── controllers/          9 @RestController (incluye JwksController, InternalUserController)
    ├── security/              JwtUtil, JwtAuthenticationFilter, JwtKeyProvider, JwtSigningKeyResolver, InternalApiKeyFilter
    ├── config/                SecurityConfig, BeansConfig, RestTemplateConfig, VeterinaryClientConfig, GlobalExceptionHandler
    ├── entity/                UserEntity, PasswordResetTokenEntity, RefreshTokenEntity (JPA)
    ├── repositories/          JpaUserRepository, JpaPasswordResetTokenRepository, JpaRefreshTokenRepository
    ├── adapters/              UserRepositoryAdapter, PasswordResetTokenRepositoryAdapter, RefreshTokenRepositoryAdapter, VeterinaryMembershipAdapter, SupabaseStorageAdapter, GooglePubSubAuthEventAdapter
    ├── email/                 EmailServiceAdapter (JavaMailSender)
    └── jobs/                  TokenCleanupJob (limpieza de tokens de reset expirados)
```

El dominio (`domain/model/User.java`) es un POJO sin anotaciones JPA; la entidad real (`infrastructure/entity/UserEntity.java`) vive en infraestructura y se traduce mediante adapters. Esto mantiene el núcleo de negocio independiente del framework.

## Modelo de datos

### `users` (`UserEntity`)
| Campo | Tipo | Notas |
|---|---|---|
| `id` | UUID | generado en código, no autogenerado por la BD |
| `username` | String | |
| `email` | String | único, not null |
| `password` | String | nullable (cuentas de Google no tienen password) |
| `profileImageUrl` | String | URL en Supabase Storage |
| `emailVerified` | boolean | false hasta verificar (o true automático con Google) |
| `authProvider` | enum (`LOCAL`, `GOOGLE`) | |
| `createdAt` | LocalDateTime | |
| `platformRole` | enum (`USER`, `PLATFORM_ADMIN`) | `platform_role`, `NOT NULL DEFAULT 'USER'`, `CHECK` en BD (ver `db/scripts/001_platform_role.sql`). `USER` para todos los registros/login nuevos; `PLATFORM_ADMIN` solo se asigna a mano en Supabase (no hay endpoint para auto-asignarlo). |

### `password_reset_tokens` (`PasswordResetTokenEntity`)
`id`, `email`, `token` (único), `expiresAt`, `createdAt`, `used`. Índices en `token`, `email`, `expires_at`.

### `refresh_tokens` (`RefreshTokenEntity`, USER-02/04)
`id`, `user_id` (FK a `users.id`, `ON DELETE CASCADE`), `token_hash` (SHA-256 hex, único — **nunca se guarda el token en claro**), `context` (`USER`/`VETERINARY`, `CHECK` en BD), `veterinary_id` (nullable, solo para `VETERINARY`), `expires_at`, `revoked_at`, `replaced_by` (id del token que lo reemplazó al rotar), `created_at`. Ver `db/scripts/002_refresh_tokens.sql`.

No existe tabla/entidad de roles: **no hay RBAC**. El control de acceso es por "ownership" (un usuario solo puede ver/editar su propio recurso).

`hibernate.ddl-auto: update`, dialecto PostgreSQL, conectado a Supabase con `prepareThreshold=0` (necesario por el pooler PgBouncer).

## Autenticación y seguridad (detalle)

### Registro local — `POST /user/register`
`RegisterController` → `RegisterService` → `RegisterUserUseCaseImpl`:
1. Normaliza email (trim/lowercase) y username.
2. Valida: username 3-50 chars `[a-zA-Z0-9_]`; email con regex; password con `PasswordPolicy` (8-128 chars, sin espacios, requiere mayúscula, minúscula, dígito y símbolo).
3. Rechaza si el email o username ya existen (`UserAlreadyExistsException` → 409).
4. Hashea el password con `BCryptPasswordEncoder`.
5. Genera `UUID`, guarda el usuario con `emailVerified=false`, dispara el envío del código de verificación de email.
6. Publica evento `USER_REGISTERED` a Pub/Sub.

### Login local — `POST /auth/local`
`LocalAuthUseCaseImpl`:
- Busca por email, exige `authProvider==LOCAL`, compara password con `BCryptPasswordEncoder.matches()`.
- Si las credenciales no coinciden: `InvalidCredentialsException` (401, mensaje genérico — no revela si el email existe).
- Si el usuario existe pero no verificó su email: `EmailNotVerifiedException` (403, `code: EMAIL_NOT_VERIFIED`).
- Si es correcto, `AuthController` emite el JWT, genera un refresh token y publica `USER_LOGIN`.

### JWT — firma RS256 + JWKS (USER-01)
- `JwtKeyProvider` (`infrastructure/security/JwtKeyProvider.java`) carga el par RSA-2048 usado para firmar:
  - Lee `JWT_PRIVATE_KEY_PEM` (PEM PKCS8; la llave pública se deriva de los parámetros CRT de la privada, no se configura por separado).
  - Si no está configurada y el perfil activo es `dev` **o no hay ningún perfil activo** (que es como corre hoy este servicio, ya que no usa perfiles Spring), genera un par RSA-2048 una sola vez y lo persiste en `.local-keys/jwt-dev-private-key.pem` (fuera de Git, en `.gitignore`) para que sobreviva reinicios.
  - Si hay un perfil activo distinto de `dev` (p. ej. `prod`) y falta `JWT_PRIVATE_KEY_PEM`, **la app no arranca** (`IllegalStateException` con mensaje explícito). ⚠️ Importante: como hoy nada fija `SPRING_PROFILES_ACTIVE`, este gate de producción solo se activa el día que un despliegue real fije un perfil explícito — hasta entonces, sin esa variable de entorno, se generaría una llave de desarrollo efímera. Hay que fijar `SPRING_PROFILES_ACTIVE=prod` (o similar) en cualquier entorno real además de `JWT_PRIVATE_KEY_PEM`.
  - `kid` configurable con `JWT_KEY_ID` (default `dev-key-1`).
- `GET /.well-known/jwks.json` (`JwksController`, público, sin JWT): publica **solo la llave pública** en formato JWK (`kty=RSA`, `use=sig`, `alg=RS256`, `kid`, `n`, `e`) con `Cache-Control: public, max-age=600`.
- `JwtUtil` (`infrastructure/security/JwtUtil.java`):
  - Firma todos los tokens **nuevos** con **RS256** + header `kid`.
  - Expiración configurable con `JWT_ACCESS_TTL_MINUTES` (default **60**, la misma duración que ya existía).
  - `iss` (`JWT_ISSUER`, default `myanimalog-user-service`), `aud` (`JWT_AUDIENCE`, default `myanimalog-api`) y `jti` (UUID aleatorio, uno por token) se agregan centralmente en `generateToken()` — no dependen de que cada caller los recuerde.
  - `sub` = **UUID del usuario** (antes era el email). Se conservan igual que antes los claims `id`, `email`, `username`, `provider`.
  - **Validación dual (USER-01/03)**: `JwtSigningKeyResolver` decide la llave según el token — si trae header `kid` usa la llave pública RSA (token nuevo); si no trae `kid` usa la llave HMAC legada (`JWT_SECRET`), **solo si `JWT_ACCEPT_LEGACY=true`** (default `true`). Esto permite que los tokens HS256 emitidos antes de esta migración sigan siendo válidos hasta que expiren (máx. 1h), sin romper sesiones activas. TODO: quitar la rama legada (y `JWT_ACCEPT_LEGACY`) pasada esa ventana.
  - `JwtAuthenticationFilter` identifica al usuario autenticado por el claim **`id`** (presente en tokens viejos y nuevos), no por `sub` — así ambos tipos de token resuelven al mismo principal. `GetProfileController`, `UpdateProfileController` y `ProfileImageController` resuelven el usuario autenticado con `userRepositoryPort.findById(...)` sobre ese `id`.
  - **Validación de `iss`/`aud` (solo tokens nuevos)**: `validateToken()` exige que los tokens con `kid` (RS256, nuevos) tengan `iss == JWT_ISSUER` y `aud` conteniendo `JWT_AUDIENCE`; si no, se rechazan (`JwtException`). Los tokens legados HS256 no tienen esos claims, así que esta verificación no se les aplica (solo se valida su firma/expiración).
- **Claims del contrato ya emitidos (USER-02)**, agregados en `AuthController.issueToken()` manteniendo los de siempre (`id`, `email`, `username`, `provider`) con los mismos nombres y valores:
  - `email_verified`: sale de `user.isEmailVerified()` (columna `users.email_verified`, ya existente).
  - `platform_role`: sale de `user.getPlatformRole()` (columna nueva `users.platform_role`, ver `db/scripts/001_platform_role.sql`). Todo usuario nuevo (registro local o alta automática por Google) se crea con `USER`.
  - `ctx`: `"USER"` en el token base; `"VETERINARY"` en el token de contexto de clínica (ver sección siguiente).
### Refresh tokens (USER-02/04)
`RefreshTokenService` (`application/services/RefreshTokenService.java`), tabla `refresh_tokens` (ver arriba):
- **Login** (`/auth/local`, `/auth/google`): además del access token de siempre, `AuthController` genera un refresh token opaco (32 bytes aleatorios, Base64 URL-safe) vía `RefreshTokenService.issue()`, y responde `{"token": "...", "refreshToken": "...", "expiresIn": <segundos>}` — los campos `token`/mensaje existentes **no cambiaron de nombre ni de significado**, solo se agregaron `refreshToken` y `expiresIn` (= `JWT_ACCESS_TTL_MINUTES * 60`).
- **`POST /auth/refresh`** (público, body `{"refreshToken": "..."}`): `RefreshTokenService.rotate()` —
  - Busca el token por el hash SHA-256 del valor recibido (nunca se guarda ni se busca por el valor plano).
  - Si no existe → 401 (`InvalidTokenException`).
  - Si ya estaba revocado (reutilización de un refresh ya usado — posible robo) → **revoca toda la cadena activa del usuario** (`revokeAllActiveForUser`) y responde 401.
  - Si expiró (30 días desde que se emitió) → 401.
  - Si es válido: lo marca revocado (`revokedAt`, `replacedBy` = id del nuevo), emite un refresh nuevo y un access token nuevo (mismos claims que un login, pero **sin** publicar `USER_LOGIN` — un refresh no es un login nuevo), y responde con el mismo formato `{"token", "refreshToken", "expiresIn"}`.
- **`POST /auth/logout`**: ahora acepta opcionalmente un body `{"refreshToken": "..."}` (`LogoutRequest`); si viene, también revoca ese refresh (best-effort: si no existe o ya estaba revocado, no falla el logout). Sin body, el logout funciona exactamente igual que antes (solo blacklist del access token).
- El campo `context` es `USER` o `VETERINARY` (ver sección siguiente para este último).
- **Pendiente**: no hay un job de limpieza para refresh tokens expirados/revocados (a diferencia de `TokenCleanupJob` para `password_reset_tokens`).

### Token de contexto de clínica (USER-03/04/05)
Permite a un usuario que es empleado de una clínica Veterinary obtener un JWT con permisos/identidad de esa clínica, sin perder su cuenta de usuario normal. Veterinary es la única fuente de verdad sobre membresías (`VeterinaryMembershipPort` → `VeterinaryMembershipAdapter`, `RestClient` con timeout de 3s, header `X-Internal-Api-Key`, hacia `GET {VETERINARY_SERVICE_URL}/internal/veterinaries/{veterinaryId}/members/{userId}`).

- **`POST /auth/context/veterinary/{veterinaryId}`** (requiere JWT de usuario válido — `ctx` puede ser `USER` o `VETERINARY`, cualquier token no revocado sirve; body opcional `{"refreshToken": "..."}` con el refresh del contexto anterior, que se revoca si viene):
  1. Consulta la membresía con el `id` del usuario del token (principal).
  2. `member=false` o `active=false` → 403 `NOT_A_VETERINARY_MEMBER` (`NotVeterinaryMemberException`).
  3. `veterinaryStatus=REJECTED` → 403 `VETERINARY_REJECTED` (`VeterinaryRejectedException`). Cualquier otro estado (`ACTIVE`, `SUSPENDED`, `PENDING_DOCUMENTS`, etc.) sí recibe el token — los permisos reales los limita Veterinary, no este servicio.
  4. Veterinary caído/timeout/5xx → 503 (`VeterinaryServiceUnavailableException`), **nunca se emite un token sin confirmar la membresía**.
  5. Emite un access token con **todos** los claims del token base (`id`, `email`, `username`, `provider`, `platform_role`, `email_verified`) más `ctx="VETERINARY"`, `vet_id`, `vet_role`, `employee_id`, `vet_licensed`, `vet_status`.
  6. Emite un refresh token nuevo con `context=VETERINARY` y `veterinary_id`.
  7. Responde `{ token, refreshToken, expiresIn, veterinary: { id, role, status } }`.
- **`POST /auth/context/user`** (mismo requisito de autenticación; mismo body opcional para revocar el refresh de clínica anterior): emite un token normal (`ctx=USER`) y un refresh `context=USER`. Responde `{ token, refreshToken, expiresIn }` (sin el campo `veterinary`).
- **`POST /auth/refresh` con un refresh `context=VETERINARY`** (USER-04): antes de rotar, vuelve a consultar la membresía actual (el rol/estado puede haber cambiado desde que se emitió el token):
  - Sigue siendo miembro activo → rota normal y el access token nuevo trae el **rol y estado actuales** (no los que tenía el token viejo).
  - Ya no es miembro activo → **revoca ese refresh** (sin rotarlo) y responde 401 `VETERINARY_MEMBERSHIP_REVOKED` (`VeterinaryMembershipRevokedException`).
  - Veterinary caído → 503, sin revocar ni rotar nada.
  - Un refresh `context=USER` sigue funcionando exactamente igual que antes y **nunca** consulta a Veterinary.
- **Seguridad de `/auth/context/**`**: a diferencia del resto de `/auth/**` (público), estas rutas SÍ exigen autenticación — `JwtAuthenticationFilter.shouldNotFilter()` las excluye explícitamente de la lista de rutas públicas, y `SecurityConfig` tiene una regla `.requestMatchers("/auth/context/**").authenticated()` antes de la regla `permitAll` de `/auth/**` (en Spring Security gana la primera coincidencia).
- `role`/`veterinaryStatus` se modelan como `String` (no enum) en `VeterinaryMembership`: Veterinary puede agregar valores nuevos (`OWNER`, `RECEPTIONIST`, `PENDING_DOCUMENTS`, etc.) sin coordinar con este servicio.

### Login con Google — `POST /auth/google`
No usa el flujo redirect de Spring OAuth2 Client; implementa **verificación directa del ID Token** (patrón típico de "Sign in with Google" desde frontend/móvil):
- `GoogleTokenVerifierService` valida firma y expiración del idToken con `GoogleIdTokenVerifier` (audiencia = `client-id` configurado), exige `email_verified=true` en el payload de Google.
- `GoogleAuthUseCaseImpl`:
  - Si ya existe un usuario **local no verificado** con ese email, Google "reclama" la cuenta: borra el password local, cambia `authProvider` a `GOOGLE`, marca `emailVerified=true` (evita cuentas duplicadas/huérfanas).
  - Si existe y ya está verificado, lo retorna sin modificar.
  - Si no existe, crea un usuario nuevo con `authProvider=GOOGLE` y `emailVerified=true` automático (se confía en la verificación de Google), generando un username único a partir del nombre o del email.
- Tras autenticar, se emite el **mismo tipo de JWT propio** que en login local (no se usan los tokens de Google para llamadas posteriores a la API).

### Verificación de email
`EmailVerificationService`, basado en Redis:
- `email:verify:code:{email}` → código de 6 dígitos, TTL 15 min.
- `email:verify:cooldown:{email}` → bloqueo de reenvío, TTL 60s.
- `email:verify:attempts:{email}` → máximo 5 intentos fallidos antes de invalidar el código.
- `POST /auth/resend-verification`: reenvía respetando el cooldown; solo si el usuario es `LOCAL` y no verificado; responde siempre el mismo mensaje genérico (anti user-enumeration).
- `POST /auth/verify-email`: compara el código con `MessageDigest.isEqual` (tiempo constante, evita timing attacks), marca `emailVerified=true` y limpia las claves de Redis.

### Logout con Redis (blacklist de JWT)
`LogoutService`:
- `POST /auth/logout` (requiere header `Authorization: Bearer <token>`): valida el JWT, calcula el TTL restante (`exp - now`) y lo inserta en Redis como `jwt:blacklist:{token}` con ese TTL exacto usando `setIfAbsent` (evita doble logout / condiciones de carrera; la clave expira sola cuando el JWT habría expirado de todas formas).
- Publica evento `USER_LOGOUT`.
- **`JwtAuthenticationFilter`** consulta `isTokenInvalid(token)` en cada request autenticado; si está en blacklist, responde 401 aunque la firma/expiración sean válidas.
- **Fail-closed**: si Redis falla al consultar la blacklist, el filtro deniega el acceso por defecto (no se asume válido un token que no se pudo verificar).

### Configuración de Spring Security (`SecurityConfig`)
- CSRF deshabilitado (API stateless sin cookies).
- Rutas públicas (`permitAll`): `/auth/**`, `/user/register`, `/api/**` (sin controllers actuales bajo esa ruta — confirmado, es una regla sin uso real hoy), `/actuator/**`, `/error`, `/.well-known/jwks.json`, `/internal/**` (su seguridad real la impone `InternalApiKeyFilter`, no Spring Security — ver sección de endpoints internos).
- Todo lo demás requiere autenticación (`anyRequest().authenticated()`), incluyendo `/user/profile/{id}`, `/user/{id}`, `/user/profile/image`.
- `SessionCreationPolicy.STATELESS`.
- `JwtAuthenticationFilter` e `InternalApiKeyFilter` insertados antes de `UsernamePasswordAuthenticationFilter`.
- No se usa `AuthenticationManager`/`UserDetailsService` tradicional: el filtro setea manualmente un `UsernamePasswordAuthenticationToken(id, null, emptyAuthorities)` en el `SecurityContextHolder` (el principal es el `id` del usuario, no el email — ver sección JWT).
- **⚠️ Sin CORS configurado**: una `CorsConfig` previa fue eliminada en el commit `22a9993` ("remove duplicate CORS config causing preflight failures on /auth/google") y no fue reemplazada. Si el frontend corre en otro origen, las llamadas cross-origin pueden fallar en el preflight salvo que se gestione en otra capa (API Gateway/proxy) o se reintroduzca configuración CORS explícita.

### Roles y permisos
**No hay RBAC** (no hay `@PreAuthorize`, `@Secured`, entidad `Role` ni `GrantedAuthority` real — las authorities del token de seguridad están vacías). El control de acceso es de tipo **ownership**: los controllers de perfil comparan manualmente que el `id` autenticado (principal, extraído del claim `id`) coincida con el `{id}` del path, lanzando `UnauthorizedAccessException` (403) si no coincide. El JWT ya lleva `platform_role` (`USER`/`PLATFORM_ADMIN`) y, en tokens de contexto de clínica, `vet_role`/`vet_licensed`/`vet_status` — pero este servicio no los usa todavía para ninguna decisión de autorización propia; son para que los consuman otros servicios (p. ej. Veterinary).

### Recuperación de contraseña
- `POST /auth/request-password-reset`: responde siempre el mismo mensaje genérico exista o no la cuenta (anti enumeration). Si el usuario es de Google, solo envía un correo informativo (no genera token). Si es local, borra tokens previos, genera un `UUID` válido 15 minutos, y envía el enlace (`{FRONTEND_URL}/reset-password?token=...`).
- `POST /auth/reset-password`: valida token (existente, no usado, no expirado), rechaza si la nueva contraseña es igual a la actual, actualiza el password (hasheado), marca `emailVerified=true` (usar el enlace del correo prueba propiedad del email), borra todos los tokens del usuario y envía correo de confirmación.
- `TokenCleanupJob` (`@Scheduled(cron="0 0 * * * *")`, cada hora) borra tokens de reset expirados.

### Encriptación
`BCryptPasswordEncoder` (bean en `BeansConfig`) para hashear y comparar contraseñas en registro, login y reset.

### Endpoints internos — `/internal/**` (USER-06)
Pensados para que otros servicios del ecosistema (p. ej. Veterinary) consulten datos básicos de usuario sin pasar por JWT:
- **No usan JWT de usuario ni `SecurityConfig`/ownership**: `/internal/**` está en `permitAll` a nivel de Spring Security, pero `InternalApiKeyFilter` (`infrastructure/security/InternalApiKeyFilter.java`) intercepta esas rutas antes y exige el header `X-Internal-Api-Key` igual a `INTERNAL_API_KEY`, comparado en **tiempo constante** (`MessageDigest.isEqual`). Sin la llave (o con la llave incorrecta) → 401, sin tocar la base de datos.
- `InternalUserController` (`/internal/users`):
  - `GET /{id}` → `{id, fullName, email, phone, emailVerified}` o 404 (`UserNotFoundException`) si no existe o el `id` no es un UUID válido.
  - `POST /batch` con body `{"ids": [...]}` (máx. 100 — si no, 400 `InvalidUserDataException`) → lista con el mismo formato; los ids que no existan simplemente no aparecen en la respuesta.
  - `GET /by-email?email=` → el usuario o 404.
- **`fullName = username` y `phone = null` por ahora**: la tabla `users` todavía no tiene columnas `full_name`/`phone` reales (ver deuda técnica). **Nunca** se devuelve `password`, hashes, ni ningún dato de autenticación.

## Endpoints REST

### `JwksController` — raíz
| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| GET | `/.well-known/jwks.json` | Publica la llave pública RSA activa (JWKS) para que otros servicios verifiquen los JWT | Pública |

### `AuthController` — `/auth`
| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| POST | `/auth/google` | Login/registro automático vía ID Token de Google. Responde `{token, refreshToken, expiresIn}` | Pública |
| POST | `/auth/local` | Login con email/password. Responde `{token, refreshToken, expiresIn}` | Pública |
| POST | `/auth/refresh` | Rota un refresh token y emite un access token nuevo (re-valida membresía si era de contexto clínica). Body `{refreshToken}` | Pública (valida el refresh token, no un JWT) |
| POST | `/auth/context/veterinary/{veterinaryId}` | Emite un token de contexto de clínica si el usuario es empleado activo. Body opcional `{refreshToken}` (revoca el contexto anterior) | **JWT requerido** |
| POST | `/auth/context/user` | Vuelve al token normal (`ctx=USER`). Body opcional `{refreshToken}` (revoca el refresh de clínica) | **JWT requerido** |
| POST | `/auth/logout` | Invalida el JWT actual (blacklist en Redis); si el body trae `{refreshToken}`, también lo revoca | Requiere `Authorization: Bearer` |

### `EmailVerificationController` — `/auth`
| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| POST | `/auth/verify-email` | Verifica código de 6 dígitos | Pública |
| POST | `/auth/resend-verification` | Reenvía código (con cooldown) | Pública |

### `PasswordResetController` — `/auth`
| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| POST | `/auth/request-password-reset` | Solicita enlace de reseteo | Pública |
| POST | `/auth/reset-password` | Aplica nueva contraseña con token | Pública |

### `RegisterController` — `/user`
| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| POST | `/user/register` | Registro de usuario local + envío de código de verificación | Pública |

### `GetProfileController` — `/user`
| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| GET | `/user/profile/{id}` | Obtiene el perfil (solo si `{id}` == usuario autenticado) | **JWT requerido** |

### `UpdateProfileController` — `/user`
| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| PUT | `/user/{id}` | Actualiza el `username` del propio usuario | **JWT requerido** |

### `ProfileImageController` — `/user/profile`
| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| POST | `/user/profile/image` | Sube/reemplaza foto de perfil (multipart) | **JWT requerido** |
| DELETE | `/user/profile/image` | Elimina la foto de perfil actual | **JWT requerido** |

### `InternalUserController` — `/internal/users` (USER-06)
| Método | Ruta | Descripción | Auth |
|---|---|---|---|
| GET | `/internal/users/{id}` | `{id, fullName, email, phone, emailVerified}` o 404 | `X-Internal-Api-Key` |
| POST | `/internal/users/batch` | Body `{ids: []}` (máx. 100) → lista con los mismos campos | `X-Internal-Api-Key` |
| GET | `/internal/users/by-email?email=` | El usuario o 404 | `X-Internal-Api-Key` |

## Servicios principales

- **`RegisterService`**: orquesta el registro, delega en `RegisterUserUseCase`.
- **`EmailVerificationService`**: códigos de verificación vía Redis.
- **`LogoutService`**: blacklist de JWT en Redis.
- **`RefreshTokenService`**: emisión/rotación/revocación de refresh tokens opacos (hash SHA-256, 30 días, detección de reuso → revoca toda la cadena).
- **`GoogleTokenVerifierService`**: validación criptográfica de ID Tokens de Google.
- **`RequestPasswordResetService` / `ResetPasswordService`**: flujo de recuperación de contraseña.
- **`ProfileImageService`**: convierte `MultipartFile` en `ProfileImageUploadRequest` (bytes, nombre, tipo, tamaño).
- **`FileValidationService`**: valida imágenes de perfil (máx. 5MB, tipos `image/jpeg|jpg|png|webp`), genera nombres únicos `{userId}/{timestamp}{ext}`.
- **`PublishAuthEventService`**: publica eventos `USER_REGISTERED` / `USER_LOGIN` / `USER_LOGOUT` a Pub/Sub.

## Otras features

- **Eventos de dominio (Google Pub/Sub)**: `GooglePubSubAuthEventAdapter` publica al topic `user-registered` para que otros microservicios (p. ej. notificaciones) reaccionen a registro/login/logout.
- **Fotos de perfil en Supabase Storage**: `SupabaseStorageAdapter` sube/elimina archivos vía API REST de Supabase, bucket `UserPetsProfilePicture`.
- **Correos transaccionales**: `EmailServiceAdapter` (JavaMailSender) para código de verificación, enlace de reset de password, confirmación de cambio de password, y aviso a cuentas Google que intentan resetear password.
- **OpenAPI/Swagger**: dependencia incluida; no hay configuración adicional ni ruta explícitamente abierta, por lo que Swagger UI quedaría bajo `anyRequest().authenticated()` (requeriría JWT) — verificar si es el comportamiento deseado.

## Manejo de excepciones

`GlobalExceptionHandler` (`@RestControllerAdvice`, limitado a `infrastructure.controllers`) mapea excepciones de dominio a HTTP:

| Excepción | HTTP |
|---|---|
| `UserAlreadyExistsException` | 409 |
| `InvalidUserDataException` | 400 |
| `InvalidCredentialsException` | 401 |
| `InvalidTokenException` | 401 |
| `InvalidPasswordResetTokenException` | 400 |
| `ExpiredPasswordResetTokenException` | 400 |
| `UserNotFoundException` | 404 |
| `UnauthorizedAccessException` | 403 |
| `InvalidFileException` | 400 |
| `FileSizeExceededException` | 413 |
| `InvalidImageFormatException` | 400 |
| `FileUploadException` | 500 |
| `EmailNotVerifiedException` | 403 (`code: EMAIL_NOT_VERIFIED`) |
| `InvalidVerificationCodeException` | 400 |
| `MethodArgumentNotValidException` | 400 (mapa `campo→mensaje`) |
| `Exception` genérica | 500 (mensaje genérico) |
| `NotVeterinaryMemberException` | 403 (`code: NOT_A_VETERINARY_MEMBER`) |
| `VeterinaryRejectedException` | 403 (`code: VETERINARY_REJECTED`) |
| `VeterinaryMembershipRevokedException` | 401 (`code: VETERINARY_MEMBERSHIP_REVOKED`) |
| `VeterinaryServiceUnavailableException` | 503 |

## Configuración (`application.yml` + `.env`)

- **Puerto**: `PORT` (default `8080`).
- **Base de datos**: Postgres en Supabase vía `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`, `ddl-auto: update`, `prepareThreshold=0` (compatibilidad con PgBouncer).
- **Redis**: `REDIS_HOST`/`REDIS_PORT`/`REDIS_PASSWORD`, SSL habilitado por defecto (apunta a Upstash).
- **Mail**: `MAIL_HOST` (default `smtp.gmail.com`), `MAIL_PORT` (587), `MAIL_USERNAME`/`MAIL_PASSWORD`, STARTTLS; `app.mail.from`, `app.mail.from-name` (default "MyAnimaLog"), `app.mail.reply-to`.
- **OAuth2 Google**: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI`.
- **JWT**:
  - `JWT_SECRET`: clave HMAC legada, usada solo para seguir validando tokens HS256 emitidos antes de la migración a RS256 (ver `JWT_ACCEPT_LEGACY`).
  - `JWT_PRIVATE_KEY_PEM`: llave privada RSA-2048 (PEM, PKCS8) usada para firmar los tokens nuevos. Obligatoria fuera del perfil `dev`; si falta y no hay perfil activo (caso actual) o el perfil es `dev`, se autogenera y persiste en `.local-keys/` (no versionado).
  - `JWT_KEY_ID`: `kid` publicado en el JWKS y en el header de los tokens nuevos (default `dev-key-1`).
  - `JWT_ACCESS_TTL_MINUTES`: duración del access token en minutos (default `60`, igual que antes de la migración).
  - `JWT_ACCEPT_LEGACY`: si `true` (default), los endpoints propios siguen aceptando tokens HS256 viejos (sin `kid`) además de los nuevos RS256. Poner en `false` una vez pase la ventana de expiración de los tokens emitidos antes del deploy de RS256.
  - `JWT_ISSUER` (default `myanimalog-user-service`) y `JWT_AUDIENCE` (default `myanimalog-api`): claims `iss`/`aud` del contrato compartido. Son constantes del protocolo entre servicios, no deberían cambiar por entorno salvo necesidad real.
- **`INTERNAL_API_KEY`**: llave compartida para `/internal/**` (header `X-Internal-Api-Key`). **Sin default** (igual que `JWT_SECRET`) — si falta, la app no arranca en absoluto (no solo `/internal/**`), porque es una propiedad `${INTERNAL_API_KEY}` sin fallback en `application.yml`. Debe ser la misma en todos los servicios del ecosistema que la usen (MVP, ver `CONTRATOS_COMPARTIDOS.md` §2); si se rota, hay que actualizarla en todos a la vez. Este servicio también la **usa como cliente** al llamar a Veterinary (`VeterinaryClientConfig`), con el mismo valor.
- **`VETERINARY_SERVICE_URL`**: base URL de Veterinary para resolver membresías de clínica (`VeterinaryMembershipAdapter`). **Tiene default `http://localhost:8082` solo para desarrollo** (igual que `FRONTEND_URL`); en Cloud Run (o cualquier entorno real) hay que configurarla explícitamente con la URL del `veterinary-service` desplegado — el default de localhost nunca debe quedar vigente fuera de un entorno local.
- **Frontend**: `FRONTEND_URL` (default `http://localhost:8100`), usado en el enlace de reset-password.
- **Supabase Storage**: `SUPABASE_URL`, `SUPABASE_KEY`.
- **GCP Pub/Sub**: `GCP_PROJECT_ID`, `pubsub.enabled=true`.
- **Multipart**: tamaño máx. de archivo/request 5MB.
- **Actuator**: solo expone `health`/`info`, `show-details: never`.
- No se usan perfiles Spring (`dev`/`prod`); una única configuración vía variables de entorno y `.env` (cargado con `spring-dotenv`).

## Migraciones de base de datos

`hibernate.ddl-auto: update` solo se usa para columnas simples sin riesgo. Cualquier cambio estructural relevante (columnas `NOT NULL`, constraints, tablas nuevas) se escribe como script SQL manual en `db/scripts/` y se corre a mano en Supabase — ver `db/scripts/README.md` para la convención (siempre calificar `public.` porque Supabase también tiene un esquema `auth` con su propia tabla `users`, que este servicio nunca toca; y toda tabla nueva se crea con `ENABLE ROW LEVEL SECURITY`) y la tabla de scripts ya aplicados. `002_refresh_tokens.sql` (tabla `refresh_tokens`) y `003_enable_rls.sql` ya se aplicaron y verificaron en Supabase — este último corrigió que `public.users` y `public.password_reset_tokens` tenían RLS **desactivado** y quedaban expuestas sin restricción por la API REST de Supabase.

## Docker

`Dockerfile` multi-stage:
- Build: `maven:3.9-eclipse-temurin-17`, `mvn dependency:go-offline` + `mvn clean package -DskipTests`.
- Runtime: `eclipse-temurin:17-jre-jammy`, usuario no-root `appuser`, expone `8080`.
- `.dockerignore` excluye `.env`, `target/`, `.git/`, etc.
- No hay `docker-compose.yml`: el desarrollo local depende de servicios cloud reales (Supabase, Upstash, GCP), no de contenedores locales de Postgres/Redis.

## Testing

~22 clases de test (JUnit 5 + Mockito + AssertJ), incluyendo las nuevas de USER-01:
- `JwtKeyProviderTest`: falla fuera del perfil `dev` sin `JWT_PRIVATE_KEY_PEM`; genera y reutiliza la llave de dev entre instancias; acepta una PEM explícita en cualquier perfil.
- `JwtUtilTest`: un token nuevo se valida con la llave pública sola (simula "pegar la llave en jwt.io") y trae `iss`/`aud`/`jti`/`iat`/`exp` además de los claims de negocio; dos tokens del mismo usuario tienen `jti` distinto; un token legado HS256 sigue siendo aceptado con `JWT_ACCEPT_LEGACY=true` y se rechaza con `false`; el TTL respeta `JWT_ACCESS_TTL_MINUTES`.
- `JwksControllerTest`: el JSON expuesto solo trae `kty/use/alg/kid/n/e`, nunca material de la llave privada.
- `AuthControllerTest` (antes era un archivo mal nombrado que en realidad probaba `LogoutService` — se corrigió): cubre que `loginLocal` emite el JWT con `sub=id` y los claims esperados (incluyendo `email_verified`, `platform_role`, `ctx`) más `refreshToken`/`expiresIn`; que `/auth/refresh` rota el token y emite un access token nuevo **sin** publicar `USER_LOGIN`; y que `logout` funciona con y sin `refreshToken` en el body.
- `RefreshTokenServiceTest`: `issue()` solo persiste el hash (nunca el valor plano); `rotate()` revoca el token usado y emite uno nuevo; reutilizar un token ya revocado revoca toda la cadena del usuario; token expirado o desconocido se rechaza sin tocar otras sesiones; `revoke()` es best-effort (no falla con tokens en blanco o inexistentes).
- `InternalApiKeyFilterTest`: sin header → 401; header con llave incorrecta → 401; llave correcta → deja pasar; rutas fuera de `/internal/**` no se tocan aunque no traigan el header.
- `InternalUserControllerTest`: mapea `username→fullName` y `phone=null`; nunca expone el password/hash en la respuesta; 404 por id inexistente o malformado; 400 si el batch pide más de 100 ids.
- `VeterinaryMembershipAdapterTest` (con `MockRestServiceServer`, bindeado a `RestClient.Builder`): mapea un 200 a `VeterinaryMembership` (incluyendo `member=false`); un 5xx y un 401 se traducen ambos a `VeterinaryServiceUnavailableException`.
- `AuthControllerTest` (ampliado para USER-03/04/05): miembro activo → token con `ctx=VETERINARY` y los 5 claims `vet_*` más todos los del token base; clínica `SUSPENDED` también emite token (solo `REJECTED` bloquea); no-miembro/inactivo → `NotVeterinaryMemberException`; clínica rechazada → `VeterinaryRejectedException`; Veterinary caído → `VeterinaryServiceUnavailableException`; refresh de contexto clínica activo rota con rol/estado actualizados y **no** revoca nada; refresh tras desactivar al empleado → revoca el refresh y lanza `VeterinaryMembershipRevokedException`; refresh de contexto `USER` nunca llama a `VeterinaryMembershipPort`; `/auth/context/user` emite `ctx=USER`; ambos endpoints de contexto revocan el refresh anterior cuando viene en el body.
- `RefreshTokenServiceTest` (ampliado): `issueVeterinaryContext()` persiste `context=VETERINARY` + `veterinaryId`; `validate()` no rota; `rotate(RefreshToken)`/`revoke(RefreshToken)` operan sobre un token ya resuelto sin volver a buscarlo.
- El resto: `LogoutService` (la más exhaustiva, blacklist de Redis y manejo de excepciones JWT, incluyendo mensajes específicos por tipo de error), `GoogleTokenVerifierService`, `RegisterService`/`RegisterUserUseCaseImpl`, `RequestPasswordResetService`/`ResetPasswordService`, `GetProfileUseCaseImpl`, `GoogleAuthUseCaseImpl`, `UpdateUserProfileUseCaseImpl`, `UserRepositoryAdapter`, controllers (`GetProfileController`, `RegisterController`, `UpdateProfileController`), `EmailServiceAdapter`, y smoke test de arranque del contexto (`PetsApplicationTests`).

**Sin cobertura**: `JwtAuthenticationFilter`/`SecurityConfig` como filtro HTTP end-to-end (p. ej. "sin token → 401" en `/auth/context/**`, o que `/auth/local`/`/auth/google`/`/auth/refresh` sigan sin exigir token) — los tests de `AuthController` llaman al método Java directamente, así que no ejercitan la cadena de filtros de Spring Security; esa garantía la da el framework (`authorizeHttpRequests`), verificada manualmente con llamadas HTTP reales en vez de con un test unitario. Tampoco hay cobertura de `EmailVerificationService`, subida/borrado de imágenes de perfil, `SupabaseStorageAdapter`, `GooglePubSubAuthEventAdapter`.

⚠️ `PetsApplicationTests.contextLoads` puede fallar en entornos sin conectividad/credenciales válidas hacia el Postgres de Supabase (`FATAL: password authentication failed`) — es un problema de entorno/credenciales, no de código; verificar `DB_PASSWORD` y acceso de red antes de asumir una regresión.

## Puntos a tener en cuenta / deuda técnica conocida

1. **Sin CORS configurado** desde el commit `22a9993` — revisar si el frontend necesita llamadas cross-origin directas.
2. ~~Sin refresh tokens~~ — **resuelto** (USER-02/04), `002_refresh_tokens.sql` aplicado y verificado en Supabase: ver sección "Refresh tokens". Pendiente real: no hay job de limpieza periódica para refresh tokens expirados/revocados (a diferencia de `TokenCleanupJob`).
3. **Sin RBAC real dentro del servicio** — el JWT ya lleva `platform_role` (USER-02), pero este servicio no lo usa todavía para ninguna decisión de autorización (sigue siendo puro ownership); sería para los consumidores del contrato (p. ej. Veterinary) o para futuros endpoints de administración.
4. Mensaje de validación desactualizado en `RegisterRequest` (dice "6 caracteres" pero exige mínimo 8).
5. Logging con `DEBUG`/`TRACE` de SQL y bind params habilitado — revisar antes de pasar a producción (riesgo de fuga de datos sensibles, p. ej. hashes de password en logs).
6. El `.env` local contiene credenciales reales (Supabase, Gmail, Google OAuth, JWT secret, Redis) — confirmar que esté en `.gitignore` y nunca commitearlo.
7. **El gate de "falla si falta `JWT_PRIVATE_KEY_PEM` fuera de `dev`" no protege nada todavía en la práctica**: como este servicio no fija `SPRING_PROFILES_ACTIVE` en ningún entorno hoy, "sin perfil activo" se trata como `dev` (para no romper el arranque actual) y generaría una llave efímera en vez de fallar. Cualquier despliegue real debe fijar un perfil explícito (p. ej. `SPRING_PROFILES_ACTIVE=prod`) además de `JWT_PRIVATE_KEY_PEM`.
8. ~~USER-06 (endpoints `/internal/**`)~~ — **resuelto**: implementado y protegido con `X-Internal-Api-Key`. Pendiente real: la tabla `users` sigue sin columnas `full_name` ni `phone` reales — hoy `fullName = username` y `phone = null` siempre, por decisión explícita documentada (ver sección de endpoints internos). Cuando se agreguen esas columnas, hay que actualizar `InternalUserController` para dejar de usar el `username` como sustituto.
9. ~~Claims pendientes del contrato compartido~~ — **resuelto**: `iss`, `aud`, `jti`, `platform_role`, `ctx`, `email_verified` ya se emiten en el JWT y `validateToken()` ya exige `iss`/`aud` correctos en tokens nuevos (USER-02).
10. **`JWT_ACCEPT_LEGACY=true` es temporal**: hay un TODO explícito en `JwtSigningKeyResolver`/`JwtUtil` para quitar la aceptación de tokens HS256 sin `kid` una vez pase la ventana de expiración de los tokens emitidos antes de esta migración.
11. **RLS en Supabase**: `public.users` y `public.password_reset_tokens` tenían Row Level Security desactivado (quedaban expuestas por la API REST de Supabase) — corregido en `003_enable_rls.sql`. Toda tabla nueva en `public` debe crearse con RLS activado desde el inicio (ver `db/scripts/README.md`).
12. **`INTERNAL_API_KEY` es un secreto compartido sin rotación automática**: si se filtra o se rota, hay que actualizarla a mano en todos los servicios que llamen a `/internal/**` (incluido este servicio como cliente de Veterinary). No hay múltiples llaves vigentes simultáneamente (a diferencia del `kid` del JWT, que sí soporta rotación con dos llaves activas).
13. ~~Token de contexto de clínica (USER-03/04/05)~~ — **resuelto**: `POST /auth/context/veterinary/{id}`, `POST /auth/context/user`, y el refresh de contexto clínica que re-valida membresía (USER-04) ya están implementados. Pendiente real: el catálogo completo de permisos por rol (`CONTRATOS_COMPARTIDOS.md` §3, matriz rol→permiso) vive enteramente en Veterinary — este servicio solo emite el token con `vet_role`/`vet_licensed`/`vet_status`, no interpreta esos permisos.
14. **`VeterinaryMembershipAdapter` no tiene test de timeout real**: `VeterinaryMembershipAdapterTest` cubre el mapeo de 200/401/5xx con `MockRestServiceServer`, pero no simula un timeout de socket real (`ResourceAccessException`) — la lógica de ese branch es simple (log + wrap) y se consideró bajo riesgo, pero quedó sin test dedicado.
15. **Ningún endpoint fuerza `vet_licensed=true` para acciones clínicas**: el claim viaja en el JWT, pero el catálogo de permisos (incluyendo el gate `🔑 licensed` de `CLINICAL_WRITE`/`NURSING_WRITE`) lo aplica Veterinary vía `GET /internal/veterinaries/{id}/patients/{petId}/access`, no este servicio.
