package br.com.pratoja.pratoja.config;

import br.com.pratoja.pratoja.domain.DomainTypes;
import br.com.pratoja.pratoja.repository.UserRepository;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean UserDetailsService userDetailsService(UserRepository users) {
        return email -> users.findByEmailIgnoreCase(email)
                .map(u -> User.withUsername(u.getEmail()).password(u.getPasswordHash())
                        .roles(u.getRole().name()).disabled(!u.isActive()).build())
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
    }

    @Bean AuthenticationSuccessHandler successHandler() {
        return (request, response, authentication) -> {
            boolean admin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + DomainTypes.Role.ADMIN));
            response.sendRedirect(admin ? "/admin" : "/cardapio");
        };
    }

    @Bean SecurityFilterChain security(HttpSecurity http, AuthenticationSuccessHandler successHandler) throws Exception {
        http.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/admin/login", "/cadastro", "/recuperar-senha", "/redefinir-senha", "/cardapio", "/produto/**", "/monte-seu-prato", "/css/**", "/js/**", "/images/**", "/uploads/**", "/error").permitAll()
                .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/checkout", "/pedidos/**", "/historico", "/perfil/**", "/api/orders/**").hasAnyRole("CUSTOMER", "ADMIN")
                .anyRequest().permitAll())
            .formLogin(form -> form.loginPage("/login").loginProcessingUrl("/login")
                    .usernameParameter("email").passwordParameter("password")
                    .successHandler(successHandler).failureUrl("/login?error").permitAll())
            .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/").invalidateHttpSession(true).deleteCookies("JSESSIONID"))
            .sessionManagement(session -> session.sessionFixation(fix -> fix.migrateSession()))
            .exceptionHandling(errors -> errors.accessDeniedHandler((req, res, ex) -> res.sendRedirect("/?acesso-negado")));
        return http.build();
    }
}
