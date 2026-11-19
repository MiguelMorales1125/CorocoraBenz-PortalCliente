# CB-PortalCliente (`cb-portal-cliente`)

Portal web del cliente de postventa: registra solicitudes de servicio, muestra su avance y recibe la respuesta del cliente a la cotización (Spring Boot 3.5.6, Java 17).

> **Este repositorio es un componente de Corocora Benz. El hub integrador, con la guía general, está en https://github.com/MiguelMorales1125/Corocora-Benz.**

Repositorio de este componente: https://github.com/MiguelMorales1125/CorocoraBenz-PortalCliente

## Propósito

Es la cara del proceso **Postventa y Garantías** (`CB_PostventaGarantias`) para el participante externo **Cliente** del BPMN. Atiende tres puntos del modelo:

| Elemento del BPMN | Qué hace el portal |
|---|---|
| Evento de inicio `startRequest` ("Solicitud de servicio recibida") | Envía el mensaje `SolicitudServicio`, que inicia el proceso |
| Evento `quoteAccepted` ("Cotización aceptada") | Envía el mensaje `CotizacionAceptada` |
| Evento `quoteRejected` ("Cotización rechazada") | Envía el mensaje `CotizacionRechazada` |

El resto del proceso (asesor, analista de garantías, coordinador, taller) lo atienden los empleados en CentralSys; el portal solo lo consulta para mostrar el avance.

**Comunicación con el motor.** El navegador nunca llama al motor: habla solo con el backend de este portal (`/api/app/**`), y el backend usa la REST de Camunda (`ENGINE_REST_URL`) con la credencial interna `X-CB-Internal`, a través de `MotorClient` de `cb-comun`:

- `POST /engine-rest/message`: mensajes `SolicitudServicio`, `CotizacionAceptada` y `CotizacionRechazada`, correlacionados por `businessKey` (`PV-` más 8 caracteres, generada por el portal). Con `SolicitudServicio` viajan las variables `vin`, `customerName`, `mileageKm`, `serviceType`, `symptom`, `customerUserId` y, si se llenó, `customerEmail`.
- `GET /engine-rest/history/process-instance`, `/history/activity-instance` y `/history/variable-instance`: avance del caso. Al navegador solo llega una lista fija de variables (`vin`, `coverageStatus`, `appointmentDate`, `quoteId`, `quoteAmount`, entre otras).
- `GET /engine-rest/event-subscription`: saber si el proceso espera `CotizacionAceptada` (campo `canReply`).
- `POST /engine-rest/identity/verify` y `GET /engine-rest/group?member=`: inicio de sesión.

El motor no llama a este portal: no expone API interna ni recibe conectores HTTP.

## Puerto, pantallas y API

Puerto **9001**. Página única en http://localhost:9001/ con dos secciones:

- **Nueva solicitud:** formulario con nombre, correo (opcional), VIN, kilometraje, tipo de servicio (`Garantía`, `Mantenimiento`, `Reparación`) y síntoma. Viene relleno con valores de demostración.
- **Mis solicitudes:** lista de los casos del usuario con su avance; cuando el proceso espera la respuesta aparecen los botones "Aceptar" y "Rechazar" de la cotización.

**Quién entra:** solo cuentas del grupo `clientes`. Cuentas de demostración: `cliente1` y `cliente2`, contraseña `demo`. Un empleado o el administrador reciben 403 en toda la API del cliente. Cada cliente ve únicamente sus solicitudes.

| Método y ruta | Quién la llama | Qué hace |
|---|---|---|
| `GET /` | Navegador | Página del portal |
| `GET /api/app/csrf`, `POST /api/app/login`, `POST /api/app/logout`, `GET /api/app/me` | Navegador | Sesión común de los portales (la aporta `cb-comun`) |
| `POST /api/app/client/requests` | Navegador (cliente) | Guarda la solicitud e inicia el proceso; responde `{"businessKey":"PV-XXXXXXXX"}`. Si el motor falla no se guarda y responde 502 |
| `GET /api/app/client/requests` | Navegador (cliente) | Solicitudes del usuario con `variables`, `activities`, `endTime` y `canReply` |
| `POST /api/app/client/requests/{businessKey}/answer` | Navegador (cliente) | `{"action":"aceptar"}` o `{"action":"rechazar"}`; 404 si no es suya, 409 si el proceso no espera respuesta |

