package br.com.pratoja.pratoja.cart;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
public record CartLine(String key, Long productId, String productName, String imagePath, BigDecimal unitPrice, int quantity, List<Choice> choices, String notes, boolean available) implements Serializable {
    public record Choice(Long optionId, String groupName, String name, BigDecimal price) implements Serializable {}
    public BigDecimal total(){return unitPrice.multiply(BigDecimal.valueOf(quantity));}
}
