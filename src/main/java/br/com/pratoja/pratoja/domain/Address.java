package br.com.pratoja.pratoja.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "addresses")
@Getter @Setter @NoArgsConstructor
public class Address {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private User user;
    @Column(nullable = false, length = 80) private String label;
    @Column(nullable = false, length = 9) private String zipCode;
    @Column(nullable = false, length = 160) private String street;
    @Column(nullable = false, length = 20) private String number;
    @Column(length = 100) private String complement;
    @Column(nullable = false, length = 100) private String neighborhood;
    @Column(nullable = false, length = 100) private String city;
    @Column(nullable = false, length = 2) private String state;
    @Column(nullable = false) private boolean primaryAddress;

    public String formatted() {
        return street + ", " + number + (complement == null || complement.isBlank() ? "" : " - " + complement) +
                ", " + neighborhood + " - " + city + "/" + state + " · " + zipCode;
    }
}
