#!/usr/bin/env node
/**
 * Genera postman/MyAnimaLogUser.postman_collection.json a partir de una lista
 * declarativa de endpoints (abajo). No depende de paquetes externos.
 * Uso: node postman/build-collection.js
 */
"use strict";

const fs = require("fs");
const path = require("path");

const NOAUTH = { type: "noauth" };
const BEARER_ACCESS = { type: "bearer", bearer: [{ key: "token", value: "{{access_token}}", type: "string" }] };

function header(key, value) {
  return { key, value, type: "text" };
}

function buildUrl(routePath, query) {
  const segments = routePath.split("/").filter(Boolean);
  let raw = "{{user_url}}" + routePath;
  const url = { raw, host: ["{{user_url}}"], path: segments };
  if (query && query.length) {
    url.query = query.map((q) => ({ key: q.key, value: q.value, disabled: !!q.disabled }));
    raw += "?" + query.map((q) => `${q.key}=${q.value}`).join("&");
    url.raw = raw;
  }
  return url;
}

function jsonBody(obj) {
  return { mode: "raw", raw: JSON.stringify(obj, null, 2), options: { raw: { language: "json" } } };
}

function formDataBody(fields) {
  return { mode: "formdata", formdata: fields };
}

function testScript(lines) {
  return [{ listen: "test", script: { type: "text/javascript", exec: lines } }];
}

function exampleResponse(name, status, code, body) {
  return {
    name,
    originalRequest: {},
    status: code === 200 ? "OK" : code === 201 ? "Created" : code === 204 ? "No Content" : "Error",
    code,
    header: [{ key: "Content-Type", value: "application/json" }],
    body: body === undefined ? "" : JSON.stringify(body, null, 2),
  };
}

function buildRequest(def) {
  const { name, method, routePath, headers = [], query, body, auth, tests = [], description = "", examples = [] } = def;
  const request = {
    method,
    header: headers.map((h) => header(h.key, h.value)),
    url: buildUrl(routePath, query),
    description,
  };
  if (body) request.body = body;
  if (auth) request.auth = auth;

  const item = { name, request };
  if (tests.length) item.event = testScript(tests);
  if (examples.length) {
    item.response = examples.map((ex) => exampleResponse(ex.name, ex.status, ex.status, ex.body));
  }
  return item;
}

function folder(name, items, auth) {
  const f = { name, item: items.map(buildRequest) };
  if (auth) f.auth = auth;
  return f;
}

// ---------------------------------------------------------------------------
// Datos: una entrada por request. routePath debe coincidir EXACTO con la ruta
// Spring del controlador (sin variables de Postman) para que check-coverage.js
// pueda cruzarla contra el codigo.
// ---------------------------------------------------------------------------

const JWT_CLAIM_DECODE = [
  "function decodeJwtId(token) {",
  "    try {",
  "        const payload = JSON.parse(atob(token.split('.')[1]));",
  "        return payload.id;",
  "    } catch (e) { return undefined; }",
  "}",
];

