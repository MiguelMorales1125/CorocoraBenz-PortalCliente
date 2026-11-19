package com.corocorabenz.portalcliente.solicitud;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /api/app/client/requests} (sección 5.1 del contrato). */
public record NuevaSolicitud(

    @NotBlank(message = "El nombre del cliente es obligatorio")
    @Size(max = 255, message = "El nombre del cliente admite máximo 255 caracteres")
    String customerName,

    @Email(message = "El correo no tiene un formato válido")
    @Size(max = 255, message = "El correo admite máximo 255 caracteres")
    String customerEmail,

    @NotNull(message = "El VIN es obligatorio")
    @Pattern(
        regexp = "[A-HJ-NPR-Z0-9]{17}",
        message = "El VIN debe tener 17 caracteres en mayúscula, sin I, O ni Q")
    String vin,

    @NotNull(message = "El kilometraje es obligatorio")
    @PositiveOrZero(message = "El kilometraje no puede ser negativo")
    Long mileageKm,

    @NotNull(message = "El tipo de servicio es obligatorio")
    @Pattern(
        regexp = "Garantía|Mantenimiento|Reparación",
        message = "El tipo de servicio debe ser Garantía, Mantenimiento o Reparación")
    String serviceType,

    @NotBlank(message = "El síntoma o la necesidad es obligatorio")
    @Size(max = 255, message = "El síntoma admite máximo 255 caracteres")
    String symptom) {
}
