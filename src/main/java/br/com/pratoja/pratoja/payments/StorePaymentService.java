package br.com.pratoja.pratoja.payments;

import br.com.pratoja.pratoja.domain.Store;
import br.com.pratoja.pratoja.domain.StorePaymentAccount;
import br.com.pratoja.pratoja.repository.StorePaymentAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/** Conexao OAuth Mercado Pago por loja: cada lojista conecta a PROPRIA conta e os tokens
 *  ficam criptografados por loja. Isolamento multitenant: toda consulta passa pelo store_id. */
@Service
public class StorePaymentService {
    private final StorePaymentAccountRepository accounts;
    private final MercadoPagoClient client;
    private final PaymentSecretsService secrets;
    private final MercadoPagoProperties props;

    public StorePaymentService(StorePaymentAccountRepository accounts, MercadoPagoClient client,
                               PaymentSecretsService secrets, MercadoPagoProperties props) {
        this.accounts = accounts;
        this.client = client;
        this.secrets = secrets;
        this.props = props;
    }

    public boolean isConnected(Long storeId) {
        return accounts.findByStore_Id(storeId)
                .map(a -> a.getStatus() == StorePaymentAccount.Status.CONNECTED)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Optional<StorePaymentAccount> accountOf(Long storeId) { return accounts.findByStore_Id(storeId); }

    public boolean oauthReady() { return props.oauthConfigured() && secrets.available(); }

    public String callbackUrl() { return props.publicBaseUrl + "/admin/pagamentos/mercado-pago/callback"; }

    /** URL de autorizacao do Mercado Pago; o state aleatorio e verificado no callback (anti-CSRF). */
    public String connectUrl(String state) {
        return props.authBaseUrl + "/authorization?client_id=" + url(props.clientId)
                + "&response_type=code&platform_id=mp"
                + "&redirect_uri=" + url(callbackUrl())
                + "&state=" + url(state);
    }

    public String newState() { return UUID.randomUUID().toString(); }

    @Transactional
    public void connect(Store store, String code) {
        if (!oauthReady()) throw new IllegalStateException("Integracao Mercado Pago nao configurada no servidor.");
        MercadoPagoClient.MpToken token = client.exchangeCode(code, callbackUrl());
        StorePaymentAccount account = accounts.findByStore_Id(store.getId()).orElseGet(StorePaymentAccount::new);
        account.setStore(store);
        account.setProvider(StorePaymentAccount.Provider.MERCADO_PAGO);
        account.setMode(parseMode(props.defaultMode));
        account.setStatus(StorePaymentAccount.Status.CONNECTED);
        account.setMpUserId(token.userId() == null ? null : String.valueOf(token.userId()));
        account.setPublicKey(token.publicKey());
        account.setAccessTokenEnc(secrets.encrypt(token.accessToken()));
        if (token.refreshToken() != null) account.setRefreshTokenEnc(secrets.encrypt(token.refreshToken()));
        account.setTokenExpiresAt(LocalDateTime.now().plusSeconds(Math.max(token.expiresInSeconds() - 300, 60)));
        account.setConnectedAt(LocalDateTime.now());
        account.setUpdatedAt(LocalDateTime.now());
        accounts.save(account);
    }

    @Transactional
    public void disconnect(Store store) {
        StorePaymentAccount account = accounts.findByStore_Id(store.getId()).orElse(null);
        if (account == null) return;
        account.setStatus(StorePaymentAccount.Status.DISCONNECTED);
        account.setAccessTokenEnc(null);
        account.setRefreshTokenEnc(null);
        account.setTokenExpiresAt(null);
        account.setPublicKey(null);
        account.setUpdatedAt(LocalDateTime.now());
        accounts.save(account);
    }

    /** Access token valido da loja; renova via refresh token quando expirado. */
    @Transactional
    public String accessToken(Long storeId) {
        StorePaymentAccount account = accounts.findByStore_Id(storeId)
                .filter(a -> a.getStatus() == StorePaymentAccount.Status.CONNECTED)
                .orElseThrow(() -> new IllegalStateException("Loja sem conta de pagamento conectada."));
        if (account.getAccessTokenEnc() == null) throw new IllegalStateException("Loja sem token de pagamento.");
        if (account.getTokenExpiresAt() != null && account.getTokenExpiresAt().isBefore(LocalDateTime.now())
                && account.getRefreshTokenEnc() != null) {
            MercadoPagoClient.MpToken token = client.refreshToken(secrets.decrypt(account.getRefreshTokenEnc()));
            account.setAccessTokenEnc(secrets.encrypt(token.accessToken()));
            if (token.refreshToken() != null) account.setRefreshTokenEnc(secrets.encrypt(token.refreshToken()));
            account.setTokenExpiresAt(LocalDateTime.now().plusSeconds(Math.max(token.expiresInSeconds() - 300, 60)));
            account.setUpdatedAt(LocalDateTime.now());
            accounts.save(account);
        }
        return secrets.decrypt(account.getAccessTokenEnc());
    }

    /** Exibicao no admin sem expor dados sensiveis. */
    public String maskedUserId(StorePaymentAccount account) {
        String id = account.getMpUserId();
        return id == null ? "-" : "MP #" + id;
    }

    private StorePaymentAccount.Mode parseMode(String value) {
        try { return StorePaymentAccount.Mode.valueOf(value == null ? "TEST" : value.trim().toUpperCase()); }
        catch (Exception e) { return StorePaymentAccount.Mode.TEST; }
    }

    private String url(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
