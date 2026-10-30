package br.com.pratoja.pratoja.payments;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/** Criptografia simetrica (AES-256-GCM) dos tokens OAuth das lojas.
 *  Falha fechada: sem PRATOJA_PAYMENTS_CRYPTO_KEY valido, nao criptografa nem descriptografa. */
@Service
public class PaymentSecretsService {
    private final MercadoPagoProperties props;
    private final SecureRandom random = new SecureRandom();

    public PaymentSecretsService(MercadoPagoProperties props) { this.props = props; }

    public boolean available() { return key() != null; }

    public String encrypt(String plain) {
        SecretKeySpec key = key();
        if (key == null) throw new IllegalStateException("Criptografia de pagamentos nao configurada.");
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) { throw new IllegalStateException("Falha ao criptografar segredo.", e); }
    }

    public String decrypt(String encoded) {
        SecretKeySpec key = key();
        if (key == null) throw new IllegalStateException("Criptografia de pagamentos nao configurada.");
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            byte[] iv = new byte[12];
            byte[] encrypted = new byte[all.length - 12];
            System.arraycopy(all, 0, iv, 0, 12);
            System.arraycopy(all, 12, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("Falha ao descriptografar segredo.", e); }
    }

    private SecretKeySpec key() {
        String value = props.cryptoKey;
        if (value == null || value.isBlank()) return null;
        try {
            byte[] raw = Base64.getDecoder().decode(value);
            if (raw.length != 32) return null;
            return new SecretKeySpec(raw, "AES");
        } catch (IllegalArgumentException e) { return null; }
    }
}
