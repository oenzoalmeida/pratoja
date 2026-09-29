package br.com.pratoja.pratoja.cart;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;
import java.io.Serializable;
import java.util.*;

/**
 * Sacola em sessão POR LOJA (multitenant, Fase 4): cada loja tem sua própria CartState.
 * O cliente pode manter sacolas separadas simultaneamente — trocar de loja não descarta a sacola da outra.
 */
@Component @SessionScope
public class SessionCart implements Serializable {
    private final Map<Long, CartState> byStore = new HashMap<>();

    private CartState state(Long storeId) { return byStore.computeIfAbsent(storeId, k -> new CartState()); }

    public List<CartLine> getLines(Long storeId) { return state(storeId).getLines(); }
    public String getNotes(Long storeId) { return state(storeId).getNotes(); }
    public void setNotes(Long storeId, String n) { state(storeId).setNotes(n); }
    public int count(Long storeId) { return state(storeId).count(); }
    public java.math.BigDecimal subtotal(Long storeId) { return state(storeId).subtotal(); }
    public void add(Long storeId, CartLine line) { state(storeId).add(line); }
    public void replace(Long storeId, String key, CartLine line) { state(storeId).replace(key, line); }
    public Optional<CartLine> find(Long storeId, String key) { return state(storeId).find(key); }
    public void remove(Long storeId, String key) { state(storeId).remove(key); }
    public void clear(Long storeId) { state(storeId).clear(); }

    /** Fecha a sacola da loja: devolve as linhas e limpa o estado (usado no place()). */
    public List<CartLine> drain(Long storeId) {
        List<CartLine> lines = List.copyOf(state(storeId).getLines());
        clear(storeId);
        return lines;
    }
}
