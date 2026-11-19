package com.corocorabenz.portalcliente.solicitud;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolicitudRepository extends JpaRepository<Solicitud, Long> {

  List<Solicitud> findByUsuarioOrderByCreadaEnDescIdDesc(String usuario);

  /** Buscar siempre con el dueño: una solicitud ajena debe verse igual que una inexistente. */
  Optional<Solicitud> findByBusinessKeyAndUsuario(String businessKey, String usuario);

  boolean existsByBusinessKey(String businessKey);
}
