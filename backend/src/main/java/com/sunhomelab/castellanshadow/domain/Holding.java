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
import java.time.LocalDate;

@Entity
@Table(
    name = "holding",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_holding",
        columnNames = {"etf_id", "as_of_date", "instrument_symbol"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "etf_id", nullable = false)
    private Long etfId;

    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "security_type", nullable = false, length = 10)
    private SecurityType securityType;

    @Column(name = "instrument_symbol", nullable = false, length = 40)
    private String instrumentSymbol;

    @Column(name = "underlying_ticker", length = 10)
    private String underlyingTicker;

    @Column(precision = 20, scale = 6)
    private BigDecimal shares;

    @Column(name = "market_value", precision = 20, scale = 2)
    private BigDecimal marketValue;

    @Column(name = "weight_pct", precision = 9, scale = 4)
    private BigDecimal weightPct;

    @Column(name = "notional_value", precision = 20, scale = 2)
    private BigDecimal notionalValue;

    @Column(length = 12)
    private String cusip;

    @Column(name = "security_name", length = 200)
    private String securityName;

    @Column(precision = 14, scale = 4)
    private BigDecimal price;

    @Column(name = "option_contract_id")
    private Long optionContractId;
}
