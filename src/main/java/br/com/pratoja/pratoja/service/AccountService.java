package br.com.pratoja.pratoja.service;
import br.com.pratoja.pratoja.domain.*;
import br.com.pratoja.pratoja.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
@Service @RequiredArgsConstructor
public class AccountService {
    private final UserRepository users;private final PasswordResetTokenRepository tokens;private final PasswordEncoder encoder;
    @Transactional public void register(String name,String email,String phone,String password,String confirm){
        if(name==null||name.strip().length()<3)throw new IllegalArgumentException("Informe seu nome completo.");
        if(email==null||!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))throw new IllegalArgumentException("Informe um e-mail válido.");
        if(phone==null||phone.replaceAll("\\D","").length()<10)throw new IllegalArgumentException("Informe um telefone válido.");
        validatePassword(password,confirm);if(users.existsByEmailIgnoreCase(email))throw new IllegalArgumentException("Este e-mail já está cadastrado.");
        User u=new User();u.setName(name.strip());u.setEmail(email.strip().toLowerCase());u.setPhone(phone.strip());u.setPasswordHash(encoder.encode(password));u.setRole(DomainTypes.Role.CUSTOMER);users.save(u);
    }
    @Transactional public String requestReset(String email){Optional<User> user=users.findByEmailIgnoreCase(email==null?"":email);if(user.isEmpty())return null;String raw=UUID.randomUUID()+""+UUID.randomUUID();PasswordResetToken t=new PasswordResetToken();t.setUser(user.get());t.setTokenHash(hash(raw));t.setExpiresAt(LocalDateTime.now().plusMinutes(30));tokens.save(t);return raw;}
    @Transactional public void reset(String raw,String password,String confirm){validatePassword(password,confirm);PasswordResetToken t=tokens.findByTokenHash(hash(raw)).filter(x->x.getUsedAt()==null&&x.getExpiresAt().isAfter(LocalDateTime.now())).orElseThrow(()->new IllegalArgumentException("Link inválido ou expirado."));t.getUser().setPasswordHash(encoder.encode(password));t.setUsedAt(LocalDateTime.now());}
    private void validatePassword(String p,String c){if(p==null||p.length()<8||!p.matches(".*[A-Z].*")||!p.matches(".*[a-z].*")||!p.matches(".*\\d.*"))throw new IllegalArgumentException("A senha deve ter 8 caracteres, com maiúscula, minúscula e número.");if(!p.equals(c))throw new IllegalArgumentException("As senhas não coincidem.");}
    private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
