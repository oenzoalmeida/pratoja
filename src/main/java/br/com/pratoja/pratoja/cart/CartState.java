package br.com.pratoja.pratoja.cart;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.*;

/** Estado da sacola de UMA loja (a sessão guarda um estado por loja — carrinho por tenant). */
public class CartState implements Serializable {
    private final List<CartLine> lines = new ArrayList<>();
    private String notes = "";

    public List<CartLine> getLines() { return lines; }
    public String getNotes() { return notes; }
    public void setNotes(String n) { notes = n == null ? "" : n.strip(); }
    public int count() { return lines.stream().mapToInt(CartLine::quantity).sum(); }
    public BigDecimal subtotal() { return lines.stream().map(CartLine::total).reduce(BigDecimal.ZERO, BigDecimal::add); }
    public void add(CartLine line) {
        for (int i = 0; i < lines.size(); i++) {
            CartLine old = lines.get(i);
            if (old.key().equals(line.key())) {
                lines.set(i, new CartLine(old.key(), old.productId(), old.productName(), old.imagePath(), old.unitPrice(),
                        Math.min(20, old.quantity() + line.quantity()), old.choices(), old.notes(), old.available()));
                return;
            }
        }
        lines.add(line);
    }
    public void replace(String key, CartLine line) { for (int i = 0; i < lines.size(); i++) if (lines.get(i).key().equals(key)) { lines.set(i, line); return; } }
    public Optional<CartLine> find(String key) { return lines.stream().filter(l -> l.key().equals(key)).findFirst(); }
    public void remove(String key) { lines.removeIf(l -> l.key().equals(key)); }
    public void clear() { lines.clear(); notes = ""; }
}
