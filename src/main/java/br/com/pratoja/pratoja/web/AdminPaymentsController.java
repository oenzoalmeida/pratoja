package br.com.pratoja.pratoja.web;

import br.com.pratoja.pratoja.domain.Store;
import br.com.pratoja.pratoja.payments.MercadoPagoProperties;
import br.com.pratoja.pratoja.payments.StorePaymentService;
import br.com.pratoja.pratoja.service.StoreService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Conexao da conta Mercado Pago DA LOJA (OAuth) pelo admin da loja.
 *  Cada lojista conecta a propria conta; o estado (state) aleatorio na sessao impede CSRF no callback. */
@Controller @RequestMapping("/admin/pagamentos") @RequiredArgsConstructor
public class AdminPaymentsController {
    private final StoreService storeService;
    private final StorePaymentService storePayments;
    private final MercadoPagoProperties props;

    private Store store(Authentication auth) { return storeService.adminStore(auth); }

    @GetMapping
    String page(Authentication auth, Model model) {
        Store store = store(auth);
        model.addAttribute("account", storePayments.accountOf(store.getId()).orElse(null));
        model.addAttribute("oauthReady", storePayments.oauthReady());
        model.addAttribute("callbackUrl", storePayments.callbackUrl());
        return "admin/payments";
    }

    @GetMapping("/mercado-pago/conectar")
    String connect(Authentication auth, HttpSession session, RedirectAttributes redirect) {
        if (!storePayments.oauthReady()) {
            redirect.addFlashAttribute("error", "Integracao Mercado Pago nao configurada no servidor.");
            return "redirect:/admin/pagamentos";
        }
        String state = storePayments.newState();
        session.setAttribute("mp_oauth_state", state);
        return "redirect:" + storePayments.connectUrl(state);
    }

    @GetMapping("/mercado-pago/callback")
    String callback(Authentication auth, HttpSession session,
                    @RequestParam(required = false) String code,
                    @RequestParam(required = false) String state,
                    @RequestParam(required = false) String error,
                    RedirectAttributes redirect) {
        String expected = (String) session.getAttribute("mp_oauth_state");
        session.removeAttribute("mp_oauth_state");
        if (expected == null || state == null || !expected.equals(state) || code == null || error != null) {
            redirect.addFlashAttribute("error", "Conexao com o Mercado Pago nao concluida. Tente novamente.");
            return "redirect:/admin/pagamentos";
        }
        try {
            storePayments.connect(store(auth), code);
            redirect.addFlashAttribute("success", "Conta Mercado Pago conectada.");
        } catch (Exception e) {
            redirect.addFlashAttribute("error", "Nao foi possivel conectar o Mercado Pago. Tente novamente.");
        }
        return "redirect:/admin/pagamentos";
    }

    @PostMapping("/mercado-pago/desconectar")
    String disconnect(Authentication auth, RedirectAttributes redirect) {
        storePayments.disconnect(store(auth));
        redirect.addFlashAttribute("success", "Conta Mercado Pago desconectada. Pagamentos online foram desativados.");
        return "redirect:/admin/pagamentos";
    }
}
