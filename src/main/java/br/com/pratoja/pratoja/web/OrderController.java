package br.com.pratoja.pratoja.web;
import br.com.pratoja.pratoja.cart.SessionCart;
import br.com.pratoja.pratoja.domain.DomainTypes;
import br.com.pratoja.pratoja.domain.Store;
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
import org.springframework.web.servlet.view.RedirectView;
@Controller @RequiredArgsConstructor
public class OrderController {
    private final SessionCart cart;private final CurrentUserService current;private final AddressRepository addresses;private final OrderService service;private final RealtimeHub hub;private final StoreService storeService;

    /** Contexto de loja da rota (active=true; inexistente/inativa -> 404). */
    private Store store(String slug){return storeService.activeBySlug(slug);}

    /** Compat: /checkout antigo redireciona 301 para a loja única ativa. */
    @GetMapping("/checkout")RedirectView oldCheckout(){return redirect("/checkout");}
    private RedirectView redirect(String suffix){Store s=storeService.singleActive();RedirectView v=new RedirectView("/loja/"+s.getSlug()+suffix,true,false);v.setStatusCode(org.springframework.http.HttpStatus.MOVED_PERMANENTLY);return v;}

    @GetMapping("/historico")String history(Authentication auth,Model model){model.addAttribute("orders",service.history(auth));return "history";}

    @GetMapping("/loja/{slug}/carrinho")String cart(@PathVariable String slug,Model model){Store s=store(slug);model.addAttribute("cartLines",cart.getLines(s.getId()));model.addAttribute("cartSubtotal",cart.subtotal(s.getId()));model.addAttribute("cartNotes",cart.getNotes(s.getId()));return "cart";}
    @GetMapping("/loja/{slug}/checkout")String checkout(@PathVariable String slug,Authentication auth,Model model){Store s=store(slug);if(cart.getLines(s.getId()).isEmpty())return "redirect:/loja/"+s.getSlug()+"/carrinho";model.addAttribute("cartLines",cart.getLines(s.getId()));model.addAttribute("cartSubtotal",cart.subtotal(s.getId()));model.addAttribute("cartNotes",cart.getNotes(s.getId()));model.addAttribute("addresses",addresses.findByUserIdOrderByPrimaryAddressDescIdDesc(current.require(auth).getId()));model.addAttribute("deliveryFee",s.getDeliveryFee());return "checkout";}
    @PostMapping("/loja/{slug}/checkout")String checkout(@PathVariable String slug,Authentication auth,@RequestParam String fulfillment,@RequestParam(required=false)Long addressId,@RequestParam String paymentMethod,@RequestParam(required=false)String changeFor,@RequestParam(required=false)String notes,RedirectAttributes redirect){try{Long id=service.place(auth,store(slug),fulfillment,addressId,paymentMethod,changeFor,notes);return "redirect:/loja/"+slug+"/pedidos/"+id+"/acompanhar";}catch(IllegalArgumentException e){redirect.addFlashAttribute("error",e.getMessage());return "redirect:/loja/"+slug+"/checkout";}}
    @GetMapping("/loja/{slug}/pedidos/{id}/acompanhar")String tracking(@PathVariable String slug,@PathVariable Long id,Authentication auth,Model model){model.addAttribute("order",service.viewInStore(id,auth,store(slug)));return "tracking";}
    @PostMapping("/loja/{slug}/pedidos/{id}/repetir")String repeat(@PathVariable String slug,@PathVariable Long id,Authentication auth,RedirectAttributes redirect){redirect.addFlashAttribute("success",service.repeat(id,auth,store(slug)));return "redirect:/loja/"+slug+"/carrinho";}
    @PostMapping("/loja/{slug}/pedidos/{id}/avaliar")String review(@PathVariable String slug,@PathVariable Long id,Authentication auth,@RequestParam int rating,@RequestParam(required=false)String comment,RedirectAttributes redirect){try{service.review(id,auth,rating,comment);redirect.addFlashAttribute("success","Obrigado pela avaliação!");}catch(IllegalArgumentException e){redirect.addFlashAttribute("error",e.getMessage());}return "redirect:/loja/"+slug+"/pedidos/"+id+"/acompanhar";}
    @GetMapping(value="/loja/{slug}/api/orders/{id}/events",produces=MediaType.TEXT_EVENT_STREAM_VALUE)@ResponseBody SseEmitter events(@PathVariable String slug,@PathVariable Long id,Authentication auth){service.viewInStore(id,auth,store(slug));return hub.subscribe(id);}
}
