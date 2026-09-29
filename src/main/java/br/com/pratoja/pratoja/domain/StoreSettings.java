package br.com.pratoja.pratoja.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity @Table(name = "store_settings")
@Getter @Setter @NoArgsConstructor
public class StoreSettings {
    public static final long SINGLETON_ID = 1L;
    @Id private Long id;
    @Column(nullable = false, length = 120) private String name;
    @Column(length = 200) private String slogan;
    @Column(length = 600) private String description;
    @Column(length = 30) private String phone;
    @Column(length = 30) private String whatsapp;
    @Column(length = 120) private String email;
    @Column(name = "address_street", length = 150) private String addressStreet;
    @Column(name = "address_number", length = 20) private String addressNumber;
    @Column(name = "address_complement", length = 100) private String addressComplement;
    @Column(name = "address_neighborhood", length = 100) private String addressNeighborhood;
    @Column(name = "address_city", length = 100) private String addressCity;
    @Column(name = "address_state", length = 2) private String addressState;
    @Column(name = "address_zip", length = 20) private String addressZip;
    @Column(name = "opening_hours", columnDefinition = "text") private String openingHours;
    @Column(name = "hero_title", length = 200) private String heroTitle;
    @Column(name = "hero_subtitle", length = 400) private String heroSubtitle;
    @Column(name = "delivery_time_note", length = 120) private String deliveryTimeNote;
    @Column(name = "delivery_fee", nullable = false, precision = 10, scale = 2) private BigDecimal deliveryFee;
    @Column(name = "brand_primary", length = 7) private String brandPrimary;
    @Column(name = "brand_primary_dark", length = 7) private String brandPrimaryDark;
    @Column(name = "logo_path", length = 300) private String logoPath;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
}
