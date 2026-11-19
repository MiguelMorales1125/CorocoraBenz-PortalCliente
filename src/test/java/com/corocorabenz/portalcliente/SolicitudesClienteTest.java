package com.corocorabenz.portalcliente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.corocorabenz.comun.motor.MotorClient;
import com.corocorabenz.comun.motor.MotorException;
import com.corocorabenz.comun.motor.ResultadoMensaje;
import com.corocorabenz.portalcliente.solicitud.Solicitud;
import com.corocorabenz.portalcliente.solicitud.SolicitudRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * API del portal del cliente (sección 5.1 del contrato) vista desde el navegador,
 * con el motor simulado y la base en memoria.
 */
@SpringBootTest
@ActiveProfiles("h2")
@AutoConfigureMockMvc
class SolicitudesClienteTest {

  private static final String RUTA = "/api/app/client/requests";
  private static final String CLAVE = "demo";
  private static final Map<String, List<String>> GRUPOS = Map.of(
      "cliente1", List.of("clientes"),
      "cliente2", List.of("clientes"),
      "asesor1", List.of("asesores-servicio"),
      "demo", List.of("camunda-admin"));

  private final ObjectMapper json = new ObjectMapper();

  @Autowired
  private MockMvc mvc;

  @Autowired
  private SolicitudRepository solicitudes;

  @MockitoBean
  private MotorClient motor;

  @BeforeEach
  void preparar() {
    solicitudes.deleteAll();
    when(motor.post(eq("/identity/verify"), any())).thenAnswer(llamada -> {
      Map<?, ?> credenciales = llamada.getArgument(1);
      boolean correcta = GRUPOS.containsKey(credenciales.get("username"))
          && CLAVE.equals(credenciales.get("password"));
      return json.createObjectNode().put("authenticated", correcta);
    });
    when(motor.get(eq("/group"), any())).thenAnswer(llamada -> {
      Map<?, ?> parametros = llamada.getArgument(1);
      ArrayNode grupos = json.createArrayNode();
      for (String grupo : GRUPOS.getOrDefault(parametros.get("member"), List.of())) {
        grupos.addObject().put("id", grupo);
      }
      return grupos;
    });
    when(motor.enviarMensaje(anyString(), anyString(), anyMap())).thenReturn(ResultadoMensaje.ENTREGADO);
  }

  // ------------------------------------------------------- sesión y permisos

  @Test
  void sinSesionResponde401() throws Exception {
    mvc.perform(get(RUTA))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Inicia sesión"));

    MockHttpSession anonima = new MockHttpSession();
    mvc.perform(conCsrf(post(RUTA), anonima)
            .contentType(MediaType.APPLICATION_JSON)
            .content(solicitudValida().toString()))
        .andExpect(status().isUnauthorized());
    mvc.perform(conCsrf(post(RUTA + "/PV-AAAAAAAA/answer"), anonima)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"action\":\"aceptar\"}"))
        .andExpect(status().isUnauthorized());

