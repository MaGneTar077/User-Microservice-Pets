# Contratos compartidos — MyAnimaLog / MyAnimaLogVet

> Copia este archivo **igual** en todos los repos (user, pets, veterinary, medical, notifications, ia, calendar).
> Si un contrato cambia, se cambia aquí primero y se replica. Ningún servicio inventa su propia versión.

---

## 1. JWT

### 1.1 Firma

- Algoritmo: **RS256** (asimétrico). Solo el **User service** tiene la llave privada y firma tokens.
- Los demás servicios **solo verifican** con la llave pública publicada en:
  `GET {USER_SERVICE_URL}/.well-known/jwks.json`
- Cada llave lleva `kid`. Los verificadores deben cachear el JWKS (5–10 min) y refrescarlo si llega un `kid` desconocido.
- `iss` = `myanimalog-user-service`
- `aud` = `myanimalog-api`
- El `JWT_SECRET` (HS256) que existe hoy en Pets y Veterinary **se elimina** al terminar la migración.

### 1.2 Token base (cualquier usuario que inicia sesión)

```json
{
  "iss": "myanimalog-user-service",
  "aud": "myanimalog-api",
  "sub": "8f1c…-uuid-del-usuario",
  "email": "ana@correo.com",
  "email_verified": true,
  "platform_role": "USER",
  "ctx": "USER",
  "iat": 1790891292,
  "exp": 1790892192,
  "jti": "uuid-del-token"
}
```

| Claim | Valores | Uso |
|---|---|---|
| `sub` | uuid | Identidad del usuario. **Reemplaza** todo `userId`/`ownerId`/`uploadedBy` que hoy viaja en bodies o query params |
| `platform_role` | `USER` \| `PLATFORM_ADMIN` | `PLATFORM_ADMIN` = equipo MyAnimaLog (aprueba clínicas, soporte) |
| `ctx` | `USER` \| `VETERINARY` | Tipo de token |
| `email_verified` | bool | Obligatorio `true` para aceptar invitaciones |
| `exp` | — | **15 minutos** de vida |

### 1.3 Token de contexto de clínica

Se obtiene con `POST /auth/context/veterinary/{veterinaryId}` en el User service (ver `USER_SERVICE_CHANGES.md`).
Lleva todo lo del token base más:

```json
{
  "ctx": "VETERINARY",
  "vet_id": "uuid-clinica",
  "vet_role": "VETERINARIAN",
  "employee_id": "uuid-empleado",
  "vet_licensed": true,
  "vet_status": "ACTIVE"
}
```

| Claim | Valores |
|---|---|
| `vet_role` | `OWNER` \| `ADMIN` \| `VETERINARIAN` \| `ASSISTANT` \| `RECEPTIONIST` |
| `vet_licensed` | `true` si el perfil profesional del usuario está `VERIFIED` (tarjeta profesional aprobada) |
| `vet_status` | `PENDING_DOCUMENTS` \| `UNDER_REVIEW` \| `NEEDS_CORRECTION` \| `ACTIVE` \| `REJECTED` \| `SUSPENDED` |

### 1.4 Refresh token

- Opaco (no JWT), guardado **hasheado** en la BD del User service, vida 30 días, **rotación** en cada uso.
- Refrescar un token de clínica **vuelve a consultar** a Veterinary (rol, estado, licencia). Por eso un cambio de rol o una desactivación aplica en máximo 15 minutos.

### 1.5 Regla obligatoria en todo servicio que reciba un token de clínica

Si la ruta o el body trae un `veterinaryId`, **debe ser igual a `vet_id` del token**. Si no → `403`.
Esto es lo que impide que una clínica lea datos de otra.

---

## 2. Autenticación servicio a servicio

- Rutas bajo `/internal/**` **no** aceptan JWT de usuario. Requieren el header:
  `X-Internal-Api-Key: <INTERNAL_API_KEY>`
- La variable `INTERNAL_API_KEY` es la misma en todos los servicios (MVP). En producción en Cloud Run se puede migrar a ID tokens de IAM.
- Las rutas `/internal/**` nunca se exponen por el gateway.
- Timeout de llamadas internas: 3 s. Si el servicio llamado no responde → el que llama responde `503`, nunca "deja pasar".

---

## 3. Endpoint de acceso (el más usado de todo el ecosistema)

Veterinary es la **única fuente de verdad** sobre qué puede hacer un empleado sobre una mascota.
Pets, Medical y Calendar **no** duplican la tabla de permisos: preguntan aquí.

```
GET {VETERINARY_SERVICE_URL}/internal/veterinaries/{veterinaryId}/patients/{petId}/access?userId={userId}
X-Internal-Api-Key: …
```

Respuesta `200`:

```json
{
  "hasAccess": true,
  "veterinaryStatus": "ACTIVE",
  "employeeId": "uuid",
  "role": "VETERINARIAN",
  "licensed": true,
  "permissions": ["CLINICAL_READ", "CLINICAL_WRITE", "NURSING_WRITE", "DOCUMENT_UPLOAD", "APPOINTMENT_MANAGE", "PATIENT_REGISTER"]
}
```

- `hasAccess = false` si: el usuario no es empleado activo, la clínica no está `ACTIVE`, o la mascota no está compartida con la clínica (consentimiento revocado o inexistente).
- Los consumidores pueden cachear la respuesta **60 segundos** por `(veterinaryId, petId, userId)`.

