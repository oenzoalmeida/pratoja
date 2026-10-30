package br.com.pratoja.pratoja.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** Conta de pagamento da loja (uma por loja): conexao OAuth com o Mercado Pago do dono da loja.
 *  Tokens ficam criptografados (AES-GCM) via servico; nunca em texto claro no banco. */
@Entity @Table(name = "store_payment_accounts")
@Getter @Setter @NoArgsConstructor
public class StorePaymentAccount {
    public enum Provider { MERCADO_PAGO }
    public enum Mode { TEST, PRODUCTION }
    public enum Status { CONNECTED, DISCONNECTED, ERROR }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "store_id", unique = true) private Store store;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Provider provider = Provider.MERCADO_PAGO;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) private Mode mode = Mode.TEST;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Status status = Status.DISCONNECTED;
    @Column(name = "mp_user_id", length = 40) private String mpUserId;
    @Column(name = "access_token_enc", columnDefinition = "TEXT") private String accessTokenEnc;
    @Column(name = "refresh_token_enc", columnDefinition = "TEXT") private String refreshTokenEnc;
    @Column(name = "token_expires_at") private LocalDateTime tokenExpiresAt;
    @Column(name = "public_key", length = 200) private String publicKey;
    @Column(name = "connected_at") private LocalDateTime connectedAt;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();
}
