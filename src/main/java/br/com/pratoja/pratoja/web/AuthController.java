package br.com.pratoja.pratoja.web;
import br.com.pratoja.pratoja.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller @RequiredArgsConstructor
public class AuthController {
    private final AccountService accounts;@Value("${pratoja.demo-mode:true}")private boolean demo;
    @GetMapping("/login")String login(){return "auth/login";}@GetMapping("/admin/login")String adminLogin(){return "auth/admin-login";}@GetMapping("/cadastro")String register(){return "auth/register";}
    @PostMapping("/cadastro")String register(@RequestParam String name,@RequestParam String email,@RequestParam String phone,@RequestParam String password,@RequestParam String confirmPassword,Model model){try{accounts.register(name,email,phone,password,confirmPassword);model.addAttribute("success","Cadastro realizado. Agora entre com sua conta.");return "auth/login";}catch(IllegalArgumentException e){model.addAttribute("error",e.getMessage());model.addAttribute("name",name);model.addAttribute("email",email);model.addAttribute("phone",phone);return "auth/register";}}
    @GetMapping("/recuperar-senha")String forgot(){return "auth/forgot";}
    @PostMapping("/recuperar-senha")String forgot(@RequestParam String email,Model model){String token=accounts.requestReset(email);model.addAttribute("success","Se o e-mail estiver cadastrado, um link de recuperação foi gerado.");if(demo&&token!=null)model.addAttribute("demoLink","/redefinir-senha?token="+token);return "auth/forgot";}
    @GetMapping("/redefinir-senha")String reset(@RequestParam String token,Model model){model.addAttribute("token",token);return "auth/reset";}
    @PostMapping("/redefinir-senha")String reset(@RequestParam String token,@RequestParam String password,@RequestParam String confirmPassword,Model model,RedirectAttributes redirect){try{accounts.reset(token,password,confirmPassword);redirect.addFlashAttribute("success","Senha redefinida. Entre novamente.");return "redirect:/login";}catch(IllegalArgumentException e){model.addAttribute("token",token);model.addAttribute("error",e.getMessage());return "auth/reset";}}
}
