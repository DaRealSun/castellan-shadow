package com.sunhomelab.castellanshadow.repo;

import com.sunhomelab.castellanshadow.domain.Etf;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EtfRepository extends JpaRepository<Etf, Long> {
    Optional<Etf> findByTicker(String ticker);
    List<Etf> findByCastellanFundTrue();
}
