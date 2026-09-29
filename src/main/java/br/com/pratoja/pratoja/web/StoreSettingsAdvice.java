package br.com.pratoja.pratoja.web;

import br.com.pratoja.pratoja.domain.StoreSettings;
import br.com.pratoja.pratoja.service.StoreSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Expõe as configurações da loja como atributo global "store" em todos os templates Thymeleaf. */
@ControllerAdvice @RequiredArgsConstructor
public class StoreSettingsAdvice {
    private final StoreSettingsService settingsService;

    @ModelAttribute("store") public StoreSettings store() {
        return settingsService.getSettings();
    }
}
