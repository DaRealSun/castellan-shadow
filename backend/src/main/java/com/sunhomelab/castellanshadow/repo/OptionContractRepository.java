package com.sunhomelab.castellanshadow.repo;

import com.sunhomelab.castellanshadow.domain.OptionContract;
import com.sunhomelab.castellanshadow.domain.OptionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface OptionContractRepository extends JpaRepository<OptionContract, Long> {

    Optional<OptionContract> findByUnderlyingTickerAndExpirationAndStrikeAndOptionType(
        String underlyingTicker, LocalDate expiration, BigDecimal strike, OptionType optionType);
}
