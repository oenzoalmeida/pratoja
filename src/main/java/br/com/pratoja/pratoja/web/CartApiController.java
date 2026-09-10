package br.com.pratoja.pratoja.web;
import br.com.pratoja.pratoja.cart.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/cart") @RequiredArgsConstructor
public class CartApiController {
    private final SessionCart cart;private final CartService service;
    public record AddRequest(@NotNull Long productId,@Min(1) @Max(20) int quantity,List<Long> optionIds,@Size(max=400) String notes){}
    @GetMapping public Map<String,Object> get(){return Map.of("count",cart.count(),"subtotal",cart.subtotal(),"items",cart.getLines());}
    @PostMapping("/items") public ResponseEntity<?> add(@Valid @RequestBody AddRequest r){try{cart.add(service.create(r.productId,r.quantity,r.optionIds,r.notes));return ResponseEntity.ok(get());}catch(IllegalArgumentException e){return ResponseEntity.badRequest().body(Map.of("message",e.getMessage()));}}
    @PatchMapping("/items/{key}") public ResponseEntity<?> quantity(@PathVariable String key,@RequestBody Map<String,Integer> body){int q=body.getOrDefault("quantity",0);CartLine old=cart.getLines().stream().filter(l->l.key().equals(key)).findFirst().orElse(null);if(old==null||q<1||q>20)return ResponseEntity.badRequest().body(Map.of("message","Quantidade inválida."));cart.replace(key,new CartLine(old.key(),old.productId(),old.productName(),old.imagePath(),old.unitPrice(),q,old.choices(),old.notes(),old.available()));return ResponseEntity.ok(get());}
    @DeleteMapping("/items/{key}") public Map<String,Object> remove(@PathVariable String key){cart.remove(key);return get();}
    @PatchMapping("/notes") public Map<String,Object> notes(@RequestBody Map<String,String> body){cart.setNotes(body.get("notes"));return get();}
}
