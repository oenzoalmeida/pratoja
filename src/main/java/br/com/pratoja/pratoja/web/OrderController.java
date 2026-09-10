package br.com.pratoja.pratoja.web;
import br.com.pratoja.pratoja.cart.SessionCart;
import br.com.pratoja.pratoja.domain.DomainTypes;
import br.com.pratoja.pratoja.repository.AddressRepository;
import br.com.pratoja.pratoja.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;
@Controller @RequiredArgsConstructor
public class OrderController {
    private final SessionCart cart;private final CurrentUserService current;private final AddressRepository addresses;private final OrderService service;private final RealtimeHub hub;
    @GetMapping("/carrinho")String cart(Model model){model.addAttribute("cart",cart);return "cart";}
    @GetMapping("/checkout")String checkout(Authentication auth,Model model){if(cart.getLines().isEmpty())return "redirect:/carrinho";model.addAttribute("cart",cart);model.addAttribute("addresses",addresses.findByUserIdOrderByPrimaryAddressDescIdDesc(current.require(auth).getId()));model.addAttribute("deliveryFee",new BigDecimal("6.00"));return "checkout";}
    @PostMapping("/checkout")String checkout(Authentication auth,@RequestParam String fulfillment,@RequestParam(required=false)Long addressId,@RequestParam String paymentMethod,@RequestParam(required=false)String changeFor,@RequestParam(required=false)String notes,Model model,RedirectAttributes redirect){try{Long id=service.place(auth,fulfillment,addressId,paymentMethod,changeFor,notes);return "redirect:/pedidos/"+id+"/acompanhar";}catch(IllegalArgumentException e){redirect.addFlashAttribute("error",e.getMessage());return "redirect:/checkout";}}
    @GetMapping("/pedidos/{id}/acompanhar")String tracking(@PathVariable Long id,Authentication auth,Model model){model.addAttribute("order",service.view(id,auth,false));return "tracking";}
    @GetMapping("/historico")String history(Authentication auth,Model model){model.addAttribute("orders",service.history(auth));return "history";}
    @PostMapping("/pedidos/{id}/repetir")String repeat(@PathVariable Long id,Authentication auth,RedirectAttributes redirect){redirect.addFlashAttribute("success",service.repeat(id,auth));return "redirect:/carrinho";}
    @PostMapping("/pedidos/{id}/avaliar")String review(@PathVariable Long id,Authentication auth,@RequestParam int rating,@RequestParam(required=false)String comment,RedirectAttributes redirect){try{service.review(id,auth,rating,comment);redirect.addFlashAttribute("success","Obrigado pela avaliação!");}catch(IllegalArgumentException e){redirect.addFlashAttribute("error",e.getMessage());}return "redirect:/pedidos/"+id+"/acompanhar";}
    @GetMapping(value="/api/orders/{id}/events",produces=MediaType.TEXT_EVENT_STREAM_VALUE)@ResponseBody SseEmitter events(@PathVariable Long id,Authentication auth){service.view(id,auth,false);return hub.subscribe(id);}
}