### 3.1 Catálogo de permisos

| Permiso | Qué habilita |
|---|---|
| `CLINIC_CONFIGURE` | Perfil, logo, configuración, horarios, catálogo de servicios |
| `SUBSCRIPTION_MANAGE` | Plan, pagos, cancelación |
| `STAFF_MANAGE` | Invitar, cambiar rol, activar/desactivar empleados |
| `APPOINTMENT_MANAGE` | Crear, confirmar, reprogramar, cancelar, check-in de citas |
| `PATIENT_REGISTER` | Registrar pacientes sin app, invitar a reclamar |
| `CLINICAL_READ` | Leer historia clínica completa |
| `CLINICAL_READ_BASIC` | Leer datos básicos (nombre, especie, alergias, próxima cita) |
| `CLINICAL_WRITE` | Visitas, diagnóstico, tratamientos, medicación, cirugías, laboratorios |
| `NURSING_WRITE` | Peso, signos, aplicar vacuna |
| `DOCUMENT_UPLOAD` | Subir documentos a la mascota |
| `REPORTS_VIEW` | Reportes del negocio |

### 3.2 Matriz rol → permisos

| Permiso | OWNER | ADMIN | VETERINARIAN | ASSISTANT | RECEPTIONIST |
|---|:-:|:-:|:-:|:-:|:-:|
| CLINIC_CONFIGURE | ✅ | ✅ | | | |
| SUBSCRIPTION_MANAGE | ✅ | | | | |
| STAFF_MANAGE | ✅ | ✅ | | | |
| APPOINTMENT_MANAGE | ✅ | ✅ | ✅ | ✅ | ✅ |
| PATIENT_REGISTER | ✅ | ✅ | ✅ | ✅ | ✅ |
| CLINICAL_READ | ✅ | ✅ | ✅ | ✅ | |
| CLINICAL_READ_BASIC | ✅ | ✅ | ✅ | ✅ | ✅ |
| CLINICAL_WRITE | 🔑 | 🔑 | 🔑 | | |
| NURSING_WRITE | 🔑 | 🔑 | ✅ | ✅ | |
| DOCUMENT_UPLOAD | ✅ | ✅ | ✅ | ✅ | |
| REPORTS_VIEW | ✅ | ✅ | | | |

🔑 = solo si `licensed = true` (tarjeta profesional verificada). Un `VETERINARIAN` sin licencia verificada **no** tiene `CLINICAL_WRITE` ni `NURSING_WRITE` hasta que se apruebe.

Además, si `veterinaryStatus != ACTIVE`, la clínica solo conserva `CLINIC_CONFIGURE`, `STAFF_MANAGE` y `SUBSCRIPTION_MANAGE` (puede prepararse mientras la revisan, pero no atender).

---

## 4. Eventos (Google Cloud Pub/Sub)

### 4.1 Sobre único para todos los servicios

```json
{
  "eventType": "APPOINTMENT_CONFIRMED",
  "userId": "uuid-del-destinatario-o-null",
  "recipient": {
    "email": "dueño@correo.com",
    "phone": "+573001234567",
    "name": "Carlos Pérez"
  },
  "payload": { "...": "datos propios del evento" },
  "occurredAt": "2026-10-01T15:04:05Z",
  "metadata": {
    "version": "1.0",
    "source": "veterinary-service",
    "veterinaryId": "uuid-o-null",
    "correlationId": "uuid"
  }
}
```

Reglas:

1. `userId` = **a quién se le notifica** (no quién hizo la acción). Si el destinatario no tiene cuenta (dueño sin app), `userId = null` y `recipient` es obligatorio.
2. Si `userId` y `recipient` vienen ambos vacíos, el evento es inválido: **no se publica** (se loguea error en el emisor).
3. `payload` lleva todo lo que la notificación necesita para armarse **sin** tener que llamar a otro servicio (nombre de mascota, nombre de clínica, fecha de cita…).
4. Un fallo al publicar **nunca** tumba la respuesta HTTP (mismo patrón que Medical: `try/catch` separado + log).
5. Pets hoy publica un `PetEvent` sin detalle: se migra a este sobre (ver `PETS_SERVICE_CHANGES.md`).

### 4.2 Topics por servicio

| Servicio | Topics |
|---|---|
| Veterinary | `veterinary-clinic`, `veterinary-verification`, `veterinary-staff`, `veterinary-patients`, `veterinary-subscription` |
| Calendar (o Veterinary si la agenda vive allí) | `veterinary-appointments` |
| Pets | los actuales `pet-*` + `pet-claim-invitation-sent`, `pet-claimed` |
| Medical | los actuales `medical-pet-*` (sin cambios de nombre) |

Suscripciones del servicio de notificaciones: `sub-notificaciones-<topic>`.

---

## 5. Convenciones HTTP

- Errores con el formato que ya usa Veterinary: `{ "timestamp", "status", "error", "message" }`.
- `401` = token ausente, vencido o firma inválida. `403` = token válido pero sin permiso / otra clínica.
- Si hay gateway, cada servicio tiene prefijo propio: `/api/users/**`, `/api/pets/**`, `/api/veterinary/**`, `/api/medical/**`, `/api/calendar/**`. Aunque haya gateway, **cada servicio valida el JWT por sí mismo** (el gateway es opcional, la seguridad no).
- Fechas en ISO-8601 UTC. Zona horaria de negocio por clínica (default `America/Bogota`).
