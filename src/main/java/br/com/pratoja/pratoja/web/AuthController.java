package br.com.pratoja.pratoja.web;
import br.com.pratoja.pratoja.config.ClientIpResolver;
import br.com.pratoja.pratoja.config.RecoveryRateLimiter;
import br.com.pratoja.pratoja.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller @RequiredArgsConstructor @Slf4j
public class AuthController {
    private final AccountService accounts;private final br.com.pratoja.pratoja.service.PasswordRecoveryService recovery;
    private final RecoveryRateLimiter rateLimiter;private final ClientIpResolver ipResolver;
    @Value("${pratoja.demo-mode:true}")private boolean demo;@Value("${pratoja.demo-reset-email:cliente@pratoja.com.br}")private String demoResetEmail;@Value("${pratoja.mail.enabled:false}")private boolean mailEnabled;
    @GetMapping("/login")String login(){return "auth/login";}@GetMapping("/admin/login")String adminLogin(){return "auth/admin-login";}@GetMapping("/cadastro")String register(){return "auth/register";}
    @PostMapping("/cadastro")String register(@RequestParam String name,@RequestParam String email,@RequestParam String phone,@RequestParam String password,@RequestParam String confirmPassword,Model model){try{accounts.register(name,email,phone,password,confirmPassword);model.addAttribute("success","Cadastro realizado. Agora entre com sua conta.");return "auth/login";}catch(IllegalArgumentException e){model.addAttribute("error",e.getMessage());model.addAttribute("name",name);model.addAttribute("email",email);model.addAttribute("phone",phone);return "auth/register";}}
    @GetMapping("/recuperar-senha")String forgot(){return "auth/forgot";}
    // Rate limit SEMPRE ativo (independe de pratoja.mail.enabled): conta TENTATIVAS por IP e por e-mail.
    // Ao estourar, devolve a MESMA view/mensagem genérica de sucesso (200) sem processar/enfileirar nada —
    // responder 429 (ou mensagem diferente) revelaria que o limite agiu, criando oráculo de enumeração.
    @PostMapping("/recuperar-senha")String forgot(@RequestParam String email,Model model,HttpServletRequest request){
        String ip=ipResolver.resolve(request);
        boolean ipAllowed=rateLimiter.tryAcquireRecoveryIp(ip);
        boolean emailAllowed=ipAllowed&&rateLimiter.tryAcquireEmail(email);
        if(!ipAllowed||!emailAllowed){log.warn("Rate limit de recuperação de senha: tentativa descartada sem gerar token ou enfileirar e-mail (ip={})",ip);return forgotSuccess(model);}
        if(mailEnabled){recovery.startReset(email==null?null:email.strip());model.addAttribute("success","Se o e-mail estiver cadastrado, enviamos as instruções de recuperação.");return "auth/forgot";}
    model.addAttribute("success","Se o e-mail estiver cadastrado, um link de recuperação foi gerado.");String token=null;if(demo){if(email!=null&&email.strip().equalsIgnoreCase(demoResetEmail))token=accounts.requestCustomerReset(email.strip());}else token=accounts.requestReset(email);if(demo&&token!=null)model.addAttribute("demoLink","/redefinir-senha?token="+token);return "auth/forgot";}
    // Resposta de sucesso genérica do modo atual, SEM processar nada: idêntica à resposta de e-mail
    // inexistente (em produção demo-mode=false os corpos são byte a byte iguais).
    private String forgotSuccess(Model model){model.addAttribute("success",mailEnabled?"Se o e-mail estiver cadastrado, enviamos as instruções de recuperação.":"Se o e-mail estiver cadastrado, um link de recuperação foi gerado.");return "auth/forgot";}
    @GetMapping("/redefinir-senha")String reset(@RequestParam String token,Model model){model.addAttribute("token",token);return "auth/reset";}
    // Rate limit por IP também na redefinição (bloqueia força bruta de tokens). Quando limitado, responde
    // exatamente como um token inválido ("Link inválido ou expirado.", 200) sem processar — sem oráculo.
    @PostMapping("/redefinir-senha")String reset(@RequestParam String token,@RequestParam String password,@RequestParam String confirmPassword,Model model,RedirectAttributes redirect,HttpServletRequest request){
        String ip=ipResolver.resolve(request);
        if(!rateLimiter.tryAcquireResetIp(ip)){log.warn("Rate limit de redefinição de senha: tentativa descartada sem validar o token (ip={})",ip);model.addAttribute("token",token);model.addAttribute("error","Link inválido ou expirado.");return "auth/reset";}
        try{accounts.reset(token,password,confirmPassword);redirect.addFlashAttribute("success","Senha redefinida. Entre novamente.");return "redirect:/login";}catch(IllegalArgumentException e){model.addAttribute("token",token);model.addAttribute("error",e.getMessage());return "auth/reset";}}
}
