package com.corocorabenz.portalcliente.solicitud;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Lo que el cliente ve de una de sus solicitudes: la solicitud guardada en el portal
 * más el avance de su proceso en el motor.
 *
 * @param id id de la instancia raíz de CB_PostventaGarantias en el historial del motor
 * @param endTime cuándo terminó el proceso, o {@code null} si sigue en curso
 * @param canReply el proceso espera que el cliente acepte o rechace la cotización
 * @param variables solo las variables de la lista blanca del contrato
 * @param activities actividades por las que ha pasado la instancia raíz
 */
public record SolicitudVista(
    String id,
    String businessKey,
    Instant creadaEn,
    String endTime,
    boolean canReply,
    Map<String, JsonNode> variables,
    List<Actividad> activities) {

  /** @param endTime {@code null} mientras la actividad siga activa */
  public record Actividad(String activityId, String endTime) {
  }
}
