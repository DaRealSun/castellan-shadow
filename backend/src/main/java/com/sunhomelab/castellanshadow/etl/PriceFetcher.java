package com.sunhomelab.castellanshadow.etl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface PriceFetcher {

    /** Fetches daily OHLCV bars in [start, end] inclusive. May return empty for non-trading days. */
    List<Bar> fetchDaily(String ticker, LocalDate start, LocalDate end);

    record Bar(
        String ticker,
        LocalDate date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal adjClose,
        Long volume
    ) {}
}
