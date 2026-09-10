package br.com.pratoja.pratoja.cart;
import br.com.pratoja.pratoja.domain.*;
import br.com.pratoja.pratoja.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
@Service @RequiredArgsConstructor
public class CartService {
    private final ProductRepository products; private final OptionGroupRepository groups; private final ProductOptionRepository options;
    @Transactional(readOnly=true) public CartLine create(Long productId,int quantity,List<Long> optionIds,String notes){
        Product p=products.findById(productId).filter(x->!x.isArchived()).orElseThrow(()->new IllegalArgumentException("Produto não encontrado."));
        if(!p.isAvailable())throw new IllegalArgumentException("Este produto está indisponível."); if(quantity<1||quantity>20)throw new IllegalArgumentException("Quantidade inválida.");
        List<Long> ids=optionIds==null?List.of():optionIds.stream().distinct().toList(); List<ProductOption> selected=ids.isEmpty()?List.of():options.findByIdInAndAvailableTrue(ids);
        if(selected.size()!=ids.size()||selected.stream().anyMatch(o->!o.getGroup().getProduct().getId().equals(productId)))throw new IllegalArgumentException("Uma opção selecionada é inválida.");
        for(OptionGroup g:groups.findByProductIdOrderBySortOrder(productId)){long count=selected.stream().filter(o->o.getGroup().getId().equals(g.getId())).count();if(count<g.getMinSelections()||count>g.getMaxSelections())throw new IllegalArgumentException("Revise as escolhas de “"+g.getName()+"”.");}
        BigDecimal unit=p.getPrice().add(selected.stream().map(ProductOption::getPriceDelta).reduce(BigDecimal.ZERO,BigDecimal::add));
        List<CartLine.Choice> choices=selected.stream().sorted(Comparator.comparing(o->o.getGroup().getSortOrder())).map(o->new CartLine.Choice(o.getId(),o.getGroup().getName(),o.getName(),o.getPriceDelta())).toList();
        String clean=notes==null?"":notes.strip();if(clean.length()>400)throw new IllegalArgumentException("A observação pode ter até 400 caracteres.");String seed=productId+":"+ids.stream().sorted().toList()+":"+clean;
        return new CartLine(UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString(),p.getId(),p.getName(),p.getImagePath(),unit,quantity,choices,clean,true);
    }
}
