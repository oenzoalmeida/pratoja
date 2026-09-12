package br.com.pratoja.pratoja.web;
import br.com.pratoja.pratoja.domain.*;
import br.com.pratoja.pratoja.repository.*;
import br.com.pratoja.pratoja.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.UUID;
@Controller @RequiredArgsConstructor
public class ProfileController {
    private final CurrentUserService current;private final AddressRepository addresses;private final UserRepository users;private final PasswordResetTokenRepository resetTokens;private final PasswordEncoder encoder;
    @GetMapping("/perfil")String profile(Authentication auth,Model model){User u=current.require(auth);model.addAttribute("user",u);model.addAttribute("addresses",addresses.findByUserIdOrderByPrimaryAddressDescIdDesc(u.getId()));return "profile";}
    @PostMapping("/perfil")String update(Authentication auth,@RequestParam String name,@RequestParam String phone,RedirectAttributes redirect){User u=current.require(auth);if(name.strip().length()<3||phone.replaceAll("\\D","").length()<10){redirect.addFlashAttribute("error","Revise nome e telefone.");return "redirect:/perfil";}u.setName(name.strip());u.setPhone(phone.strip());users.save(u);redirect.addFlashAttribute("success","Perfil atualizado.");return "redirect:/perfil";}
    @PostMapping("/perfil/enderecos")@Transactional String address(Authentication auth,@RequestParam(required=false)Long id,@RequestParam String label,@RequestParam String zipCode,@RequestParam String street,@RequestParam String number,@RequestParam(required=false)String complement,@RequestParam String neighborhood,@RequestParam String city,@RequestParam String state,@RequestParam(defaultValue="false")boolean primaryAddress,RedirectAttributes redirect){
        User u=current.require(auth);Address a=id==null?new Address():addresses.findById(id).filter(x->x.getUser().getId().equals(u.getId())).orElseThrow();String zip=zipCode.replaceAll("\\D","");if(zip.length()!=8||state.length()!=2||street.isBlank()||number.isBlank()){redirect.addFlashAttribute("error","Revise os dados do endereço.");return "redirect:/perfil";}if(primaryAddress)addresses.findByUserIdOrderByPrimaryAddressDescIdDesc(u.getId()).forEach(x->x.setPrimaryAddress(false));a.setUser(u);a.setLabel(label.strip());a.setZipCode(zip.substring(0,5)+"-"+zip.substring(5));a.setStreet(street.strip());a.setNumber(number.strip());a.setComplement(complement==null?null:complement.strip());a.setNeighborhood(neighborhood.strip());a.setCity(city.strip());a.setState(state.toUpperCase());a.setPrimaryAddress(primaryAddress||addresses.findByUserIdOrderByPrimaryAddressDescIdDesc(u.getId()).isEmpty());addresses.save(a);redirect.addFlashAttribute("success","Endereço salvo.");return "redirect:/perfil";}
    @PostMapping("/perfil/enderecos/{id}/excluir")String delete(Authentication auth,@PathVariable Long id){User u=current.require(auth);addresses.findById(id).filter(a->a.getUser().getId().equals(u.getId())).ifPresent(addresses::delete);return "redirect:/perfil";}
    @PostMapping("/perfil/excluir")@Transactional String excludeAccount(Authentication auth,HttpServletRequest request,RedirectAttributes redirect){
        User u=current.require(auth);
        if(u.getRole()!=DomainTypes.Role.CUSTOMER){redirect.addFlashAttribute("error","Contas administrativas não podem ser excluídas por aqui.");return "redirect:/perfil";}
        addresses.findByUserIdOrderByPrimaryAddressDescIdDesc(u.getId()).forEach(addresses::delete);
        resetTokens.deleteByUser_Id(u.getId());
        u.setName("Cliente removido");u.setEmail("removido+"+UUID.randomUUID()+"@exemplo.local");u.setPhone("");
        u.setPasswordHash(encoder.encode(UUID.randomUUID().toString()));u.setActive(false);users.save(u);
        var session=request.getSession(false);if(session!=null)session.invalidate();
        return "redirect:/?conta-removida=1";
    }
}
