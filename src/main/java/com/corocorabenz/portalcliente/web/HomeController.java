package com.corocorabenz.portalcliente.web;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
@Controller public class HomeController {
  @Value("${corocora.engine-rest}") private String engineRest;
  @GetMapping("/") public String home(Model model) { model.addAttribute("engineRest", engineRest); return "index"; }
}
