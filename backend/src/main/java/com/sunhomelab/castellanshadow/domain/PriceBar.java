package com.sunhomelab.castellanshadow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
    name = "price_bar",
    uniqueConstraints = @UniqueConstraint(name = "uk_price", columnNames = {"ticker", "bar_date"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceBar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String ticker;

    @Column(name = "bar_date", nullable = false)
    private LocalDate barDate;

    @Column(name = "open_px", precision = 14, scale = 4)
    private BigDecimal open;

    @Column(name = "high_px", precision = 14, scale = 4)
    private BigDecimal high;

    @Column(name = "low_px", precision = 14, scale = 4)
    private BigDecimal low;

    @Column(name = "close_px", nullable = false, precision = 14, scale = 4)
    private BigDecimal close;

    @Column(name = "adj_close", precision = 14, scale = 4)
    private BigDecimal adjClose;

    private Long volume;
}
