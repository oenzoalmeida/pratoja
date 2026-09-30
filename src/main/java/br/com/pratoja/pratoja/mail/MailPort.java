package br.com.pratoja.pratoja.mail;

import java.util.Map;

/**
 * Porta de saída de e-mails transacionais. Implementações rendem o template
 * {@code templates/email/<template>.html} (modo texto) com as variáveis informadas.
 */
public interface MailPort {
    void send(String to, String template, Map<String, Object> vars);
}