const folders = [
  folder(
    "00 · Autenticación",
    [
      {
        name: "POST Iniciar sesión",
        method: "POST",
        routePath: "/auth/local",
        headers: [{ key: "Content-Type", value: "application/json" }],
        body: jsonBody({ email: "luis.santiago@example.com", password: "Secreta123!" }),
        description:
          "Login local. Publico (sin token). Responde `{token, refreshToken, expiresIn}`.\n\nErrores: 401 credenciales invalidas, 403 `EMAIL_NOT_VERIFIED`.",
        tests: [
          ...JWT_CLAIM_DECODE,
          "pm.test('Status 200', () => pm.response.to.have.status(200));",
          "const body = pm.response.json();",
          "pm.environment.set('access_token', body.token);",
          "pm.environment.set('refresh_token', body.refreshToken);",
          "const id = decodeJwtId(body.token);",
          "if (id) pm.environment.set('user_id', id);",
        ],
        examples: [
          { name: "200 OK", status: 200, body: { token: "<jwt>", refreshToken: "<refresh-token>", expiresIn: 3600 } },
        ],
      },
      {
        name: "POST Iniciar sesión con Google",
        method: "POST",
        routePath: "/auth/google",
        headers: [{ key: "Content-Type", value: "application/json" }],
        body: jsonBody({ idToken: "<id-token-de-google>" }),
        description:
          "Login/registro automatico via ID Token de Google. Publico. Responde `{token, refreshToken, expiresIn}`.\n\nErrores: 401 `InvalidTokenException` si el idToken es invalido o falta.",
        tests: [
          ...JWT_CLAIM_DECODE,
          "pm.test('Status 200', () => pm.response.to.have.status(200));",
          "const body = pm.response.json();",
          "pm.environment.set('access_token', body.token);",
          "pm.environment.set('refresh_token', body.refreshToken);",
          "const id = decodeJwtId(body.token);",
          "if (id) pm.environment.set('user_id', id);",
        ],
      },
      {
        name: "POST Refrescar token",
        method: "POST",
        routePath: "/auth/refresh",
        headers: [{ key: "Content-Type", value: "application/json" }],
        body: jsonBody({ refreshToken: "{{refresh_token}}" }),
        description:
          "Rota el refresh token y emite un access token nuevo (revalida membresia si era de contexto clinica). Publico (valida el refresh token, no un JWT).\n\nErrores: 401 token invalido/expirado/reutilizado, 401 `VETERINARY_MEMBERSHIP_REVOKED`, 503 si Veterinary no responde.",
        tests: [
          "pm.test('Status 200', () => pm.response.to.have.status(200));",
          "const body = pm.response.json();",
          "pm.environment.set('access_token', body.token);",
          "pm.environment.set('refresh_token', body.refreshToken);",
        ],
      },
      {
        name: "POST Cerrar sesión",
        method: "POST",
        routePath: "/auth/logout",
        headers: [{ key: "Content-Type", value: "application/json" }],
        auth: BEARER_ACCESS,
        body: jsonBody({ refreshToken: "{{refresh_token}}" }),
        description:
          "Invalida el access token actual (blacklist en Redis). Si el body trae `refreshToken`, tambien lo revoca. Requiere `Authorization: Bearer` (override de carpeta, que es No Auth).",
        tests: ["pm.test('Status 200', () => pm.response.to.have.status(200));"],
      },
      {
        name: "GET Llave pública (JWKS)",
        method: "GET",
        routePath: "/.well-known/jwks.json",
        description: "Publica la llave publica RSA activa (JWKS) para validar los JWT. Publico, sin JWT.",
        tests: ["pm.test('Status 200', () => pm.response.to.have.status(200));"],
      },
    ],
    NOAUTH
  ),

  folder(
    "01 · Registro",
    [
      {
        name: "POST Registrar usuario",
        method: "POST",
        routePath: "/user/register",
        headers: [{ key: "Content-Type", value: "application/json" }],
        body: jsonBody({
          username: "luis_santiago_{{$timestamp}}",
          email: "luis.santiago.{{$timestamp}}@example.com",
          password: "Secreta123!",
          fullName: "Luis Santiago",
          phone: "3001234567",
        }),
        description:
          "Registro de usuario local + envio de codigo de verificacion. Publico. `fullName` y `phone` son opcionales (ver docs/NOMBRE_Y_TELEFONO.md). `phone` se normaliza a E.164.\n\nErrores: 409 email/username ya existen, 400 datos invalidos (incluye `INVALID_PHONE`).",
        tests: [
          "pm.test('Status 201', () => pm.response.to.have.status(201));",
          "const body = pm.response.json();",
          "pm.environment.set('user_id', body.uuid);",
        ],
        examples: [
          {
            name: "201 Created",
            status: 201,
            body: {
              uuid: "a1b2c3d4-...",
              username: "luis_santiago_1234",
              fullName: "Luis Santiago",
              phone: "+573001234567",
              email: "luis.santiago.1234@example.com",
              createdAt: "2026-11-26T10:00:00",
            },
          },
        ],
      },
      {
        name: "POST Registrar usuario - username con espacios (error esperado)",
        method: "POST",
        routePath: "/user/register",
        headers: [{ key: "Content-Type", value: "application/json" }],
        body: jsonBody({
          username: "Luis Santiago",
          email: "error.username.{{$timestamp}}@example.com",
          password: "Secreta123!",
        }),
        description:
          "El username sigue sin admitir espacios (regla sin cambios). Espera 400 con el mapa de errores de campo (`username`).",
        tests: [
          "pm.test('Status 400', () => pm.response.to.have.status(400));",
          "pm.test('Error de username', () => {",
          "    const body = pm.response.json();",
          "    pm.expect(JSON.stringify(body)).to.include('username');",
          "});",
        ],
      },
      {
        name: "POST Registrar usuario - fullName con < > (error esperado)",
        method: "POST",
        routePath: "/user/register",
        headers: [{ key: "Content-Type", value: "application/json" }],
        body: jsonBody({
          username: "usuario_{{$timestamp}}",
          email: "error.fullname.{{$timestamp}}@example.com",
          password: "Secreta123!",
          fullName: "Luis <script>",
        }),
        description: "`fullName` rechaza `<` y `>` (FullNamePolicy). Espera 400 (`InvalidUserDataException`).",
        tests: ["pm.test('Status 400', () => pm.response.to.have.status(400));"],
      },
      {
        name: "POST Registrar usuario - phone inválido (error esperado)",
        method: "POST",
        routePath: "/user/register",
        headers: [{ key: "Content-Type", value: "application/json" }],
        body: jsonBody({
          username: "usuario_{{$timestamp}}",
          email: "error.phone.{{$timestamp}}@example.com",
          password: "Secreta123!",
          phone: "not-a-phone",
        }),
        description: "`phone` invalido (PhoneNumberPolicy + libphonenumber). Espera 400 con `code: INVALID_PHONE`.",
        tests: [
          "pm.test('Status 400', () => pm.response.to.have.status(400));",
          "pm.test('code INVALID_PHONE', () => {",
          "    pm.expect(pm.response.json().code).to.eql('INVALID_PHONE');",
          "});",
        ],
        examples: [
          { name: "400 INVALID_PHONE", status: 400, body: { error: "Invalid phone number", code: "INVALID_PHONE", status: 400 } },
        ],
      },
    ],
    NOAUTH
  ),

  folder(
    "02 · Verificación de email",
    [
      {
        name: "POST Verificar código",
        method: "POST",
        routePath: "/auth/verify-email",
        headers: [{ key: "Content-Type", value: "application/json" }],
        body: jsonBody({ email: "luis.santiago@example.com", code: "123456" }),
        description: "Verifica el codigo de 6 digitos enviado al registrarse. Publico.\n\nErrores: 400 codigo invalido/expirado.",
        tests: ["pm.test('Status 200', () => pm.response.to.have.status(200));"],
      },
      {
        name: "POST Reenviar código",
        method: "POST",
        routePath: "/auth/resend-verification",
        headers: [{ key: "Content-Type", value: "application/json" }],
        body: jsonBody({ email: "luis.santiago@example.com" }),
        description: "Reenvia el codigo de verificacion (respeta cooldown de 60s). Publico, mensaje siempre generico.",
        tests: ["pm.test('Status 200', () => pm.response.to.have.status(200));"],
      },
    ],
    NOAUTH
  ),

  folder(
    "03 · Recuperación de contraseña",
    [
      {
        name: "POST Solicitar recuperación de contraseña",
        method: "POST",
        routePath: "/auth/request-password-reset",
        headers: [{ key: "Content-Type", value: "application/json" }],
        body: jsonBody({ email: "luis.santiago@example.com" }),
        description: "Envia el enlace de reseteo si la cuenta existe y es local. Publico, mensaje siempre generico (anti enumeration).",
        tests: ["pm.test('Status 200', () => pm.response.to.have.status(200));"],
      },
      {
        name: "POST Restablecer contraseña",
        method: "POST",
        routePath: "/auth/reset-password",
        headers: [{ key: "Content-Type", value: "application/json" }],
        body: jsonBody({ token: "<token-del-correo>", newPassword: "NuevaSecreta123!" }),
        description: "Aplica la nueva contrasena con el token recibido por correo. Publico.\n\nErrores: 400 token invalido/expirado o igual a la actual.",
        tests: ["pm.test('Status 200', () => pm.response.to.have.status(200));"],
      },
    ],
    NOAUTH
  ),

  folder(
    "04 · Perfil",
    [
      {
        name: "GET Obtener perfil",
        method: "GET",
        routePath: "/user/profile/{{user_id}}",
        description:
          "Obtiene el perfil propio (solo si `{id}` == usuario autenticado). `fullName` cae al `username` si no hay nombre guardado.\n\nErrores: 403 si se intenta ver el perfil de otro usuario.",
        tests: ["pm.test('Status 200', () => pm.response.to.have.status(200));"],
      },
      {
        name: "PUT Actualizar perfil",
        method: "PUT",
        routePath: "/user/{{user_id}}",
        headers: [{ key: "Content-Type", value: "application/json" }],
        body: jsonBody({ username: "luis_santiago", fullName: "Luis Santiago Gómez", phone: "3001234567" }),
        description:
          "Actualiza username (obligatorio), fullName y phone (opcionales e independientes; omitidos = no se tocan, vacios = se borran) del propio usuario.\n\nErrores: 409 username ya existe, 400 datos invalidos (incluye `INVALID_PHONE`), 403 si no es el propio usuario.",
        tests: ["pm.test('Status 200', () => pm.response.to.have.status(200));"],
      },
      {
        name: "POST Subir foto de perfil",
        method: "POST",
        routePath: "/user/profile/image",
        auth: BEARER_ACCESS,
        body: formDataBody([{ key: "file", type: "file", src: [] }]),
        description: "Sube/reemplaza la foto de perfil (multipart, campo `file`, max 5MB, image/jpeg|jpg|png|webp).",
        tests: ["pm.test('Status 200', () => pm.response.to.have.status(200));"],
      },
      {
        name: "DELETE Eliminar foto de perfil",
        method: "DELETE",
        routePath: "/user/profile/image",
        description: "Elimina la foto de perfil actual del usuario autenticado.",
        tests: ["pm.test('Status 204', () => pm.response.to.have.status(204));"],
      },
    ],
    BEARER_ACCESS
  ),

  folder(
    "05 · Contexto de clínica",
    [
      {
        name: "POST Obtener token de clínica",
        method: "POST",
        routePath: "/auth/context/veterinary/{{veterinaryId}}",
        headers: [{ key: "Content-Type", value: "application/json" }],
        body: jsonBody({ refreshToken: "{{clinic_token}}" }),
        description:
          "Emite un token de contexto de clinica si el usuario es empleado activo. Requiere JWT de usuario.\n\nErrores: 403 `NOT_A_VETERINARY_MEMBER`, 403 `VETERINARY_REJECTED`, 403 `VETERINARY_SUSPENDED`, 503 si Veterinary no responde.",
        tests: [
          "pm.test('Status 200', () => pm.response.to.have.status(200));",
          "const body = pm.response.json();",
          "pm.environment.set('clinic_token', body.token);",
        ],
      },
      {
        name: "POST Volver a token de usuario",
        method: "POST",
        routePath: "/auth/context/user",
        headers: [{ key: "Content-Type", value: "application/json" }],
        body: jsonBody({ refreshToken: "{{clinic_token}}" }),
        description: "Vuelve a un token normal (`ctx=USER`), revocando el refresh de clinica anterior si viene en el body.",
        tests: [
          "pm.test('Status 200', () => pm.response.to.have.status(200));",
          "const body = pm.response.json();",
          "pm.environment.set('access_token', body.token);",
          "pm.environment.set('refresh_token', body.refreshToken);",
          "pm.environment.set('clinic_token', '');",
        ],
      },
    ],
    BEARER_ACCESS
  ),

  folder(
    "06 · Internos",
    [
      {
        name: "GET Obtener usuario interno por id",
        method: "GET",
        routePath: "/internal/users/{{user_id}}",
        headers: [{ key: "X-Internal-Api-Key", value: "{{internal_api_key}}" }],
        description:
          "Usado por otros servicios del ecosistema (ej. Veterinary). Nunca expuesto por el gateway, solo servicio a servicio.\n\nErrores: 401 sin llave o llave incorrecta, 404 usuario no encontrado.",
        tests: ["pm.test('Status 200', () => pm.response.to.have.status(200));"],
      },
      {
        name: "POST Obtener usuarios internos en lote",
        method: "POST",
        routePath: "/internal/users/batch",
        headers: [
          { key: "X-Internal-Api-Key", value: "{{internal_api_key}}" },
          { key: "Content-Type", value: "application/json" },
        ],
        body: jsonBody({ ids: ["{{user_id}}"] }),
        description: "Maximo 100 ids por llamada. Los ids que no existan simplemente no aparecen en la respuesta.\n\nErrores: 400 si se piden mas de 100 ids.",
        tests: ["pm.test('Status 200', () => pm.response.to.have.status(200));"],
      },
      {
        name: "GET Obtener usuario interno por email",
        method: "GET",
        routePath: "/internal/users/by-email",
        query: [{ key: "email", value: "luis.santiago@example.com" }],
        headers: [{ key: "X-Internal-Api-Key", value: "{{internal_api_key}}" }],
        description: "Busca un usuario por email. Protegido solo por `X-Internal-Api-Key`.\n\nErrores: 404 si no existe.",
        tests: ["pm.test('Status 200', () => pm.response.to.have.status(200));"],
      },
    ],
    NOAUTH
  ),
];

const collection = {
  info: {
    _postman_id: "myanimalog-user-service-collection",
    name: "MyAnimaLog User Service",
    description:
      "Coleccion generada con postman/build-collection.js — NO editar el .json a mano, editar este script y regenerar.\n\n" +
      "Orden recomendado: 00 (login/registro) -> 01 (registro) -> 02/03 (si aplica) -> 04 (perfil) -> 05 (contexto de clinica, requiere veterinaryId real) -> 06 (internos, requiere internal_api_key).\n\n" +
      "Token por carpeta: 00-03 sin auth (publicas); 04-05 Bearer {{access_token}} (05 tambien acepta {{clinic_token}}); 06 header X-Internal-Api-Key, sin Bearer.\n\n" +
      "Ver postman/README.md para detalle de variables y como regenerar/verificar esta coleccion.",
    schema: "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
  },
  item: folders,
};

const outFile = path.join(__dirname, "MyAnimaLogUser.postman_collection.json");
fs.writeFileSync(outFile, JSON.stringify(collection, null, 2) + "\n", "utf8");
console.log("Coleccion generada en " + outFile);
