package br.com.pratoja.pratoja.config;

import br.com.pratoja.pratoja.domain.DomainTypes;
import br.com.pratoja.pratoja.repository.UserRepository;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
public class SecurityConfig {
    @Value("${pratoja.security.remember-me-key}") private String rememberMeKey;
    @Value("${pratoja.security.remember-me-validity-days}") private int rememberMeValidityDays;

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    // "Manter conectado": tokens persistentes no banco (tabela persistent_logins, V13).
    // Troca de senha invalida os tokens; logout remove o token da sessão atual.
    @Bean PersistentTokenRepository persistentTokenRepository(DataSource dataSource) {
        JdbcTokenRepositoryImpl tokens = new JdbcTokenRepositoryImpl();
        tokens.setDataSource(dataSource);
        return tokens;
    }

    @Bean UserDetailsService userDetailsService(UserRepository users) {
        return email -> users.findByEmailIgnoreCase(email)
                .map(u -> User.withUsername(u.getEmail()).password(u.getPasswordHash())
                        .roles(u.getRole().name()).disabled(!u.isActive()).build())
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
    }

    @Bean AuthenticationSuccessHandler successHandler() {
        return (request, response, authentication) -> {
            boolean admin = authentication.getAuthorities().stream().anyMatch(a ->
                    a.getAuthority().equals("ROLE_" + DomainTypes.Role.STORE_ADMIN) || a.getAuthority().equals("ROLE_" + DomainTypes.Role.PLATFORM_ADMIN));
            response.sendRedirect(admin ? "/admin" : "/");
        };
    }

    @Bean SecurityFilterChain security(HttpSecurity http, AuthenticationSuccessHandler successHandler,
                                       AuthRateLimiter authRateLimiter, ClientIpResolver ipResolver,
                                       PersistentTokenRepository persistentTokenRepository) throws Exception {
        http.addFilterBefore(new LoginRateLimitFilter(authRateLimiter, ipResolver), UsernamePasswordAuthenticationFilter.class)
            .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                // Webhook do provedor de pagamento nao tem sessao/CSRF; protegido por assinatura HMAC propria.
                .ignoringRequestMatchers("/webhooks/mercadopago"))
            .authorizeHttpRequests(auth -> auth
                // Rotas privadas da loja devem preceder as rotas públicas de catálogo e carrinho
                .requestMatchers("/loja/*/checkout", "/loja/*/pedidos/**", "/loja/*/api/orders/**").hasAnyRole("CUSTOMER", "STORE_ADMIN", "PLATFORM_ADMIN")
                .requestMatchers("/", "/login", "/admin/login", "/cadastro", "/recuperar-senha", "/redefinir-senha", "/loja/**", "/cardapio", "/produto/**", "/monte-seu-prato", "/css/**", "/js/**", "/images/**", "/uploads/**", "/error", "/carrinho", "/webhooks/mercadopago", "/termos", "/privacidade", "/robots.txt", "/favicon.ico").permitAll()
                .requestMatchers("/trocar-senha").authenticated()
                .requestMatchers("/platform/**").hasRole("PLATFORM_ADMIN") // Painel da plataforma (Fase 5)
                .requestMatchers("/admin/**", "/api/admin/**").hasAnyRole("STORE_ADMIN", "PLATFORM_ADMIN")
                .requestMatchers("/checkout", "/pedidos/**", "/historico", "/perfil/**", "/api/orders/**").hasAnyRole("CUSTOMER", "STORE_ADMIN", "PLATFORM_ADMIN")
                // Fail closed: novos endpoints precisam de autorização explícita.
                .anyRequest().denyAll())
            .formLogin(form -> form.loginPage("/login").loginProcessingUrl("/login")
                    .usernameParameter("email").passwordParameter("password")
                    .successHandler(successHandler).failureUrl("/login?error").permitAll())
            .rememberMe(remember -> remember
                    .tokenRepository(persistentTokenRepository)
                    .tokenValiditySeconds(rememberMeValidityDays * 24 * 60 * 60)
                    .rememberMeCookieName("PRATOJA_REMEMBER_ME")
                    .key(rememberMeKey))
            .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/").invalidateHttpSession(true).deleteCookies("JSESSIONID", "PRATOJA_REMEMBER_ME"))
            .sessionManagement(session -> session.sessionFixation(fix -> fix.migrateSession()))
            .exceptionHandling(errors -> errors.accessDeniedHandler((req, res, ex) -> res.sendRedirect("/?acesso-negado")));
        return http.build();
    }
}
