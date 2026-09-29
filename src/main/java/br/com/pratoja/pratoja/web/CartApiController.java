package br.com.pratoja.pratoja.web;
import br.com.pratoja.pratoja.cart.*;
import br.com.pratoja.pratoja.domain.Store;
import br.com.pratoja.pratoja.service.StoreService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * Fase 4: API da sacola escopada por loja (/loja/{slug}/api/cart). A sacola da sessão é POR LOJA:
 * itens adicionados em uma loja não aparecem em outra.
 */
@RestController @RequestMapping("/loja/{slug}/api/cart") @RequiredArgsConstructor
public class CartApiController {
    private final SessionCart cart; private final CartService service; private final StoreService stores;

    private Long storeId(String slug) { return store(slug).getId(); }
    private Store store(String slug) { return stores.activeBySlug(slug); }

    public record AddRequest(@NotNull Long productId,@Min(1) @Max(20) int quantity,List<Long> optionIds,@Size(max=400) String notes){}
    @GetMapping public Map<String,Object> get(@PathVariable String slug){Long sid=storeId(slug);return Map.of("count",cart.count(sid),"subtotal",cart.subtotal(sid),"items",cart.getLines(sid));}
    @PostMapping("/items") public ResponseEntity<?> add(@PathVariable String slug,@Valid @RequestBody AddRequest r){
        Long sid=storeId(slug);
        try{cart.add(sid,service.create(sid,r.productId,r.quantity,r.optionIds,r.notes));return ResponseEntity.ok(get(slug));}catch(IllegalArgumentException e){return ResponseEntity.badRequest().body(Map.of("message",e.getMessage()));}}
    @PatchMapping("/items/{key}") public ResponseEntity<?> quantity(@PathVariable String slug,@PathVariable String key,@RequestBody Map<String,Integer> body){
        Long sid=storeId(slug);int q=body.getOrDefault("quantity",0);
        CartLine old=cart.find(sid,key).orElse(null);
        if(old==null||q<1||q>20)return ResponseEntity.badRequest().body(Map.of("message","Quantidade inválida."));
        cart.replace(sid,key,new CartLine(old.key(),old.productId(),old.productName(),old.imagePath(),old.unitPrice(),q,old.choices(),old.notes(),old.available()));
        return ResponseEntity.ok(get(slug));}
    @DeleteMapping("/items/{key}") public Map<String,Object> remove(@PathVariable String slug,@PathVariable String key){cart.remove(storeId(slug),key);return get(slug);}
    @PatchMapping("/notes") public Map<String,Object> notes(@PathVariable String slug,@RequestBody Map<String,String> body){cart.setNotes(storeId(slug),body.get("notes"));return get(slug);}
}
