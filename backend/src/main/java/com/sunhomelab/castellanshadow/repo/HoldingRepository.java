package com.sunhomelab.castellanshadow.repo;

import com.sunhomelab.castellanshadow.domain.Holding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, Long> {

    List<Holding> findByEtfIdAndAsOfDateOrderByWeightPctDesc(Long etfId, LocalDate asOfDate);

    Optional<Holding> findByEtfIdAndAsOfDateAndInstrumentSymbol(
        Long etfId, LocalDate asOfDate, String instrumentSymbol);

    @Query("""
           SELECT MAX(h.asOfDate) FROM Holding h WHERE h.etfId = :etfId
           """)
    Optional<LocalDate> findLatestAsOfDate(Long etfId);

    @Query("""
           SELECT DISTINCT h.asOfDate FROM Holding h
           WHERE h.etfId = :etfId
           ORDER BY h.asOfDate DESC
           """)
    List<LocalDate> findAvailableDates(Long etfId);
}
