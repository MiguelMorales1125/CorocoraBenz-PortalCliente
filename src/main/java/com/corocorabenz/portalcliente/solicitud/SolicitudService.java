package com.corocorabenz.portalcliente.solicitud;

import com.corocorabenz.comun.motor.MotorClient;
import com.corocorabenz.comun.motor.ResultadoMensaje;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Solicitudes de servicio del cliente: crearlas, listarlas con su avance y responder la cotización. */
@Service
public class SolicitudService {

  static final String MENSAJE_SOLICITUD = "SolicitudServicio";
  static final String MENSAJE_RECHAZADA = "CotizacionRechazada";

  private static final String PREFIJO = "PV-";
  private static final int LARGO_CLAVE = 8;
  // Sin 0, O, 1 ni I: la clave se puede leer y dictar sin confusiones.
  private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final int INTENTOS_CLAVE = 5;

  private final SolicitudRepository solicitudes;
  private final SeguimientoProceso seguimiento;
  private final MotorClient motor;
  private final SecureRandom azar = new SecureRandom();

  public SolicitudService(SolicitudRepository solicitudes, SeguimientoProceso seguimiento, MotorClient motor) {
    this.solicitudes = solicitudes;
    this.seguimiento = seguimiento;
    this.motor = motor;
  }

  /**
   * Guarda la solicitud y arranca su proceso en el motor.
   *
   * <p>Es transaccional a propósito: se guarda primero (así la businessKey queda reservada
   * con su dueño) y, si el motor no arranca el proceso, la excepción deshace el guardado.
   * No puede quedar una solicitud sin proceso.
   */
  @Transactional
  public Solicitud crear(String usuario, NuevaSolicitud datos) {
    Solicitud solicitud = new Solicitud();
    solicitud.setBusinessKey(nuevaBusinessKey());
    solicitud.setUsuario(usuario);
    solicitud.setCustomerName(datos.customerName().strip());
    solicitud.setCustomerEmail(correoONulo(datos.customerEmail()));
    solicitud.setVin(datos.vin());
    solicitud.setServiceType(datos.serviceType());
    solicitud.setMileageKm(datos.mileageKm());
    solicitud.setSymptom(datos.symptom().strip());
    solicitud.setCreadaEn(Instant.now());
    solicitudes.saveAndFlush(solicitud);

    ResultadoMensaje resultado = motor.enviarMensaje(
        MENSAJE_SOLICITUD,
        solicitud.getBusinessKey(),
        variablesDeInicio(solicitud));
    if (resultado == ResultadoMensaje.MOTOR_NO_DISPONIBLE) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "El motor no responde");
    }
    if (resultado != ResultadoMensaje.ENTREGADO) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY,
          "El motor no pudo iniciar el proceso de la solicitud");
    }
    return solicitud;
  }

  /** Las solicitudes del usuario que todavía existen en el motor, de la más reciente a la más antigua. */
  public List<SolicitudVista> listar(String usuario) {
    List<SolicitudVista> vistas = new ArrayList<>();
    for (Solicitud solicitud : solicitudes.findByUsuarioOrderByCreadaEnDescIdDesc(usuario)) {
      seguimiento.de(solicitud).ifPresent(vistas::add);
    }
    return vistas;
  }

  /** Acepta o rechaza la cotización de una solicitud propia. */
  public void responder(String usuario, String businessKey, RespuestaCotizacion respuesta) {
    Solicitud solicitud = solicitudes.findByBusinessKeyAndUsuario(businessKey, usuario)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró la solicitud"));
    String mensaje = respuesta.acepta() ? SeguimientoProceso.MENSAJE_ACEPTADA : MENSAJE_RECHAZADA;
    ResultadoMensaje resultado = motor.enviarMensaje(mensaje, solicitud.getBusinessKey(), Map.of());
    if (resultado == ResultadoMensaje.MOTOR_NO_DISPONIBLE) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "El motor no responde");
    }
    if (resultado != ResultadoMensaje.ENTREGADO) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "La solicitud no tiene una cotización pendiente de respuesta");
    }
  }

  private static Map<String, Object> variablesDeInicio(Solicitud solicitud) {
    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("vin", solicitud.getVin());
    variables.put("customerName", solicitud.getCustomerName());
    variables.put("mileageKm", solicitud.getMileageKm());
    variables.put("serviceType", solicitud.getServiceType());
    variables.put("symptom", solicitud.getSymptom());
    variables.put("customerUserId", solicitud.getUsuario());
    if (solicitud.getCustomerEmail() != null) {
      variables.put("customerEmail", solicitud.getCustomerEmail());
    }
    return variables;
  }

  // El formulario envía el correo vacío cuando no se llena: cuenta como "sin correo".
  private static String correoONulo(String correo) {
    if (correo == null || correo.isBlank()) {
      return null;
    }
    return correo.strip();
  }

  private String nuevaBusinessKey() {
    for (int intento = 0; intento < INTENTOS_CLAVE; intento++) {
      String clave = PREFIJO + claveAlAzar();
      if (!solicitudes.existsByBusinessKey(clave)) {
        return clave;
      }
    }
    throw new IllegalStateException("No fue posible generar una businessKey libre");
  }

  private String claveAlAzar() {
    StringBuilder clave = new StringBuilder(LARGO_CLAVE);
    for (int posicion = 0; posicion < LARGO_CLAVE; posicion++) {
      clave.append(ALFABETO.charAt(azar.nextInt(ALFABETO.length())));
    }
    return clave.toString();
  }
}
