package com.corocorabenz.portalcliente.web;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
@RestController @RequestMapping("/api/cotizaciones")
public class CotizacionesController {
  private final RestClient client;
  public CotizacionesController(@Value("${corocora.taller-bodega:http://localhost:9005}") String url) {
    client = RestClient.builder().baseUrl(url).defaultHeader("X-CB-Internal", System.getenv().getOrDefault("COROCORA_INTERNAL_TOKEN", "cb-local-interno-demo")).build();
  }
  @GetMapping public Object listar(@RequestParam String businessKey) {
    return client.get().uri(uri -> uri.path("/api/cotizaciones").queryParam("businessKey", businessKey).build()).retrieve().body(Object.class);
  }
}
