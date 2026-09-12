package br.com.pratoja.pratoja.web;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LegalController {
    @GetMapping("/termos") String termos() { return "termos"; }
    @GetMapping("/privacidade") String privacidade() { return "privacidade"; }
}
