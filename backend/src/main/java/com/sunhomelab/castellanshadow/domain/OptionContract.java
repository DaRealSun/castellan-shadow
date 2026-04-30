package com.sunhomelab.castellanshadow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
    name = "option_contract",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_option_contract",
        columnNames = {"underlying_ticker", "expiration", "strike", "option_type"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OptionContract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "underlying_ticker", nullable = false, length = 10)
    private String underlyingTicker;

    @Column(nullable = false)
    private LocalDate expiration;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal strike;

    @Enumerated(EnumType.STRING)
    @Column(name = "option_type", nullable = false, length = 4)
    private OptionType optionType;

    @Column(name = "contract_size", nullable = false)
    private Integer contractSize;

    @Column(name = "is_flex", nullable = false)
    private boolean flex;

    @Column(length = 200)
    private String description;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
