/*
 * Página del portal del cliente: crear una solicitud de servicio, seguir su estado
 * y aceptar o rechazar la cotización.
 *
 * Solo habla con /api/app de este portal por medio de CB (auth.js). Aquí no hay
 * ninguna URL del motor: el avance de cada caso lo arma el servidor.
 */
(function () {
  "use strict";

  const mensaje = document.querySelector("#message");
  const formulario = document.querySelector("#request");
  const lista = document.querySelector("#cases");
  const sinSolicitudes = document.querySelector("#empty");
  const botonActualizar = document.querySelector("#refresh");

  const COBERTURAS = {
    CAMPANA_ACTIVA: "campaña activa",
    CUBIERTO_POR_GARANTIA: "cubierto por garantía",
    REQUIERE_COTIZACION: "requiere cotización"
  };

  const pesos = new Intl.NumberFormat("es-CO", {
    style: "currency",
    currency: "COP",
    maximumFractionDigits: 0
  });
  const fechaYHora = new Intl.DateTimeFormat("es-CO", {
    dateStyle: "medium",
    timeStyle: "short"
  });

  // Cada carga recibe un número: si llega una respuesta vieja después de una nueva, se descarta.
  let cargaVigente = 0;

  // ---------------------------------------------------------------- mensajes

  function avisar(texto, tipo) {
    mensaje.classList.remove("ok", "error");
    if (tipo) {
      mensaje.classList.add(tipo);
    }
    mensaje.textContent = texto;
    // El mensaje está arriba de la página y los botones que lo provocan, más abajo.
    if (texto) {
      mensaje.scrollIntoView({ block: "nearest" });
    }
  }

  function avisarError(fallo) {
    const texto = fallo && fallo.message ? fallo.message : "No fue posible completar la operación.";
    avisar(texto, "error");
  }

  // ------------------------------------------------------------------ estado

  function estadoDe(solicitud) {
    const actividades = solicitud.activities || [];

    function terminada(id) {
      return actividades.some(function (actividad) {
        return actividad.activityId === id && actividad.endTime;
      });
    }

    function activa(id) {
      return actividades.some(function (actividad) {
        return actividad.activityId === id && !actividad.endTime;
      });
    }

    // El orden importa: primero los finales, luego lo más avanzado del proceso.
    if (terminada("closedEnd")) {
      return { texto: "Vehículo entregado", clase: "ok" };
    }
    if (terminada("quoteRejected")) {
      return { texto: "Cotización rechazada; caso finalizado", clase: "error" };
    }
    if (terminada("quoteExpired")) {
      return { texto: "Cotización vencida; caso finalizado", clase: "error" };
    }
    if (solicitud.endTime) {
      return { texto: "Caso finalizado", clase: "" };
    }
    if (activa("deliverVehicle")) {
      return { texto: "Pendiente de entrega", clase: "info" };
    }
    if (activa("attendWorkshop")) {
      return { texto: "En atención de taller", clase: "info" };
    }
    if (activa("waitParts")) {
      return { texto: "Esperando aviso de repuestos disponibles", clase: "pendiente" };
    }
    if (activa("scheduleWorkshop")) {
      return { texto: "Pendiente de agendar cita", clase: "pendiente" };
    }
    if (solicitud.canReply) {
      return { texto: "Esperando tu respuesta a la cotización", clase: "pendiente" };
    }
    if (activa("reviewWarranty")) {
      return { texto: "En revisión de garantía", clase: "info" };
    }
    if (activa("registerRequest")) {
      return { texto: "Solicitud recibida", clase: "info" };
    }
    return { texto: "Solicitud en curso", clase: "" };
  }

  function entregado(solicitud) {
    return (solicitud.activities || []).some(function (actividad) {
      return actividad.activityId === "closedEnd" && actividad.endTime;
    });
  }

  // ----------------------------------------------------------------- pintado

  function crear(etiqueta, clase, texto) {
    const elemento = document.createElement(etiqueta);
    if (clase) {
      elemento.className = clase;
    }
    if (texto !== undefined) {
      elemento.textContent = texto;
    }
    return elemento;
  }

  function dato(nombre, valor) {
    const parrafo = crear("p");
    parrafo.append(crear("strong", null, nombre + ": "), String(valor));
    return parrafo;
  }

  function fechaLegible(instante) {
    const fecha = new Date(instante);
    if (Number.isNaN(fecha.getTime())) {
      return String(instante);
    }
    return fechaYHora.format(fecha);
  }

  /**
   * La cobertura que ve el cliente. Si hay cotización aunque la regla dio garantía o campaña,
   * es porque el analista no aprobó la garantía: en ese camino el proceso no cambia coverageStatus.
   */
  function coberturaLegible(valores) {
    const garantiaNegada = Boolean(valores.quoteId) && valores.coverageStatus !== "REQUIERE_COTIZACION";
    if (garantiaNegada) {
      return "garantía no aprobada; requiere cotización";
    }
    return COBERTURAS[valores.coverageStatus] || valores.coverageStatus;
  }

  function detalles(solicitud) {
    const valores = solicitud.variables || {};
    const parrafos = [];

    const vehiculo = [valores.vin, valores.serviceType].filter(Boolean).join(" · ");
    if (vehiculo) {
      parrafos.push(dato("Vehículo", vehiculo));
    }
    if (valores.symptom) {
      parrafos.push(dato("Síntoma o necesidad", valores.symptom));
    }
    if (solicitud.creadaEn) {
      parrafos.push(dato("Creada", fechaLegible(solicitud.creadaEn)));
    }
    if (valores.coverageStatus) {
      parrafos.push(dato("Cobertura", coberturaLegible(valores)));
    }
    if (valores.quoteId) {
      const valor = pesos.format(Number(valores.quoteAmount));
      parrafos.push(dato("Cotización " + valores.quoteId, valor + " (simulada)"));
    }
    if (valores.appointmentDate) {
      parrafos.push(dato("Cita", valores.appointmentDate));
    }
    if (valores.serviceOrderId) {
      parrafos.push(dato("Orden de taller", valores.serviceOrderId));
    }
    // Las notas del taller solo se muestran con el caso cerrado: antes pueden cambiar.
    if (valores.repairNotes && entregado(solicitud)) {
      parrafos.push(dato("Trabajo realizado", valores.repairNotes));
    }
    return parrafos;
  }

  function botonesDeRespuesta(solicitud) {
    const acciones = crear("div", "acciones");
    const aceptar = crear("button", null, "Aceptar");
    const rechazar = crear("button", "peligro", "Rechazar");
    aceptar.type = "button";
    rechazar.type = "button";
    aceptar.setAttribute("aria-label", "Aceptar la cotización de " + solicitud.businessKey);
    rechazar.setAttribute("aria-label", "Rechazar la cotización de " + solicitud.businessKey);

    async function responder(accion) {
      aceptar.disabled = true;
      rechazar.disabled = true;
      try {
        const ruta = "/client/requests/" + encodeURIComponent(solicitud.businessKey) + "/answer";
        await CB.post(ruta, { action: accion });
        const hecho = accion === "aceptar" ? "Cotización aceptada" : "Cotización rechazada";
        avisar(hecho + " para " + solicitud.businessKey + ".", "ok");
      } catch (fallo) {
        avisarError(fallo);
      }
      // También tras un error: si el caso ya no espera respuesta, la lista lo mostrará.
      await cargar({ conservarMensaje: true });
    }

    aceptar.addEventListener("click", function () {
      responder("aceptar");
    });
    rechazar.addEventListener("click", function () {
      const pregunta = "¿Rechazar la cotización de " + solicitud.businessKey
        + "? El caso se cerrará sin reparar el vehículo.";
      if (window.confirm(pregunta)) {
        responder("rechazar");
      }
    });

    acciones.append(aceptar, rechazar);
    return acciones;
  }

  function pintarSolicitud(solicitud) {
    const estado = estadoDe(solicitud);
    const elemento = crear("li");

    const titulo = crear("h3", null, solicitud.businessKey + " ");
    const clases = estado.clase ? "insignia " + estado.clase : "insignia";
    titulo.append(crear("span", clases, estado.texto));
    elemento.append(titulo);

    for (const parrafo of detalles(solicitud)) {
      elemento.append(parrafo);
    }
    if (solicitud.canReply) {
      elemento.append(botonesDeRespuesta(solicitud));
    }
    return elemento;
  }

  function pintarLista(solicitudes) {
    lista.replaceChildren();
    for (const solicitud of solicitudes) {
      lista.append(pintarSolicitud(solicitud));
    }
    sinSolicitudes.hidden = solicitudes.length > 0;
  }

  // ------------------------------------------------------------------- datos

  async function cargar(opciones) {
    const conservarMensaje = opciones && opciones.conservarMensaje;
    const estaCarga = ++cargaVigente;
    botonActualizar.disabled = true;
    lista.setAttribute("aria-busy", "true");
    try {
      const solicitudes = await CB.request("/client/requests");
      if (estaCarga !== cargaVigente) {
        return;
      }
      pintarLista(solicitudes || []);
      if (!conservarMensaje) {
        avisar("Solicitudes actualizadas.", "ok");
      }
    } catch (fallo) {
      if (estaCarga === cargaVigente) {
        avisarError(fallo);
      }
    } finally {
      if (estaCarga === cargaVigente) {
        botonActualizar.disabled = false;
        lista.removeAttribute("aria-busy");
      }
    }
  }

  function datosDelFormulario() {
    const campos = new FormData(formulario);
    const datos = {
      customerName: String(campos.get("customerName")).trim(),
      // Se acepta escrito en minúscula; el servidor lo exige en mayúscula.
      vin: String(campos.get("vin")).trim().toUpperCase(),
      mileageKm: Number(campos.get("mileageKm")),
      serviceType: campos.get("serviceType"),
      symptom: String(campos.get("symptom")).trim()
    };
    const correo = String(campos.get("customerEmail")).trim();
    if (correo) {
      datos.customerEmail = correo;
    }
    return datos;
  }

  async function enviarSolicitud(evento) {
    evento.preventDefault();
    const boton = formulario.querySelector("button[type=submit]");
    boton.disabled = true;
    try {
      const creada = await CB.post("/client/requests", datosDelFormulario());
      formulario.reset();
      avisar("Solicitud enviada. Tu número de caso es " + creada.businessKey + ".", "ok");
      await cargar({ conservarMensaje: true });
    } catch (fallo) {
      avisarError(fallo);
    } finally {
      boton.disabled = false;
    }
  }

  // ------------------------------------------------------------------ inicio

  formulario.addEventListener("submit", enviarSolicitud);

  botonActualizar.addEventListener("click", function () {
    cargar();
  });

  // Puede dispararse más de una vez (al volver a entrar tras perder la sesión), quizá con
  // otra cuenta: se limpia lo que hubiera en pantalla antes de cargar lo de quien entró.
  window.addEventListener("cb-auth-ready", function () {
    avisar("");
    lista.replaceChildren();
    sinSolicitudes.hidden = true;
    cargar({ conservarMensaje: true });
  });
})();
