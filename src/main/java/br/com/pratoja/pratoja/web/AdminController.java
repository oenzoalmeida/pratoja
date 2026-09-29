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
    private final OrderRepository orders;private final OrderService orderService;private final ProductRepository products;private final CategoryRepository categories;private final OptionGroupRepository groups;private final ProductOptionRepository options;private final UserRepository users;private final RealtimeHub hub;private final StoreRepository stores;private final StoreService storeService;private final CurrentUserService currentUser;
    @Value("${pratoja.upload-dir}")private String uploadDir;

    /** Loja em contexto = loja do admin logado. PLATFORM_ADMIN não tem loja -> 404 (exceto dashboard). */
    private Store store(Authentication auth){return storeService.adminStore(auth);}

    @GetMapping String dashboard(Authentication auth,Model m){LocalDateTime start=LocalDate.now().atStartOfDay(),end=start.plusDays(1);Store s;try{s=store(auth);}catch(Exception e){/* PLATFORM_ADMIN não opera loja: aviso no painel (funcionalidade /platform na Fase 5) */m.addAttribute("platformAdmin",true);return "admin/dashboard";}Long sid=s.getId();m.addAttribute("today",orders.countByStore_IdAndCreatedAtBetween(sid,start,end));m.addAttribute("revenue",orders.revenueInStore(sid,start,end));m.addAttribute("pending",orders.countByStore_IdAndStatus(sid,DomainTypes.OrderStatus.RECEIVED)+orders.countByStore_IdAndStatus(sid,DomainTypes.OrderStatus.CONFIRMED));m.addAttribute("preparing",orders.countByStore_IdAndStatus(sid,DomainTypes.OrderStatus.PREPARING));m.addAttribute("done",orders.countByStore_IdAndStatus(sid,DomainTypes.OrderStatus.DELIVERED));m.addAttribute("platformAdmin",false);m.addAttribute("recent",orderService.all(s).stream().limit(8).toList());return "admin/dashboard";}
    @GetMapping("/produtos")String products(Authentication auth,Model m){Store s=store(auth);m.addAttribute("products",products.findByStore_IdAndArchivedFalseOrderByCategorySortOrderAscNameAsc(s.getId()));m.addAttribute("categories",categories.findByStore_IdOrderBySortOrderAscNameAsc(s.getId()));return "admin/products";}
    @GetMapping("/produtos/novo")String newProduct(Authentication auth,Model m){Store s=store(auth);m.addAttribute("product",new Product());m.addAttribute("categories",categories.findByStore_IdOrderBySortOrderAscNameAsc(s.getId()));return "admin/product-form";}
    @GetMapping("/produtos/{id}/editar")String editProduct(Authentication auth,@PathVariable Long id,Model m){Store s=store(auth);m.addAttribute("product",products.findByIdAndStore_Id(id,s.getId()).orElseThrow(SecurityException::new));m.addAttribute("categories",categories.findByStore_IdOrderBySortOrderAscNameAsc(s.getId()));return "admin/product-form";}
    @PostMapping("/produtos/salvar")String saveProduct(Authentication auth,@RequestParam(required=false)Long id,@RequestParam String name,@RequestParam String description,@RequestParam BigDecimal price,@RequestParam Long categoryId,@RequestParam(defaultValue="false")boolean available,@RequestParam(defaultValue="false")boolean featured,@RequestParam(defaultValue="false")boolean customizable,@RequestParam(required=false)MultipartFile image,RedirectAttributes redirect){try{Store s=store(auth);if(name.isBlank()||description.isBlank()||price.signum()<0)throw new IllegalArgumentException("Revise os dados do produto.");Product p=id==null?new Product():products.findByIdAndStore_Id(id,s.getId()).orElseThrow(SecurityException::new);Category c=categories.findByIdAndStore_Id(categoryId,s.getId()).orElseThrow(SecurityException::new);p.setName(name.strip());p.setDescription(description.strip());p.setPrice(price);p.setCategory(c);p.setStore(s);p.setAvailable(available);p.setFeatured(featured);p.setCustomizable(customizable);if(image!=null&&!image.isEmpty())p.setImagePath(saveImage(image));products.save(p);redirect.addFlashAttribute("success","Produto salvo.");return "redirect:/admin/produtos";}catch(SecurityException e){throw e;}catch(Exception e){redirect.addFlashAttribute("error",e.getMessage());return id==null?"redirect:/admin/produtos/novo":"redirect:/admin/produtos/"+id+"/editar";}}
    @PostMapping("/produtos/{id}/disponibilidade")String availability(Authentication auth,@PathVariable Long id){Store s=store(auth);Product p=products.findByIdAndStore_Id(id,s.getId()).orElseThrow(SecurityException::new);p.setAvailable(!p.isAvailable());products.save(p);return "redirect:/admin/produtos";}
    @PostMapping("/produtos/{id}/excluir")String archive(Authentication auth,@PathVariable Long id){Store s=store(auth);Product p=products.findByIdAndStore_Id(id,s.getId()).orElseThrow(SecurityException::new);p.setArchived(true);p.setAvailable(false);products.save(p);return "redirect:/admin/produtos";}
    @PostMapping("/categorias")String category(Authentication auth,@RequestParam(required=false)Long id,@RequestParam String name,@RequestParam(defaultValue="0")int sortOrder){Store s=store(auth);Category c=id==null?new Category():categories.findByIdAndStore_Id(id,s.getId()).orElseThrow(SecurityException::new);c.setStore(s);c.setName(name.strip());c.setSortOrder(sortOrder);categories.save(c);return "redirect:/admin/produtos";}
    @PostMapping("/categorias/{id}/status")String categoryStatus(Authentication auth,@PathVariable Long id){Store s=store(auth);Category c=categories.findByIdAndStore_Id(id,s.getId()).orElseThrow(SecurityException::new);c.setActive(!c.isActive());categories.save(c);return "redirect:/admin/produtos";}
    @GetMapping("/pedidos")String orderBoard(Authentication auth,Model m){m.addAttribute("orders",orderService.all(store(auth)));return "admin/orders";}
    @GetMapping("/pedidos/{id}")String order(Authentication auth,@PathVariable Long id,Model m){Store s=store(auth);m.addAttribute("order",orderService.adminView(id,s));m.addAttribute("statuses",DomainTypes.OrderStatus.values());return "admin/order-detail";}
    @PostMapping("/pedidos/{id}/status")String status(Authentication auth,@PathVariable Long id,@RequestParam DomainTypes.OrderStatus status,RedirectAttributes redirect){try{orderService.transitionInStore(id,store(auth),status,auth.getName());redirect.addFlashAttribute("success","Status atualizado.");}catch(IllegalArgumentException e){redirect.addFlashAttribute("error",e.getMessage());}return "redirect:/admin/pedidos/"+id;}
    @GetMapping("/clientes")String customers(Model m){m.addAttribute("customers",users.findByRoleOrderByNameAsc(DomainTypes.Role.CUSTOMER));return "admin/customers";}
    @GetMapping("/relatorios")String reports(Authentication auth,Model m){List<OrderView> delivered=orderService.all(store(auth)).stream().filter(o->o.status()==DomainTypes.OrderStatus.DELIVERED).toList();BigDecimal revenue=delivered.stream().map(OrderView::total).reduce(BigDecimal.ZERO,BigDecimal::add);m.addAttribute("orders",delivered);m.addAttribute("revenue",revenue);m.addAttribute("ticket",delivered.isEmpty()?BigDecimal.ZERO:revenue.divide(BigDecimal.valueOf(delivered.size()),2,java.math.RoundingMode.HALF_UP));Map<String,Integer> top=delivered.stream().flatMap(o->o.items().stream()).collect(Collectors.toMap(OrderView.Item::name,OrderView.Item::quantity,Integer::sum));m.addAttribute("top",top.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue().reversed()).limit(5).toList());return "admin/reports";}
    /** SSE do painel: resolve a loja da sessão. STORE_ADMIN recebe apenas eventos da própria loja;
     *  PLATFORM_ADMIN (sem loja) recebe eventos de todas — papel de monitoria da plataforma. */
    @GetMapping(value="/pedidos/events",produces=MediaType.TEXT_EVENT_STREAM_VALUE)@ResponseBody SseEmitter events(Authentication auth){
        var u=currentUser.require(auth);
        if(u.getRole()==DomainTypes.Role.PLATFORM_ADMIN)return hub.subscribePlatformAdmin();
        return hub.subscribeAdmin(storeService.adminStore(u).getId());
    }
    @GetMapping("/configuracoes")String settings(Authentication auth,Model m){m.addAttribute("settings",store(auth));return "admin/settings";}
    @PostMapping("/configuracoes")String saveSettings(Authentication auth,@RequestParam String name,@RequestParam(required=false)String slogan,@RequestParam(required=false)String description,@RequestParam(required=false)String phone,@RequestParam(required=false)String whatsapp,@RequestParam(required=false)String email,@RequestParam(required=false)String addressStreet,@RequestParam(required=false)String addressNumber,@RequestParam(required=false)String addressComplement,@RequestParam(required=false)String addressNeighborhood,@RequestParam(required=false)String addressCity,@RequestParam(required=false)String addressState,@RequestParam(required=false)String addressZip,@RequestParam(required=false)String openingHours,@RequestParam(required=false)String heroTitle,@RequestParam(required=false)String heroSubtitle,@RequestParam(required=false)String deliveryTimeNote,@RequestParam BigDecimal deliveryFee,@RequestParam(required=false)String brandPrimary,@RequestParam(required=false)String brandPrimaryDark,@RequestParam(required=false)String logoPath,RedirectAttributes redirect){
        try{
            Store s=store(auth);
            if(name==null||name.strip().length()<2)throw new IllegalArgumentException("Informe o nome do restaurante (mínimo de 2 caracteres).");
            if(deliveryFee==null||deliveryFee.signum()<0)throw new IllegalArgumentException("A taxa de entrega deve ser maior ou igual a zero.");
            if(!isHexOrNull(brandPrimary)||!isHexOrNull(brandPrimaryDark))throw new IllegalArgumentException("As cores devem estar no formato hexadecimal #RRGGBB (ex.: #ef5b2a).");
            s.setName(name.strip());s.setSlogan(bl(slogan));s.setDescription(bl(description));s.setPhone(bl(phone));s.setWhatsapp(bl(whatsapp));s.setEmail(bl(email));
            s.setAddressStreet(bl(addressStreet));s.setAddressNumber(bl(addressNumber));s.setAddressComplement(bl(addressComplement));s.setAddressNeighborhood(bl(addressNeighborhood));s.setAddressCity(bl(addressCity));s.setAddressState(bl(addressState));s.setAddressZip(bl(addressZip));
            s.setOpeningHours(openingHours==null?"":openingHours.strip());s.setHeroTitle(bl(heroTitle));s.setHeroSubtitle(bl(heroSubtitle));s.setDeliveryTimeNote(bl(deliveryTimeNote));
            s.setDeliveryFee(deliveryFee);s.setBrandPrimary(bl(brandPrimary)==null?null:brandPrimary.strip());s.setBrandPrimaryDark(bl(brandPrimaryDark)==null?null:brandPrimaryDark.strip());s.setLogoPath(bl(logoPath));
            s.setUpdatedAt(LocalDateTime.now());stores.save(s);redirect.addFlashAttribute("success","Configurações salvas.");return "redirect:/admin/configuracoes";
        }catch(IllegalArgumentException e){redirect.addFlashAttribute("error",e.getMessage());return "redirect:/admin/configuracoes";}
    }
    private static String bl(String v){if(v==null)return null;String x=v.strip();return x.isEmpty()?null:x;}
    private static boolean isHexOrNull(String v){if(v==null||v.strip().isEmpty())return true;return v.strip().matches("^#[0-9a-fA-F]{6}$");}

    private String saveImage(MultipartFile file)throws Exception{String type=file.getContentType();if(type==null||!List.of("image/jpeg","image/png","image/webp").contains(type))throw new IllegalArgumentException("Use uma imagem JPG, PNG ou WebP.");String ext=switch(type){case"image/png"->".png";case"image/webp"->".webp";default->".jpg";};Path dir=Path.of(uploadDir).toAbsolutePath().normalize();Files.createDirectories(dir);String name=UUID.randomUUID()+ext;file.transferTo(dir.resolve(name));return "/uploads/"+name;}
}
