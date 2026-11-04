package br.com.pratoja.pratoja.web;

import br.com.pratoja.pratoja.domain.*;
import br.com.pratoja.pratoja.repository.*;
import br.com.pratoja.pratoja.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Regressão do contrato de erro ao salvar produto (auditoria 2026-11-03):
 * IllegalArgumentException mantém a mensagem de negócio; exceção inesperada
 * vira mensagem genérica pt-BR (o detalhe interno fica só no log).
 */
class AdminProductSaveErrorMessageTests {

    private AdminController newController(Store store, CategoryRepository categories, ProductRepository products) {
        StoreService storeService = mock(StoreService.class);
        when(storeService.adminStore(any(Authentication.class))).thenReturn(store);
        AdminController c = new AdminController(mock(OrderRepository.class), mock(OrderService.class),
                products, categories, mock(OptionGroupRepository.class), mock(ProductOptionRepository.class),
                mock(UserRepository.class), mock(RealtimeHub.class), mock(StoreRepository.class),
                storeService, mock(CurrentUserService.class));
        ReflectionTestUtils.setField(c, "uploadDir", "/tmp/pratoja-test-uploads");
        return c;
    }

    private Store store() { Store s = new Store(); s.setId(1L); return s; }

    private CategoryRepository categoriesOk() {
        CategoryRepository categories = mock(CategoryRepository.class);
        Category c = new Category(); c.setId(2L);
        when(categories.findByIdAndStore_Id(2L, 1L)).thenReturn(Optional.of(c));
        return categories;
    }

    @Test
    void businessValidationKeepsSpecificMessage() {
        ProductRepository products = mock(ProductRepository.class);
        AdminController c = newController(store(), categoriesOk(), products);
        MultipartFile image = mock(MultipartFile.class);
        when(image.isEmpty()).thenReturn(false);
        when(image.getContentType()).thenReturn("text/plain");
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
        String view = c.saveProduct(mock(Authentication.class), null, "Marmita", "Descricao ok", new BigDecimal("10.00"), 2L, true, false, false, image, ra);
        assertEquals("redirect:/admin/produtos/novo", view);
        assertEquals("Use uma imagem JPG, PNG ou WebP.", ra.getFlashAttributes().get("error"));
    }

    @Test
    void unexpectedIoFailureShowsGenericMessage() throws IOException {
        ProductRepository products = mock(ProductRepository.class);
        AdminController c = newController(store(), categoriesOk(), products);
        MultipartFile image = mock(MultipartFile.class);
        when(image.isEmpty()).thenReturn(false);
        when(image.getContentType()).thenReturn("image/png");
        when(image.getBytes()).thenThrow(new IOException("disk full: /private/internal/path"));
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
        c.saveProduct(mock(Authentication.class), null, "Marmita", "Descricao ok", new BigDecimal("10.00"), 2L, true, false, false, image, ra);
        assertEquals("Não foi possível salvar o produto. Tente novamente.", ra.getFlashAttributes().get("error"));
    }

    @Test
    void unexpectedDatabaseFailureShowsGenericMessage() {
        ProductRepository products = mock(ProductRepository.class);
        when(products.save(any())).thenThrow(new DataIntegrityViolationException("ERROR: duplicate key value violates unique constraint"));
        AdminController c = newController(store(), categoriesOk(), products);
        RedirectAttributesModelMap ra = new RedirectAttributesModelMap();
        String view = c.saveProduct(mock(Authentication.class), null, "Marmita", "Descricao ok", new BigDecimal("10.00"), 2L, true, false, false, null, ra);
        assertEquals("redirect:/admin/produtos/novo", view);
        assertEquals("Não foi possível salvar o produto. Tente novamente.", ra.getFlashAttributes().get("error"));
    }
}
