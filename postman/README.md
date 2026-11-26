# Postman — User service

Colección y environments generados a partir de los `@RestController` reales del servicio (no de la documentación).

## Archivos

- `build-collection.js` — genera `MyAnimaLogUser.postman_collection.json` a partir de una lista declarativa de endpoints. **No edites el `.json` a mano**: edita este script y vuelve a generarlo.
- `check-coverage.js` — lee los controladores y compara sus rutas contra la colección; reporta faltantes (en código, no en la colección) y sobrantes (en la colección, no en el código). Exit code 1 si hay alguno.
- `MyAnimaLogUser.postman_collection.json` — la colección (Postman Collection v2.1).
- `MyAnimaLogUser-Local.postman_environment.json` / `MyAnimaLogUser-CloudRun.postman_environment.json` — environments.

## Importar en Postman

1. File → Import → selecciona los 3 archivos (la colección + los 2 environments).
2. Si ya existe una versión anterior importada, al re-importar la colección elige **Replace** (no "Create a copy") para no duplicarla.
3. Selecciona el environment correspondiente (Local o Cloud Run) en el selector superior derecho.

## Variables a llenar a mano

| Variable | Dónde se llena | Notas |
|---|---|---|
| `user_url` | Ya viene en el environment | Local: `http://localhost:8080` (puerto de `application.yml`). Cloud Run: reemplazar el marcador por la URL real desplegada. |
| `access_token`, `refresh_token` | Se llenan solos | Los guarda el script de test de login/registro/refresh. |
| `clinic_token` | Se llena solo | Lo guarda "POST Obtener token de clínica" (carpeta 05). |
| `user_id` | Se llena solo | Lo guarda el registro (campo `uuid`) o el login (decodificado del JWT). |
| `veterinaryId` | **A mano** | Id de una clínica real en Veterinary de la que el usuario sea empleado activo (necesario para probar la carpeta 05). |
| `internal_api_key` | **A mano** | El valor de `INTERNAL_API_KEY` del `.env`/entorno real (nunca lo pongas en el archivo de environment commiteado). |

Todas las variables de token/llave son de tipo **secret** y quedan vacías en los archivos versionados — nunca hay credenciales reales en este repo.

## Orden recomendado de pruebas

1. **01 · Registro** → "POST Registrar usuario" (guarda `user_id`; usa `{{$timestamp}}` para no chocar con registros previos).
2. **02 · Verificación de email** (opcional en flujo manual: revisar el código enviado por correo).
3. **00 · Autenticación** → "POST Iniciar sesión" (guarda `access_token`, `refresh_token`, `user_id`).
4. **04 · Perfil** → probar obtener/actualizar perfil y foto con el `access_token` ya guardado.
5. **05 · Contexto de clínica** → requiere `veterinaryId` real (llenar a mano) y que el usuario sea empleado de esa clínica en Veterinary.
6. **06 · Internos** → requiere `internal_api_key` real (llenar a mano); no usa Bearer.
7. **00 · Autenticación** → "POST Refrescar token" / "POST Cerrar sesión" para probar el ciclo completo.

Los requests de **01 · Registro** marcados `(error esperado)` prueban validaciones (`username` con espacios, `fullName` con `< >`, `phone` inválido → `code: INVALID_PHONE`) y deben responder 400, no 2xx.

## Regenerar y verificar

```bash
node postman/build-collection.js   # regenera MyAnimaLogUser.postman_collection.json
node postman/check-coverage.js     # debe reportar 0 faltantes y 0 sobrantes
```

Ver también `docs/NOMBRE_Y_TELEFONO.md` para el detalle de los campos `fullName`/`phone` usados en los ejemplos de la carpeta 01.
