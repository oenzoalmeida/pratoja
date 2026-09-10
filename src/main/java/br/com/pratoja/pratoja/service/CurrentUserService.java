package br.com.pratoja.pratoja.service;
import br.com.pratoja.pratoja.domain.User;
import br.com.pratoja.pratoja.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor
public class CurrentUserService {
    private final UserRepository users;
    public User require(Authentication auth){if(auth==null||!auth.isAuthenticated()||"anonymousUser".equals(auth.getPrincipal()))throw new IllegalStateException("Faça login para continuar.");return users.findByEmailIgnoreCase(auth.getName()).orElseThrow();}
}
