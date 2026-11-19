package com.corocorabenz.portalcliente.solicitud;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Cuerpo de {@code POST /api/app/client/requests/{businessKey}/answer}. */
public record RespuestaCotizacion(

    @NotNull(message = "La acción es obligatoria")
    @Pattern(regexp = "aceptar|rechazar", message = "La acción debe ser aceptar o rechazar")
    String action) {

  public static final String ACEPTAR = "aceptar";

  public boolean acepta() {
    return ACEPTAR.equals(action);
  }
}
