package br.com.pratoja.pratoja.web;

import br.com.pratoja.pratoja.domain.DomainTypes;
import br.com.pratoja.pratoja.domain.Payment;
import br.com.pratoja.pratoja.domain.Store;
import br.com.pratoja.pratoja.domain.StorePaymentAccount;
import br.com.pratoja.pratoja.payments.PixPaymentService;
import br.com.pratoja.pratoja.payments.StorePaymentService;
import br.com.pratoja.pratoja.service.OrderService;
import br.com.pratoja.pratoja.service.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/** Tela de pagamento Pix do cliente: QR dinamico + copia-e-cola + acompanhamento em tempo real.
 *  Rota protegida (dono do pedido, loja da URL) pelas regras de /loja/*\/pedidos/**. */
@Controller @RequiredArgsConstructor
public class StorePaymentController {
    private final StoreService storeService;
    private final OrderService orderService;
    private final PixPaymentService pixPayments;
    private final StorePaymentService storePayments;

    private Store store(String slug) { return storeService.activeBySlug(slug); }

    @GetMapping("/loja/{slug}/pedidos/{id}/pagamento")
    String paymentPage(@PathVariable String slug, @PathVariable Long id, Authentication auth, Model model,
                       RedirectAttributes redirect) {
        Store store = store(slug);
        Payment payment = orderService.paymentInStore(id, auth, store);
        if (payment == null || payment.getMethod() != DomainTypes.PaymentMethod.PIX
                || payment.getStatus() != DomainTypes.PaymentStatus.PENDING || payment.getReference() == null)
            return "redirect:/loja/" + slug + "/pedidos/" + id + "/acompanhar";
        String error = null;
        try { payment = pixPayments.startPix(id); }
        catch (Exception e) { error = "Nao foi possivel gerar o QR Code agora. Tente novamente em instantes."; }
        model.addAttribute("order", orderService.viewInStore(id, auth, store));
        model.addAttribute("payment", payment);
        model.addAttribute("error", error);
        model.addAttribute("testMode", storePayments.accountOf(store.getId())
                .map(a -> a.getMode() == StorePaymentAccount.Mode.TEST).orElse(false));
        return "payment";
    }

    /** Status para o polling da tela de pagamento. */
    @GetMapping("/loja/{slug}/pedidos/{id}/pagamento/status")
    @ResponseBody
    ResponseEntity<Map<String, String>> paymentStatus(@PathVariable String slug, @PathVariable Long id,
                                                      Authentication auth) {
        Payment payment = orderService.paymentInStore(id, auth, store(slug));
        return ResponseEntity.ok(Map.of("status", payment == null ? "UNKNOWN" : payment.getStatus().name()));
    }

    /** "Ja paguei": re-consulta a API do Mercado Pago pelo mesmo caminho validado do webhook.
     *  Util tambem no modo de testes, quando o webhook externo nao alcanca o ambiente local. */
    @PostMapping("/loja/{slug}/pedidos/{id}/pagamento/verificar")
    String verify(@PathVariable String slug, @PathVariable Long id, Authentication auth,
                  RedirectAttributes redirect) {
        Payment payment = orderService.paymentInStore(id, auth, store(slug));
        if (payment != null && payment.getMpPaymentId() != null
                && payment.getStatus() == DomainTypes.PaymentStatus.PENDING) {
            try {
                PixPaymentService.SyncResult result = pixPayments.syncFromProvider(payment);
                if (result == PixPaymentService.SyncResult.MISMATCH)
                    redirect.addFlashAttribute("error", "Pagamento ainda nao confirmado.");
            } catch (Exception e) {
                redirect.addFlashAttribute("error", "Nao foi possivel verificar agora. Tente novamente.");
            }
        }
        return "redirect:/loja/" + slug + "/pedidos/" + id + "/pagamento";
    }
}
