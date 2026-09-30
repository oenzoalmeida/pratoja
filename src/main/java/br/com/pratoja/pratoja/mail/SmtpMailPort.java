package br.com.pratoja.pratoja.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

/**
 * Envio SMTP real. É criado somente quando pratoja.mail.enabled=true.
 * O corpo do e-mail vem de templates Thymeleaf em modo texto (templates/email/*.html),
 * com branding textual simples — sem imagens externas.
 */
@RequiredArgsConstructor
@Slf4j
public class SmtpMailPort implements MailPort {
    private final JavaMailSender sender;
    private final TemplateEngine mailTemplateEngine;
    private final String from;

    @Override
    public void send(String to, String template, Map<String, Object> vars) {
        Object subject = vars.getOrDefault("subject", "PratoJá");
        Context ctx = new Context();
        vars.forEach(ctx::setVariable);
        String body = mailTemplateEngine.process("email/" + template, ctx);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(String.valueOf(subject));
        message.setText(body);
        sender.send(message);
        log.info("E-mail '{}' enfileirado no SMTP para {}", template, to);
    }
}
