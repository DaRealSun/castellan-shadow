package com.sunhomelab.castellanshadow.etl;

import com.sunhomelab.castellanshadow.domain.Etf;
import com.sunhomelab.castellanshadow.domain.EtlRun;
import com.sunhomelab.castellanshadow.repo.EtfRepository;
import com.sunhomelab.castellanshadow.repo.EtlRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Drives the daily ETL: pull holdings for Castellan funds, then prices for everything held. */
@Component
public class EtlOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(EtlOrchestrator.class);
    private static final ZoneId ET = ZoneId.of("America/New_York");

    private final EtfRepository etfRepo;
    private final HoldingsIngestService holdingsIngest;
    private final PricesIngestService pricesIngest;
    private final EtlRunRepository runRepo;
    private final boolean enabled;

    public EtlOrchestrator(EtfRepository etfRepo,
                           HoldingsIngestService holdingsIngest,
                           PricesIngestService pricesIngest,
                           EtlRunRepository runRepo,
                           @Value("${castellan.etl.enabled:true}") boolean enabled) {
        this.etfRepo = etfRepo;
        this.holdingsIngest = holdingsIngest;
        this.pricesIngest = pricesIngest;
        this.runRepo = runRepo;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${castellan.etl.holdings-cron}", zone = "America/New_York")
    public void runHoldings() {
        if (!enabled) return;
        runJob("holdings", this::ingestAllHoldings);
    }

    @Scheduled(cron = "${castellan.etl.prices-cron}", zone = "America/New_York")
    public void runPrices() {
        if (!enabled) return;
        runJob("prices", () -> ingestAllPrices(LocalDate.now(ET)));
    }

    /** Public entrypoint so a manual /admin/etl/run can trigger the same code path. */
    public int ingestAllHoldings() {
        int total = 0;
        for (Etf etf : etfRepo.findByCastellanFundTrue()) {
            try {
                total += holdingsIngest.ingest(etf.getTicker()).rowsUpserted();
            } catch (Exception e) {
                log.error("Holdings ingest failed for {}: {}", etf.getTicker(), e.getMessage(), e);
            }
        }
        return total;
    }

    public int ingestAllPrices(LocalDate today) {
        Set<String> tickers = priceUniverse();
        int total = 0;
        for (String t : tickers) {
            try {
                total += pricesIngest.catchUp(t, today);
            } catch (Exception e) {
                log.error("Price ingest failed for {}: {}", t, e.getMessage(), e);
            }
        }
        return total;
    }

    private Set<String> priceUniverse() {
        // All ETFs themselves (for NAV) + every underlying ticker we currently hold
        Set<String> tickers = etfRepo.findAll().stream()
            .map(Etf::getTicker)
            .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        // Note: we'd add underlying tickers via a query on holdings; deferred to next iteration
        // to keep the first run small and predictable.
        return tickers;
    }

    private void runJob(String name, JobBody body) {
        EtlRun run = runRepo.save(EtlRun.builder()
            .jobName(name)
            .startedAt(Instant.now())
            .status("RUNNING")
            .build());
        try {
            int rows = body.run();
            run.setRowsOut(rows);
            run.setStatus("OK");
        } catch (Exception e) {
            run.setStatus("ERROR");
            run.setErrorMsg(e.getMessage());
            log.error("ETL job {} failed", name, e);
        } finally {
            run.setFinishedAt(Instant.now());
            runRepo.save(run);
        }
    }

    @FunctionalInterface
    private interface JobBody { int run() throws Exception; }
}
