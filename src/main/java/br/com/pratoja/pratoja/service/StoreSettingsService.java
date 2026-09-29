package br.com.pratoja.pratoja.service;

import br.com.pratoja.pratoja.domain.StoreSettings;
import br.com.pratoja.pratoja.repository.StoreSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service @RequiredArgsConstructor
public class StoreSettingsService {
    private final StoreSettingsRepository repository;
    @Value("${pratoja.delivery-fee:6.00}") private BigDecimal defaultDeliveryFee;

    /** Retorna a linha única de configurações, criando-a com valores padrão caso não exista (idempotente). */
    @Transactional public StoreSettings getSettings() {
        return repository.findById(StoreSettings.SINGLETON_ID).orElseGet(this::createDefaults);
    }

    @Transactional public StoreSettings save(StoreSettings settings) {
        settings.setId(StoreSettings.SINGLETON_ID);
        settings.setUpdatedAt(LocalDateTime.now());
        return repository.save(settings);
    }

    private StoreSettings createDefaults() {
        StoreSettings s = new StoreSettings();
        s.setId(StoreSettings.SINGLETON_ID);
        s.setName("Restaurante");
        s.setSlogan("Seu cardápio digital");
        s.setDescription("Configure o nome, o contato e a identidade do seu restaurante em Administração > Configurações da loja.");
        s.setHeroTitle("Seu cardápio, do seu jeito");
        s.setHeroSubtitle("Monte o pedido em poucos passos, personalize seu prato e acompanhe a entrega até a sua porta.");
        s.setDeliveryTimeNote("Entrega em 35–50 min");
        s.setDeliveryFee(defaultDeliveryFee);
        s.setBrandPrimary("#ef5b2a");
        s.setBrandPrimaryDark("#d94717");
        s.setUpdatedAt(LocalDateTime.now());
        return repository.save(s);
    }
}
