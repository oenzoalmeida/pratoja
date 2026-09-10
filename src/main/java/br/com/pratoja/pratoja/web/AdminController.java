package br.com.pratoja.pratoja.web;
import br.com.pratoja.pratoja.domain.*;
import br.com.pratoja.pratoja.repository.*;
import br.com.pratoja.pratoja.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
@Controller @RequestMapping("/admin") @RequiredArgsConstructor
public class AdminController {
    private final OrderRepository orders;private final OrderService orderService;private final ProductRepository products;private final CategoryRepository categories;private final OptionGroupRepository groups;private final ProductOptionRepository options;private final UserRepository users;private final RealtimeHub hub;
    @Value("${pratoja.upload-dir}")private String uploadDir;
    @GetMapping String dashboard(Model m){LocalDateTime start=LocalDate.now().atStartOfDay(),end=start.plusDays(1);m.addAttribute("today",orders.countByCreatedAtBetween(start,end));m.addAttribute("revenue",orders.revenue(start,end));m.addAttribute("pending",orders.countByStatus(DomainTypes.OrderStatus.RECEIVED)+orders.countByStatus(DomainTypes.OrderStatus.CONFIRMED));m.addAttribute("preparing",orders.countByStatus(DomainTypes.OrderStatus.PREPARING));m.addAttribute("done",orders.countByStatus(DomainTypes.OrderStatus.DELIVERED));m.addAttribute("recent",orderService.all().stream().limit(8).toList());return "admin/dashboard";}
    @GetMapping("/produtos")String products(Model m){m.addAttribute("products",products.findByArchivedFalseOrderByCategorySortOrderAscNameAsc());m.addAttribute("categories",categories.findAll());return "admin/products";}
    @GetMapping("/produtos/novo")String newProduct(Model m){m.addAttribute("product",new Product());m.addAttribute("categories",categories.findAll());return "admin/product-form";}
    @GetMapping("/produtos/{id}/editar")String editProduct(@PathVariable Long id,Model m){m.addAttribute("product",products.findById(id).orElseThrow());m.addAttribute("categories",categories.findAll());return "admin/product-form";}
    @PostMapping("/produtos/salvar")String saveProduct(@RequestParam(required=false)Long id,@RequestParam String name,@RequestParam String description,@RequestParam BigDecimal price,@RequestParam Long categoryId,@RequestParam(defaultValue="false")boolean available,@RequestParam(defaultValue="false")boolean featured,@RequestParam(defaultValue="false")boolean customizable,@RequestParam(required=false)MultipartFile image,RedirectAttributes redirect){try{if(name.isBlank()||description.isBlank()||price.signum()<0)throw new IllegalArgumentException("Revise os dados do produto.");Product p=id==null?new Product():products.findById(id).orElseThrow();p.setName(name.strip());p.setDescription(description.strip());p.setPrice(price);p.setCategory(categories.findById(categoryId).orElseThrow());p.setAvailable(available);p.setFeatured(featured);p.setCustomizable(customizable);if(image!=null&&!image.isEmpty())p.setImagePath(saveImage(image));products.save(p);redirect.addFlashAttribute("success","Produto salvo.");return "redirect:/admin/produtos";}catch(Exception e){redirect.addFlashAttribute("error",e.getMessage());return id==null?"redirect:/admin/produtos/novo":"redirect:/admin/produtos/"+id+"/editar";}}
    @PostMapping("/produtos/{id}/disponibilidade")String availability(@PathVariable Long id){Product p=products.findById(id).orElseThrow();p.setAvailable(!p.isAvailable());products.save(p);return "redirect:/admin/produtos";}
    @PostMapping("/produtos/{id}/excluir")String archive(@PathVariable Long id){Product p=products.findById(id).orElseThrow();p.setArchived(true);p.setAvailable(false);products.save(p);return "redirect:/admin/produtos";}
    @PostMapping("/categorias")String category(@RequestParam(required=false)Long id,@RequestParam String name,@RequestParam(defaultValue="0")int sortOrder){Category c=id==null?new Category():categories.findById(id).orElseThrow();c.setName(name.strip());c.setSortOrder(sortOrder);categories.save(c);return "redirect:/admin/produtos";}
    @PostMapping("/categorias/{id}/status")String categoryStatus(@PathVariable Long id){Category c=categories.findById(id).orElseThrow();c.setActive(!c.isActive());categories.save(c);return "redirect:/admin/produtos";}
    @GetMapping("/pedidos")String orderBoard(Model m){m.addAttribute("orders",orderService.all());return "admin/orders";}
    @GetMapping("/pedidos/{id}")String order(@PathVariable Long id,Authentication auth,Model m){m.addAttribute("order",orderService.view(id,auth,true));m.addAttribute("statuses",DomainTypes.OrderStatus.values());return "admin/order-detail";}
    @PostMapping("/pedidos/{id}/status")String status(@PathVariable Long id,@RequestParam DomainTypes.OrderStatus status,Authentication auth,RedirectAttributes redirect){try{orderService.transition(id,status,auth.getName());redirect.addFlashAttribute("success","Status atualizado.");}catch(IllegalArgumentException e){redirect.addFlashAttribute("error",e.getMessage());}return "redirect:/admin/pedidos/"+id;}
    @GetMapping("/clientes")String customers(Model m){m.addAttribute("customers",users.findByRoleOrderByNameAsc(DomainTypes.Role.CUSTOMER));return "admin/customers";}
    @GetMapping("/relatorios")String reports(Model m){List<OrderView> delivered=orderService.all().stream().filter(o->o.status()==DomainTypes.OrderStatus.DELIVERED).toList();BigDecimal revenue=delivered.stream().map(OrderView::total).reduce(BigDecimal.ZERO,BigDecimal::add);m.addAttribute("orders",delivered);m.addAttribute("revenue",revenue);m.addAttribute("ticket",delivered.isEmpty()?BigDecimal.ZERO:revenue.divide(BigDecimal.valueOf(delivered.size()),2,java.math.RoundingMode.HALF_UP));Map<String,Integer> top=delivered.stream().flatMap(o->o.items().stream()).collect(Collectors.toMap(OrderView.Item::name,OrderView.Item::quantity,Integer::sum));m.addAttribute("top",top.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue().reversed()).limit(5).toList());return "admin/reports";}
    @GetMapping(value="/pedidos/events",produces=MediaType.TEXT_EVENT_STREAM_VALUE)@ResponseBody SseEmitter events(){return hub.subscribeAdmin();}
    private String saveImage(MultipartFile file)throws Exception{String type=file.getContentType();if(type==null||!List.of("image/jpeg","image/png","image/webp").contains(type))throw new IllegalArgumentException("Use uma imagem JPG, PNG ou WebP.");String ext=switch(type){case"image/png"->".png";case"image/webp"->".webp";default->".jpg";};Path dir=Path.of(uploadDir).toAbsolutePath().normalize();Files.createDirectories(dir);String name=UUID.randomUUID()+ext;file.transferTo(dir.resolve(name));return "/uploads/"+name;}
}
