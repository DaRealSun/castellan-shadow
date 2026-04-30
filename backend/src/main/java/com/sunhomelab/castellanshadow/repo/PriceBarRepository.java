package com.sunhomelab.castellanshadow.repo;

import com.sunhomelab.castellanshadow.domain.PriceBar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PriceBarRepository extends JpaRepository<PriceBar, Long> {

    Optional<PriceBar> findByTickerAndBarDate(String ticker, LocalDate barDate);

    List<PriceBar> findByTickerAndBarDateBetweenOrderByBarDate(
        String ticker, LocalDate start, LocalDate end);

    @Query("SELECT MAX(p.barDate) FROM PriceBar p WHERE p.ticker = :ticker")
    Optional<LocalDate> findLatestDate(String ticker);
}
