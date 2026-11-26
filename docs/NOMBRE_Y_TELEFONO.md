# Nombre completo (fullName) y teléfono (phone)

Se separó el nombre para mostrar del `username` (que sigue siendo el identificador único, sin espacios) y se agregó un teléfono opcional. Esto afecta el registro, el login con Google, el perfil propio (`/me`) y los endpoints internos `/internal/users/**`.

## Campos nuevos

| Campo | Tipo | Obligatorio | Único | Notas |
|---|---|---|---|---|
| `fullName` | string | No | **No** | Nombre para mostrar. 2 a 100 caracteres. Letras Unicode (incluye tildes y ñ), espacios, guion `-` y apóstrofe `'`. Se normaliza: `trim` + colapsa espacios repetidos. |
| `phone` | string | No | **No** | Se normaliza a formato internacional **E.164** (ej. `+573001234567`) antes de guardarse. Región por defecto para números sin código de país: Colombia (`CO`). |

`username` **no cambia**: sigue siendo obligatorio, único, sin espacios (`^[a-zA-Z0-9_]+$`, 3-50 caracteres).

## Registro — `POST /user/register`

### Request

```json
{
  "username": "luis_santiago",
  "email": "luis@example.com",
  "password": "Secreta123!",
  "fullName": "Luis Santiago",
  "phone": "3001234567"
}
```

`fullName` y `phone` son opcionales — se puede omitir cualquiera de los dos (o ambos) y el registro funciona igual que antes.

### Response (201)

```json
{
  "uuid": "a1b2c3d4-...",
  "username": "luis_santiago",
  "fullName": "Luis Santiago",
  "phone": "+573001234567",
  "email": "luis@example.com",
  "createdAt": "2026-11-26T10:00:00"
}
```

Nota: el teléfono queda guardado en formato E.164 (`+573001234567`), sin importar cómo lo escribió el usuario (`"3001234567"`, `"+57 300 123 4567"`, etc.).

### Login con Google — `POST /auth/google`

Si el usuario se registra/autentica por primera vez con Google, `fullName` sale automáticamente del nombre que reporta el proveedor (claim `name` del ID Token). El `username` se sigue generando igual que antes (slug del nombre o del email). `phone` nunca se completa desde Google — queda `null` hasta que el usuario lo agregue desde su perfil.

## Perfil propio — `GET /user/profile/{id}` (== `/me`)

Solo puede verse el propio perfil (ownership check, 403 si se intenta ver el de otro usuario — este endpoint nunca devuelve el perfil de otra persona, por lo que `phone` tampoco se filtra a terceros).

### Response (200)

```json
{
  "id": "a1b2c3d4-...",
  "username": "luis_santiago",
  "fullName": "Luis Santiago",
  "phone": "+573001234567",
  "email": "luis@example.com",
  "profileImageUrl": null,
  "createdAt": "2026-11-26T10:00:00",
  "authProvider": "LOCAL"
}
```

**Fallback**: si el usuario no tiene `fullName` guardado (`null`), este campo devuelve el `username` en su lugar. El frontend puede confiar en que `fullName` siempre viene con algún valor para mostrar.

## Actualizar perfil — `PUT /user/{id}`

Misma ruta de siempre. Ahora acepta también `fullName` y `phone`, ambos opcionales e independientes entre sí:

```json
{
  "username": "luis_santiago",
  "fullName": "Luis Santiago Gómez",
  "phone": "+573001234567"
}
```

- Si `fullName` o `phone` **no vienen** en el body (se omiten / son `null`), el valor guardado no se toca.
- Si vienen como string vacío (`""`), se **borran** (quedan `null`).
- `username` sigue siendo obligatorio en cada `PUT` (igual que antes).

### Response (200)

```json
{
  "id": "a1b2c3d4-...",
  "username": "luis_santiago",
  "fullName": "Luis Santiago Gómez",
  "phone": "+573001234567",
  "email": "luis@example.com"
}
```

## Endpoints internos — `/internal/users/**` (otros microservicios, `X-Internal-Api-Key`)

`GET /internal/users/{id}`, `POST /internal/users/batch`, `GET /internal/users/by-email` **mantienen todos sus campos actuales** y ahora devuelven datos reales (antes `fullName` era siempre igual al `username` y `phone` siempre `null`):

```json
{
  "id": "a1b2c3d4-...",
  "fullName": "Luis Santiago Gómez",
  "email": "luis@example.com",
  "phone": "+573001234567",
  "emailVerified": true
}
```

`fullName` aplica el mismo fallback al `username` cuando no hay nombre guardado. Ningún otro servicio necesita cambiar su integración: la forma de la respuesta no cambió, solo el contenido de estos dos campos.

## Errores nuevos

| Situación | HTTP | Body |
|---|---|---|
| `fullName` con `<`, `>`, caracteres de control, menos de 2 o más de 100 caracteres | 400 | `{"field_errors": {...}}` o `{"error": "...", "status": 400}` (viene de `InvalidUserDataException`, igual que los demás errores de datos de usuario) |
| `phone` con formato inválido (no es un número real) | 400 | `{"error": "Invalid phone number", "code": "INVALID_PHONE", "status": 400}` |

El `code: INVALID_PHONE` es nuevo — úsenlo para mostrar un mensaje específico de teléfono en el formulario en vez del genérico de "dato inválido".

## Qué debe cambiar en los formularios del frontend

1. **Formulario de registro**: agregar un campo "Nombre completo" (opcional, admite espacios y tildes) y un campo "Teléfono" (opcional). El campo "Usuario" sigue siendo el único que no admite espacios.
2. **Formulario de edición de perfil**: agregar los mismos dos campos. Si el usuario borra el campo y envía vacío, el valor se borra en el backend (comportamiento esperado, no es un bug).
3. **Pantalla de perfil / "quién soy"**: mostrar `fullName` en vez de `username` como nombre visible (ya viene con fallback al username, así que siempre hay algo que mostrar).
4. **Manejo de errores**: agregar un mensaje específico para `code: INVALID_PHONE` (ej. "El número de teléfono no es válido").
5. El campo "Usuario" (`username`) **no cambia de comportamiento** — sigue sin aceptar espacios y sigue siendo único; no hace falta tocar esa validación en el frontend.

## Lo que NO cambió

- El JWT no lleva `phone` en ningún claim — no se puede leer el teléfono desde el token.
- El teléfono nunca aparece en logs ni en la respuesta de otro usuario — solo en `/me` (perfil propio) y en `/internal/users/**` (protegido por `X-Internal-Api-Key`, uso servicio-a-servicio).
- `username` sigue siendo obligatorio, único, sin espacios.
