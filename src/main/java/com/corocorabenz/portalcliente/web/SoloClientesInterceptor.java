package com.corocorabenz.portalcliente.web;

import com.corocorabenz.comun.portal.Permisos;
import com.corocorabenz.comun.portal.UsuarioPortal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Deja pasar a la API del cliente solo al perfil cliente (cualquier otro: 403).
 *
 * <p>Es un interceptor y no una comprobación dentro de cada método porque así se
 * ejecuta antes de leer y validar el cuerpo: un empleado recibe 403 aunque envíe
 * datos inválidos, y no un 400 que le describa la API.
 */
public class SoloClientesInterceptor implements HandlerInterceptor {

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    Permisos.exigirCliente(usuarioDeLaSesion());
    return true;
  }

  private static UsuarioPortal usuarioDeLaSesion() {
    Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
    if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioPortal usuario) {
      return usuario;
    }
    return null;
  }
}
