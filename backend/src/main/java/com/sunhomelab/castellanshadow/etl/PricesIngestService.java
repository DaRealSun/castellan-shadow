package com.sunhomelab.castellanshadow.etl;

import com.sunhomelab.castellanshadow.domain.PriceBar;
import com.sunhomelab.castellanshadow.repo.PriceBarRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class PricesIngestService {

    private static final Logger log = LoggerFactory.getLogger(PricesIngestService.class);

    private final PriceFetcher fetcher;
    private final PriceBarRepository priceRepo;

    public PricesIngestService(PriceFetcher fetcher, PriceBarRepository priceRepo) {
        this.fetcher = fetcher;
        this.priceRepo = priceRepo;
    }

    /** Fetches and upserts daily bars between [start, end] (inclusive). */
    @Transactional
    public int ingest(String ticker, LocalDate start, LocalDate end) {
        List<PriceFetcher.Bar> bars = fetcher.fetchDaily(ticker, start, end);
        int upserted = 0;
        for (PriceFetcher.Bar b : bars) {
            PriceBar existing = priceRepo.findByTickerAndBarDate(b.ticker(), b.date())
                .orElseGet(() -> PriceBar.builder()
                    .ticker(b.ticker())
                    .barDate(b.date())
                    .build());

            existing.setOpen(b.open());
            existing.setHigh(b.high());
            existing.setLow(b.low());
            existing.setClose(b.close());
            existing.setAdjClose(b.adjClose());
            existing.setVolume(b.volume());

            priceRepo.save(existing);
            upserted++;
        }
        log.info("Upserted {} price bars for {} {}..{}", upserted, ticker, start, end);
        return upserted;
    }

    /** Fetches missing bars from the last persisted date to today. */
    @Transactional
    public int catchUp(String ticker, LocalDate today) {
        LocalDate start = priceRepo.findLatestDate(ticker)
            .map(d -> d.plusDays(1))
            .orElse(today.minusYears(2));   // bootstrap window
        if (!start.isBefore(today.plusDays(1))) {
            log.debug("Prices up-to-date for {}: latest={}", ticker, start.minusDays(1));
            return 0;
        }
        return ingest(ticker, start, today);
    }
}
