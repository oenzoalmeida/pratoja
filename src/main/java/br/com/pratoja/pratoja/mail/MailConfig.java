package br.com.pratoja.pratoja.mail;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Toda a infraestrutura de e-mail só existe quando pratoja.mail.enabled=true.
 * - Scheduler do outbox com fixDelay configurável (default 30s).
 * - TemplateEngine dedicado em modo TEXTO para templates/email/*.html (não interfere no Thymeleaf web).
 */
@Configuration
@ConditionalOnProperty(prefix = "pratoja.mail", name = "enabled", havingValue = "true")
@EnableScheduling
public class MailConfig {

    @Bean
    TemplateEngine mailTemplateEngine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/email/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.TEXT);
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCheckExistence(true);
        resolver.setOrder(10);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    @Bean
    SmtpMailPort smtpMailPort(ObjectProvider<JavaMailSender> sender,
                              TemplateEngine mailTemplateEngine,
                              @Value("${pratoja.mail.from:notificacoes@pratoja.app}") String from) {
        return new SmtpMailPort(sender.getIfAvailable(), mailTemplateEngine, from);
    }

    @Bean
    MailOutboxScheduler mailOutboxScheduler(MailService mailService) {
        return new MailOutboxScheduler(mailService);
    }

    /** Dispara o processamento do outbox periodicamente. */
    static class MailOutboxScheduler {
        private final MailService mailService;

        MailOutboxScheduler(MailService mailService) { this.mailService = mailService; }

        @Scheduled(fixedDelayString = "${pratoja.mail.poll-interval-ms:30000}",
                   initialDelayString = "${pratoja.mail.poll-interval-ms:30000}")
        void tick() {
            mailService.processDue();
        }
    }
}
