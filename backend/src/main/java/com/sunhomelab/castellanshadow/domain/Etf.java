package com.sunhomelab.castellanshadow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "etf")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Etf {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String ticker;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "expense_ratio", precision = 6, scale = 4)
    private BigDecimal expenseRatio;

    @Column(name = "inception_date")
    private LocalDate inceptionDate;

    @Column(name = "is_castellan", nullable = false)
    private boolean castellanFund;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