    assertThat(solicitudes.count()).isZero();
  }

  @Test
  void unEmpleadoRecibe403EnTodaLaApiDelCliente() throws Exception {
    MockHttpSession asesor = sesionDe("asesor1");

    mvc.perform(get(RUTA).session(asesor))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("Esta operación es solo para clientes"));
    mvc.perform(conCsrf(post(RUTA), asesor)
            .contentType(MediaType.APPLICATION_JSON)
            .content(solicitudValida().toString()))
        .andExpect(status().isForbidden());
    mvc.perform(conCsrf(post(RUTA + "/PV-AAAAAAAA/answer"), asesor)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"action\":\"aceptar\"}"))
        .andExpect(status().isForbidden());

    assertThat(solicitudes.count()).isZero();
    verify(motor, never()).enviarMensaje(anyString(), anyString(), anyMap());
  }

  @Test
  void elPermisoSeRevisaAntesDeValidarElCuerpo() throws Exception {
    mvc.perform(conCsrf(post(RUTA), sesionDe("asesor1"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void elAdministradorNoEsCliente() throws Exception {
    mvc.perform(get(RUTA).session(sesionDe("demo")))
        .andExpect(status().isForbidden());
  }

  // ------------------------------------------------------------------- crear

  @Test
  void unClienteCreaSuSolicitudYQuedaGuardadaConSuDueno() throws Exception {
    String businessKey = crear(sesionDe("cliente1"), solicitudValida());

    assertThat(businessKey).matches("PV-[A-Z0-9]{8}");
    List<Solicitud> guardadas = solicitudes.findAll();
    assertThat(guardadas).hasSize(1);
    Solicitud guardada = guardadas.get(0);
    assertThat(guardada.getBusinessKey()).isEqualTo(businessKey);
    assertThat(guardada.getUsuario()).isEqualTo("cliente1");
    assertThat(guardada.getVin()).isEqualTo("WDB0000000000000B");
    assertThat(guardada.getServiceType()).isEqualTo("Garantía");
    assertThat(guardada.getMileageKm()).isEqualTo(15000L);
    assertThat(guardada.getCustomerEmail()).isEqualTo("cliente1@example.invalid");
    assertThat(guardada.getCreadaEn()).isNotNull();
  }

  @Test
  void alCrearEnviaAlMotorElMensajeConLasVariablesDelContrato() throws Exception {
    String businessKey = crear(sesionDe("cliente1"), solicitudValida());

    Map<String, Object> variables = variablesEnviadas("SolicitudServicio", businessKey);
    Map<String, Object> esperadas = new LinkedHashMap<>();
    esperadas.put("vin", "WDB0000000000000B");
    esperadas.put("customerName", "Cliente autenticado");
    esperadas.put("mileageKm", 15000L);
    esperadas.put("serviceType", "Garantía");
    esperadas.put("symptom", "Ruido en la suspensión");
    esperadas.put("customerUserId", "cliente1");
    esperadas.put("customerEmail", "cliente1@example.invalid");
    assertThat(variables).isEqualTo(esperadas);
  }

  @Test
  void elCorreoEsOpcionalYVacioCuentaComoAusente() throws Exception {
    ObjectNode sinCorreo = solicitudValida();
    sinCorreo.remove("customerEmail");
    String primera = crear(sesionDe("cliente1"), sinCorreo);
    assertThat(variablesEnviadas("SolicitudServicio", primera)).doesNotContainKey("customerEmail");

    ObjectNode correoVacio = solicitudValida().put("customerEmail", "");
    String segunda = crear(sesionDe("cliente1"), correoVacio);
    assertThat(variablesEnviadas("SolicitudServicio", segunda)).doesNotContainKey("customerEmail");
  }

  @Test
  void losDatosInvalidosResponden400YNoGuardanNada() throws Exception {
    MockHttpSession cliente = sesionDe("cliente1");

    rechazar(cliente, solicitudValida().put("vin", "WDB000000000000OB"),
        "El VIN debe tener 17 caracteres en mayúscula, sin I, O ni Q");
    rechazar(cliente, solicitudValida().put("vin", "WDB0000"),
        "El VIN debe tener 17 caracteres en mayúscula, sin I, O ni Q");
    rechazar(cliente, solicitudValida().put("vin", "wdb0000000000000b"),
        "El VIN debe tener 17 caracteres en mayúscula, sin I, O ni Q");
    rechazar(cliente, sin("vin"), "El VIN es obligatorio");
    rechazar(cliente, solicitudValida().put("customerName", " "), "El nombre del cliente es obligatorio");
    rechazar(cliente, solicitudValida().put("customerName", "x".repeat(256)),
        "El nombre del cliente admite máximo 255 caracteres");
    rechazar(cliente, solicitudValida().put("customerEmail", "no-es-correo"),
        "El correo no tiene un formato válido");
    rechazar(cliente, solicitudValida().put("mileageKm", -1), "El kilometraje no puede ser negativo");
    rechazar(cliente, sin("mileageKm"), "El kilometraje es obligatorio");
    rechazar(cliente, solicitudValida().put("serviceType", "Lavado"),
        "El tipo de servicio debe ser Garantía, Mantenimiento o Reparación");
    rechazar(cliente, solicitudValida().put("symptom", ""), "El síntoma o la necesidad es obligatorio");
    rechazar(cliente, solicitudValida().put("symptom", "x".repeat(256)),
        "El síntoma admite máximo 255 caracteres");
    rechazar(cliente, solicitudValida().put("mileageKm", 1500.5),
        "El cuerpo de la petición no es un JSON válido");

    assertThat(solicitudes.count()).isZero();
    verify(motor, never()).enviarMensaje(anyString(), anyString(), anyMap());
  }

  @Test
  void seAceptanLosTresTiposDeServicio() throws Exception {
    MockHttpSession cliente = sesionDe("cliente1");

    for (String tipo : List.of("Garantía", "Mantenimiento", "Reparación")) {
      crear(cliente, solicitudValida().put("serviceType", tipo));
    }

    assertThat(solicitudes.count()).isEqualTo(3);
  }

  @Test
  void siElMotorFallaLaSolicitudNoQuedaGuardadaYResponde502() throws Exception {
    MockHttpSession cliente = sesionDe("cliente1");

    when(motor.enviarMensaje(eq("SolicitudServicio"), anyString(), anyMap()))
        .thenReturn(ResultadoMensaje.MOTOR_NO_DISPONIBLE);
    mvc.perform(conCsrf(post(RUTA), cliente)
            .contentType(MediaType.APPLICATION_JSON)
            .content(solicitudValida().toString()))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.error").value("El motor no responde"));

    when(motor.enviarMensaje(eq("SolicitudServicio"), anyString(), anyMap()))
        .thenReturn(ResultadoMensaje.NO_ESPERADO);
    mvc.perform(conCsrf(post(RUTA), cliente)
            .contentType(MediaType.APPLICATION_JSON)
            .content(solicitudValida().toString()))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.error").isNotEmpty());

    when(motor.enviarMensaje(eq("SolicitudServicio"), anyString(), anyMap()))
        .thenThrow(new MotorException(500, "{\"message\":\"fallo interno\"}"));
    mvc.perform(conCsrf(post(RUTA), cliente)
            .contentType(MediaType.APPLICATION_JSON)
            .content(solicitudValida().toString()))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.error").value("El motor rechazó la operación"));

    assertThat(solicitudes.count()).isZero();
  }

  // ------------------------------------------------------------------ listar

  @Test
  void laListaTraeElAvanceDelProcesoYSoloLasVariablesPermitidas() throws Exception {
    MockHttpSession cliente = sesionDe("cliente1");
    String businessKey = crear(cliente, solicitudValida());
    simularInstancia(businessKey, "inst-1", null);
    ArrayNode variables = json.createArrayNode();
    variables.add(variable("vin", "WDB0000000000000B", "inst-1"));
    variables.add(variable("quoteId", "COT-7", "inst-1"));
    variables.addObject().put("name", "quoteAmount").put("value", 350000).put("activityInstanceId", "inst-1");
    variables.add(variable("customerUserId", "cliente1", "inst-1"));
    variables.add(variable("customerEmail", "cliente1@example.invalid", "inst-1"));
    variables.add(variable("coverageReason", "Fuera de cobertura", "inst-1"));
    variables.addObject().put("name", "dealerId").putNull("value").put("activityInstanceId", "inst-1");
    when(motor.get(eq("/history/variable-instance"), anyMap())).thenReturn(variables);
    ArrayNode actividades = json.createArrayNode();
    actividades.addObject().put("activityId", "registerRequest").put("endTime", "2026-11-17T10:00:00.000+0000");
    actividades.addObject().put("activityId", "quoteResponse").putNull("endTime");
    when(motor.get(eq("/history/activity-instance"), anyMap())).thenReturn(actividades);
    when(motor.get(eq("/event-subscription"), anyMap()))
        .thenReturn(json.createArrayNode().add(json.createObjectNode().put("id", "sub-1")));

    MvcResult resultado = mvc.perform(get(RUTA).session(cliente))
        .andExpect(status().isOk())
        .andReturn();

    JsonNode lista = cuerpo(resultado);
    assertThat(lista).hasSize(1);
    JsonNode caso = lista.get(0);
    assertThat(nombresDe(caso)).containsExactlyInAnyOrder(
        "id", "businessKey", "creadaEn", "endTime", "canReply", "variables", "activities");
    assertThat(caso.get("id").asText()).isEqualTo("inst-1");
    assertThat(caso.get("businessKey").asText()).isEqualTo(businessKey);
    assertThat(caso.get("creadaEn").isTextual()).isTrue();
    assertThat(caso.get("endTime").isNull()).isTrue();
    assertThat(caso.get("canReply").asBoolean()).isTrue();
    assertThat(nombresDe(caso.get("variables"))).containsExactlyInAnyOrder("vin", "quoteId", "quoteAmount");
    assertThat(caso.get("variables").get("quoteAmount").asLong()).isEqualTo(350000L);
    assertThat(caso.get("activities")).hasSize(2);
    assertThat(caso.get("activities").get(0).get("activityId").asText()).isEqualTo("registerRequest");
    assertThat(caso.get("activities").get(0).get("endTime").asText()).isEqualTo("2026-11-17T10:00:00.000+0000");
    assertThat(caso.get("activities").get(1).get("endTime").isNull()).isTrue();

    Map<String, Object> filtro = parametrosDe("/event-subscription");
    assertThat(filtro).containsEntry("processInstanceId", "inst-1");
    assertThat(filtro).containsEntry("eventType", "message");
    assertThat(filtro).containsEntry("eventName", "CotizacionAceptada");
    Map<String, Object> busqueda = parametrosDe("/history/process-instance");
    assertThat(busqueda).containsEntry("processInstanceBusinessKey", businessKey);
    assertThat(busqueda).containsEntry("processDefinitionKey", "CB_PostventaGarantias");
  }

  @Test
  void unProcesoTerminadoNoPuedeResponderseNiConsultaSuscripciones() throws Exception {
    MockHttpSession cliente = sesionDe("cliente1");
    String businessKey = crear(cliente, solicitudValida());
    simularInstancia(businessKey, "inst-2", "2026-11-17T12:00:00.000+0000");

    mvc.perform(get(RUTA).session(cliente))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].endTime").value("2026-11-17T12:00:00.000+0000"))
        .andExpect(jsonPath("$[0].canReply").value(false))
        .andExpect(jsonPath("$[0].variables").isMap())
        .andExpect(jsonPath("$[0].activities").isArray());

    verify(motor, never()).get(eq("/event-subscription"), anyMap());
  }

  @Test
  void lasSolicitudesQueElMotorYaNoTieneNoSeListan() throws Exception {
    MockHttpSession cliente = sesionDe("cliente1");
    crear(cliente, solicitudValida());
    when(motor.get(eq("/history/process-instance"), anyMap())).thenReturn(json.createArrayNode());

    mvc.perform(get(RUTA).session(cliente))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void siElMotorNoRespondeAlListarEs502() throws Exception {
    MockHttpSession cliente = sesionDe("cliente1");
    crear(cliente, solicitudValida());
    when(motor.get(eq("/history/process-instance"), anyMap()))
        .thenThrow(new MotorException(500, "fallo"));

    mvc.perform(get(RUTA).session(cliente))
        .andExpect(status().isBadGateway());
  }

  // ----------------------------------------------------- aislamiento y answer

  @Test
  void otroClienteNoVeLaSolicitudNiPuedeResponderla() throws Exception {
    String businessKey = crear(sesionDe("cliente1"), solicitudValida());
    simularInstancia(businessKey, "inst-3", null);
    MockHttpSession cliente2 = sesionDe("cliente2");

    mvc.perform(get(RUTA).session(cliente2))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
    verify(motor, never()).get(eq("/history/process-instance"), anyMap());

    mvc.perform(conCsrf(post(RUTA + "/" + businessKey + "/answer"), cliente2)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"action\":\"aceptar\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("No se encontró la solicitud"));
    verify(motor, never()).enviarMensaje(eq("CotizacionAceptada"), anyString(), anyMap());
  }

  @Test
  void elDuenoAceptaORechazaYSeEnviaElMensajeQueCorresponde() throws Exception {
    MockHttpSession cliente = sesionDe("cliente1");
    String businessKey = crear(cliente, solicitudValida());

    mvc.perform(conCsrf(post(RUTA + "/" + businessKey + "/answer"), cliente)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"action\":\"aceptar\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.businessKey").value(businessKey))
        .andExpect(jsonPath("$.action").value("aceptar"));
    verify(motor).enviarMensaje(eq("CotizacionAceptada"), eq(businessKey), anyMap());

    mvc.perform(conCsrf(post(RUTA + "/" + businessKey + "/answer"), cliente)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"action\":\"rechazar\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.action").value("rechazar"));
    verify(motor).enviarMensaje(eq("CotizacionRechazada"), eq(businessKey), anyMap());
  }

  @Test
  void siElProcesoNoEsperaRespuestaEs409() throws Exception {
    MockHttpSession cliente = sesionDe("cliente1");
    String businessKey = crear(cliente, solicitudValida());
    when(motor.enviarMensaje(eq("CotizacionAceptada"), anyString(), anyMap()))
        .thenReturn(ResultadoMensaje.NO_ESPERADO);

    mvc.perform(conCsrf(post(RUTA + "/" + businessKey + "/answer"), cliente)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"action\":\"aceptar\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").isNotEmpty());
  }

  @Test
  void unaAccionDesconocidaEs400YUnaSolicitudInexistente404() throws Exception {
    MockHttpSession cliente = sesionDe("cliente1");
    String businessKey = crear(cliente, solicitudValida());

    mvc.perform(conCsrf(post(RUTA + "/" + businessKey + "/answer"), cliente)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"action\":\"posponer\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("La acción debe ser aceptar o rechazar"));
    mvc.perform(conCsrf(post(RUTA + "/PV-NOEXISTE/answer"), cliente)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"action\":\"aceptar\"}"))
        .andExpect(status().isNotFound());
  }

  // ------------------------------------------------------------------ página

  @Test
  void laPaginaNoExponeElMotorNiElProxyDeCotizaciones() throws Exception {
    MvcResult pagina = mvc.perform(get("/"))
        .andExpect(status().isOk())
        .andReturn();
    String html = pagina.getResponse().getContentAsString(StandardCharsets.UTF_8);
    assertThat(html)
        .contains("/portal.css", "/auth.js", "/app.js", "CB_PORTAL", "CB0TEST0000000001", "WDB0000000000000B")
        .doesNotContain("engine-rest")
        .doesNotContain("9000");

    MvcResult script = mvc.perform(get("/app.js"))
        .andExpect(status().isOk())
        .andReturn();
    assertThat(script.getResponse().getContentAsString(StandardCharsets.UTF_8))
        .contains("cb-auth-ready")
        .doesNotContain("engine-rest")
        .doesNotContain("X-CB-Internal");

    mvc.perform(get("/auth.js"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/cotizaciones").param("businessKey", "PV-AAAAAAAA"))
        .andExpect(status().isNotFound());
  }

  // ------------------------------------------------------------------ ayudas

  private ObjectNode solicitudValida() {
    return json.createObjectNode()
        .put("customerName", "Cliente autenticado")
        .put("customerEmail", "cliente1@example.invalid")
        .put("vin", "WDB0000000000000B")
        .put("mileageKm", 15000)
        .put("serviceType", "Garantía")
        .put("symptom", "Ruido en la suspensión");
  }

  private ObjectNode sin(String campo) {
    ObjectNode solicitud = solicitudValida();
    solicitud.remove(campo);
    return solicitud;
  }

  private ObjectNode variable(String nombre, String valor, String actividad) {
    return json.createObjectNode()
        .put("name", nombre)
        .put("value", valor)
        .put("activityInstanceId", actividad);
  }

  private String crear(MockHttpSession sesion, ObjectNode solicitud) throws Exception {
    MvcResult resultado = mvc.perform(conCsrf(post(RUTA), sesion)
            .contentType(MediaType.APPLICATION_JSON)
            .content(solicitud.toString().getBytes(StandardCharsets.UTF_8)))
        .andExpect(status().isOk())
        .andReturn();
    return cuerpo(resultado).get("businessKey").asText();
  }

  private void rechazar(MockHttpSession sesion, ObjectNode solicitud, String mensaje) throws Exception {
    mvc.perform(conCsrf(post(RUTA), sesion)
            .contentType(MediaType.APPLICATION_JSON)
            .content(solicitud.toString().getBytes(StandardCharsets.UTF_8)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value(mensaje));
  }

  /** El motor simulado responde como si tuviera una instancia raíz, sin variables ni actividades. */
  private void simularInstancia(String businessKey, String id, String endTime) {
    ObjectNode instancia = json.createObjectNode()
        .put("id", id)
        .put("businessKey", businessKey)
        .put("endTime", endTime);
    when(motor.get(eq("/history/process-instance"), anyMap()))
        .thenReturn(json.createArrayNode().add(instancia));
    when(motor.get(eq("/history/variable-instance"), anyMap())).thenReturn(json.createArrayNode());
    when(motor.get(eq("/history/activity-instance"), anyMap())).thenReturn(json.createArrayNode());
    when(motor.get(eq("/event-subscription"), anyMap())).thenReturn(json.createArrayNode());
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> variablesEnviadas(String mensaje, String businessKey) {
    ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.forClass(Map.class);
    verify(motor).enviarMensaje(eq(mensaje), eq(businessKey), variables.capture());
    return variables.getValue();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> parametrosDe(String ruta) {
    ArgumentCaptor<Map<String, Object>> parametros = ArgumentCaptor.forClass(Map.class);
    verify(motor).get(eq(ruta), parametros.capture());
    return parametros.getValue();
  }

  private JsonNode cuerpo(MvcResult resultado) throws Exception {
    return json.readTree(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8));
  }

  private static List<String> nombresDe(JsonNode objeto) {
    List<String> nombres = new java.util.ArrayList<>();
    objeto.fieldNames().forEachRemaining(nombres::add);
    return nombres;
  }

  private JsonNode pedirCsrf(MockHttpSession sesion) throws Exception {
    MvcResult resultado = mvc.perform(get("/api/app/csrf").session(sesion))
        .andExpect(status().isOk())
        .andReturn();
    return json.readTree(resultado.getResponse().getContentAsString());
  }

  /** Igual que auth.js: pide un token nuevo justo antes de cada escritura. */
  private MockHttpServletRequestBuilder conCsrf(MockHttpServletRequestBuilder peticion, MockHttpSession sesion)
      throws Exception {
    JsonNode csrf = pedirCsrf(sesion);
    return peticion
        .session(sesion)
        .header(csrf.get("header").asText(), csrf.get("token").asText());
  }

  private MockHttpSession sesionDe(String usuario) throws Exception {
    MockHttpSession sesion = new MockHttpSession();
    mvc.perform(conCsrf(post("/api/app/login"), sesion)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("username", usuario)
            .param("password", CLAVE))
        .andExpect(status().isNoContent());
    return sesion;
  }
}
