package com.sunhomelab.castellanshadow.etl;

import com.sunhomelab.castellanshadow.domain.OptionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Pulls a single ETF's published daily holdings. */
public interface HoldingsFetcher {

    Optional<Snapshot> fetch(String etfTicker);

    record Snapshot(LocalDate asOfDate, List<HoldingRow> rows) {}

    /**
     * One position. For options, populate {@code option} so the ingest layer can
     * upsert an {@link com.sunhomelab.castellanshadow.domain.OptionContract} row.
     * {@code shares} is signed: positive for long, negative for written/short.
     */
    record HoldingRow(
        String instrumentSymbol,
        String securityName,
        String cusip,
        String securityType,
        String underlyingTicker,
        BigDecimal shares,
        BigDecimal price,
        BigDecimal marketValue,
        BigDecimal weightPct,
        BigDecimal notionalValue,
        OptionMeta option
    ) {}

    /** Metadata that uniquely identifies an option contract. */
    record OptionMeta(
        String underlyingTicker,
        LocalDate expiration,
        BigDecimal strike,
        OptionType optionType,
        boolean flex,
        String description
    ) {}
}
