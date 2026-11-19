package com.corocorabenz.portalcliente.solicitud;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Solicitud de servicio creada por un cliente. El proceso vive en el motor;
 * aquí se guarda quién es el dueño de cada businessKey, que es lo que permite
 * mostrarle a cada cliente solo sus casos.
 */
@Entity
@Table(name = "pc_solicitud", indexes = @Index(name = "ix_pc_solicitud_usuario", columnList = "usuario"))
public class Solicitud {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 40)
  private String businessKey;

  /** Usuario de Camunda dueño de la solicitud. */
  @Column(nullable = false)
  private String usuario;

  @Column(nullable = false)
  private String customerName;

  private String customerEmail;

  @Column(nullable = false, length = 17)
  private String vin;

  @Column(nullable = false, length = 40)
  private String serviceType;

  @Column(nullable = false)
  private Long mileageKm;

  @Column(nullable = false)
  private String symptom;

  @Column(nullable = false)
  private Instant creadaEn;

  public Long getId() {
    return id;
  }

  public String getBusinessKey() {
    return businessKey;
  }

  public void setBusinessKey(String businessKey) {
    this.businessKey = businessKey;
  }

  public String getUsuario() {
    return usuario;
  }

  public void setUsuario(String usuario) {
    this.usuario = usuario;
  }

  public String getCustomerName() {
    return customerName;
  }

  public void setCustomerName(String customerName) {
    this.customerName = customerName;
  }

  public String getCustomerEmail() {
    return customerEmail;
  }

  public void setCustomerEmail(String customerEmail) {
    this.customerEmail = customerEmail;
  }

  public String getVin() {
    return vin;
  }

  public void setVin(String vin) {
    this.vin = vin;
  }

  public String getServiceType() {
    return serviceType;
  }

  public void setServiceType(String serviceType) {
    this.serviceType = serviceType;
  }

  public Long getMileageKm() {
    return mileageKm;
  }

  public void setMileageKm(Long mileageKm) {
    this.mileageKm = mileageKm;
  }

  public String getSymptom() {
    return symptom;
  }

  public void setSymptom(String symptom) {
    this.symptom = symptom;
  }

  public Instant getCreadaEn() {
    return creadaEn;
  }

  public void setCreadaEn(Instant creadaEn) {
    this.creadaEn = creadaEn;
  }
}
