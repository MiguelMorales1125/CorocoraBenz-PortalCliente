package com.corocorabenz.portalcliente.solicitud;

import com.corocorabenz.comun.portal.UsuarioPortal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API del portal para el navegador (sección 5.1 del contrato).
 * El perfil de cliente lo exige {@code SoloClientesInterceptor} en todo /api/app/client/**.
 */
@RestController
@RequestMapping("/api/app/client/requests")
public class SolicitudController {

  public record SolicitudCreada(String businessKey) {
  }

  public record RespuestaEnviada(String businessKey, String action) {
  }

  private final SolicitudService servicio;

  public SolicitudController(SolicitudService servicio) {
    this.servicio = servicio;
  }

  @PostMapping
  public SolicitudCreada crear(
      @AuthenticationPrincipal UsuarioPortal usuario,
      @Valid @RequestBody NuevaSolicitud datos) {
    Solicitud solicitud = servicio.crear(usuario.user(), datos);
    return new SolicitudCreada(solicitud.getBusinessKey());
  }

  @GetMapping
  public List<SolicitudVista> listar(@AuthenticationPrincipal UsuarioPortal usuario) {
    return servicio.listar(usuario.user());
  }

  @PostMapping("/{businessKey}/answer")
  public RespuestaEnviada responder(
      @AuthenticationPrincipal UsuarioPortal usuario,
      @PathVariable String businessKey,
      @Valid @RequestBody RespuestaCotizacion respuesta) {
    servicio.responder(usuario.user(), businessKey, respuesta);
    return new RespuestaEnviada(businessKey, respuesta.action());
  }
}
