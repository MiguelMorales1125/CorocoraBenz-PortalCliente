package com.corocorabenz.portalcliente.solicitud;

import com.corocorabenz.comun.motor.MotorClient;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Lee del historial del motor el avance del proceso de una solicitud.
 * Solo deja pasar al navegador las variables de la lista blanca del contrato.
 */
@Component
public class SeguimientoProceso {

  static final String PROCESO = "CB_PostventaGarantias";
  static final String MENSAJE_ACEPTADA = "CotizacionAceptada";

  private static final Set<String> VARIABLES_VISIBLES = Set.of(
      "vin",
      "customerName",
      "serviceType",
      "mileageKm",
      "symptom",
      "coverageStatus",
      "dealerId",
      "appointmentDate",
      "serviceOrderId",
      "repairNotes",
      "serviceCost",
      "quoteId",
      "quoteAmount");

  private final MotorClient motor;

  public SeguimientoProceso(MotorClient motor) {
    this.motor = motor;
  }

  /** @return vacío si el motor ya no tiene la instancia de esa solicitud */
  public Optional<SolicitudVista> de(Solicitud solicitud) {
    JsonNode instancia = instanciaRaiz(solicitud.getBusinessKey());
    if (instancia == null) {
      return Optional.empty();
    }
    String id = instancia.path("id").asText();
    String endTime = textoONulo(instancia, "endTime");
    // Un proceso terminado ya no tiene suscripciones: se ahorra la consulta.
    boolean canReply = endTime == null && esperaRespuesta(id);
    SolicitudVista vista = new SolicitudVista(
        id,
        solicitud.getBusinessKey(),
        solicitud.getCreadaEn(),
        endTime,
        canReply,
        variables(id),
        actividades(id));
    return Optional.of(vista);
  }

  private JsonNode instanciaRaiz(String businessKey) {
    // El subproceso del taller hereda la businessKey: se filtra por la definición
    // del proceso principal y por instancias raíz para no confundirlo con él.
    Map<String, Object> parametros = new LinkedHashMap<>();
    parametros.put("processInstanceBusinessKey", businessKey);
    parametros.put("processDefinitionKey", PROCESO);
    parametros.put("rootProcessInstances", true);
    parametros.put("sortBy", "startTime");
    parametros.put("sortOrder", "desc");
    parametros.put("maxResults", 1);
    JsonNode instancias = motor.get("/history/process-instance", parametros);
    if (instancias == null || !instancias.isArray() || instancias.isEmpty()) {
      return null;
    }
    return instancias.get(0);
  }

  private boolean esperaRespuesta(String processInstanceId) {
    Map<String, Object> parametros = new LinkedHashMap<>();
    parametros.put("processInstanceId", processInstanceId);
    parametros.put("eventType", "message");
    parametros.put("eventName", MENSAJE_ACEPTADA);
    JsonNode suscripciones = motor.get("/event-subscription", parametros);
    return suscripciones != null && suscripciones.isArray() && !suscripciones.isEmpty();
  }

  private Map<String, JsonNode> variables(String processInstanceId) {
    Map<String, Object> parametros = new LinkedHashMap<>();
    parametros.put("processInstanceId", processInstanceId);
    parametros.put("deserializeValues", false);
    JsonNode historial = motor.get("/history/variable-instance", parametros);
    Map<String, JsonNode> visibles = new LinkedHashMap<>();
    if (historial == null) {
      return visibles;
    }
    for (JsonNode variable : historial) {
      String nombre = variable.path("name").asText();
      if (!VARIABLES_VISIBLES.contains(nombre) || !variable.hasNonNull("value")) {
        continue;
      }
      // Si una actividad tuvo una copia local con el mismo nombre, manda la del proceso.
      boolean esDelProceso = processInstanceId.equals(variable.path("activityInstanceId").asText());
      if (esDelProceso || !visibles.containsKey(nombre)) {
        visibles.put(nombre, variable.get("value"));
      }
    }
    return visibles;
  }

  private List<SolicitudVista.Actividad> actividades(String processInstanceId) {
    JsonNode historial = motor.get("/history/activity-instance", Map.of("processInstanceId", processInstanceId));
    List<SolicitudVista.Actividad> actividades = new ArrayList<>();
    if (historial == null) {
      return actividades;
    }
    for (JsonNode actividad : historial) {
      String activityId = actividad.path("activityId").asText();
      actividades.add(new SolicitudVista.Actividad(activityId, textoONulo(actividad, "endTime")));
    }
    return actividades;
  }

  private static String textoONulo(JsonNode nodo, String campo) {
    return nodo.hasNonNull(campo) ? nodo.get(campo).asText() : null;
  }
}