Toda escritura exige el encabezado CSRF. Validaciones y formatos de respuesta: sección 5.1 del [contrato de integración](https://github.com/MiguelMorales1125/Corocora-Benz/blob/main/docs/CONTRATO-INTEGRACION.md).

## Datos

Tabla propia `pc_solicitud` (prefijo `pc_`) en la base `corocora`: `business_key` única, usuario dueño, datos del formulario y fecha de creación. Es lo que permite mostrar a cada cliente solo sus casos; el estado del proceso no se copia, se lee del motor. La tabla la crea JPA al arrancar (`ddl-auto: update`). Más detalle en el [modelo de datos](https://github.com/MiguelMorales1125/Corocora-Benz/blob/main/docs/MODELO-DE-DATOS.md).

## Configuración

| Variable de entorno | Para qué | Valor por defecto |
|---|---|---|
| `SERVER_ADDRESS` | Interfaz en la que escucha | `127.0.0.1` |
| `DB_HOST`, `DB_PORT`, `DB_NAME` | Servidor PostgreSQL y base de negocio | `localhost`, `5432`, `corocora` |
| `DB_USER`, `DB_PASSWORD` | Usuario y contraseña de PostgreSQL | `postgres` y la contraseña local definida en `application.yaml` |
| `COROCORA_INTERNAL_TOKEN` | Credencial interna con la que se identifica ante el motor; la misma en los seis sistemas | `cb-local-interno-demo` |
| `ENGINE_REST_URL` | REST de Camunda en el motor | `http://localhost:9000/engine-rest` |

Perfil `h2` (`--spring.profiles.active=h2`): usa una base H2 en memoria (`jdbc:h2:mem:cb-portal-cliente`) que se borra al detener el portal. La cookie de sesión se llama `CBSESSION_CLIENTE`.

## Compilar y ejecutar

Este repositorio no compila clonado por separado: su `pom.xml` hereda del pom padre del hub (`com.corocorabenz:corocora-benz:1.0.0-SNAPSHOT`) y usa la librería `cb-comun`. Se compila desde el hub, con Java 17 o superior (Maven no hace falta: el hub trae su wrapper).

```powershell
git clone https://github.com/MiguelMorales1125/Corocora-Benz.git
cd Corocora-Benz/corocora-benz-embebida
.\mvnw.cmd -pl cb-portal-cliente -am package -DskipTests
java -jar cb-portal-cliente/target/cb-portal-cliente-1.0.0-SNAPSHOT.jar --spring.profiles.active=h2
```

En Linux o macOS se usa `./mvnw` en lugar de `.\mvnw.cmd`; lo demás es igual. Sin `--spring.profiles.active=h2` usa PostgreSQL y necesita la base `corocora` (se crea con `psql -U postgres -f crear-bases.sql`, script del hub).

- **Orden de arranque:** primero el motor (`cb-bpm-engine`, puerto 9000); sin él no se puede iniciar sesión ni crear solicitudes (la API responde 502). Para que una solicitud avance más allá del registro deben estar arriba también Taller-Bodega (9005), al que el motor consulta el VIN y pide la cotización, y CentralSys (9003), donde los empleados atienden las tareas.
- **Comprobar que arrancó:** http://localhost:9001/ debe mostrar el formulario de inicio de sesión; entra con `cliente1` / `demo`.

## Pruebas

```powershell
.\mvnw.cmd -pl cb-portal-cliente -am test
```

Son 19 pruebas en `SolicitudesClienteTest`, con H2 y el motor simulado, sin ningún otro sistema arrancado (`-am` ejecuta además las de `cb-comun`). Cubren:

- Sesión y permisos: 401 sin sesión, 403 para empleados y administrador, y el permiso se revisa antes de validar el cuerpo.
- Creación: la solicitud queda guardada con su dueño, el mensaje `SolicitudServicio` lleva las variables del contrato, los datos inválidos responden 400 y, si el motor falla, no se guarda nada (502).
- Lista: avance del proceso, solo las variables permitidas y sin las solicitudes que el motor ya no tiene.
- Respuesta a la cotización: mensaje correcto, 409 si el proceso no la espera y aislamiento entre clientes.
- La página no expone la URL del motor.

Los casos de prueba de los procesos completos se documentan en [CASOS-DE-PRUEBA.md](https://github.com/MiguelMorales1125/Corocora-Benz/blob/main/docs/CASOS-DE-PRUEBA.md).

## Estructura del código

Paquete base `com.corocorabenz.portalcliente` (`src/main/java`):

- `PortalClienteApplication`: arranque de Spring Boot.
- `solicitud`: `SolicitudController` (API `/api/app/client/requests`), `SolicitudService` (crear, listar, responder), `SeguimientoProceso` (lectura del historial del motor), la entidad `Solicitud` con su repositorio y los registros de entrada y salida (`NuevaSolicitud`, `RespuestaCotizacion`, `SolicitudVista`).
- `web`: `HomeController` (página), `SoloClientesInterceptor` y `ConfiguracionWeb` (403 a quien no sea cliente en `/api/app/client/**`).
- `src/main/resources`: `application.yaml`, `templates/index.html` y `static/app.js`. `auth.js` y `portal.css` vienen de `cb-comun`.
