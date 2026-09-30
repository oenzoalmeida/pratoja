package br.com.pratoja.pratoja.mail;

/** Ciclo de vida de um e-mail no outbox. */
public enum MailOutboxStatus {
    /** Aguardando primeiro envio. */
    PENDING,
    /** Enviado com sucesso. */
    SENT,
    /** Tentativa falhou; aguardando retry (next_attempt_at com backoff exponencial). */
    FAILED,
    /** Esgotou as 5 tentativas; não será reprocessado. */
    DEAD
}
