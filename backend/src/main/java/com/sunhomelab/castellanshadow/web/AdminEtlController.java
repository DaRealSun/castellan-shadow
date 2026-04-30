package com.sunhomelab.castellanshadow.web;

import com.sunhomelab.castellanshadow.domain.Etf;
import com.sunhomelab.castellanshadow.etl.EtlOrchestrator;
import com.sunhomelab.castellanshadow.etl.HoldingsFetcher;
import com.sunhomelab.castellanshadow.etl.HoldingsIngestService;
import com.sunhomelab.castellanshadow.etl.NameTickerResolver;
import com.sunhomelab.castellanshadow.etl.PartFParser;
import com.sunhomelab.castellanshadow.repo.EtfRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

/** Manual ETL triggers for local dev and one-shot backfills. */
@RestController
@RequestMapping("/api/admin/etl")
public class AdminEtlController {

    private static final ZoneId ET = ZoneId.of("America/New_York");

    private final EtlOrchestrator orchestrator;
    private final PartFParser partFParser;
    private final HoldingsIngestService ingestService;
    private final EtfRepository etfRepo;
    private final NameTickerResolver tickerResolver;

    public AdminEtlController(EtlOrchestrator orchestrator,
                              PartFParser partFParser,
                              HoldingsIngestService ingestService,
                              EtfRepository etfRepo,
                              NameTickerResolver tickerResolver) {
        this.orchestrator = orchestrator;
        this.partFParser = partFParser;
        this.ingestService = ingestService;
        this.etfRepo = etfRepo;
        this.tickerResolver = tickerResolver;
    }

    @PostMapping("/holdings")
    public Map<String, Object> runHoldings() {
        int rows = orchestrator.ingestAllHoldings();
        return Map.of("rowsUpserted", rows);
    }

    @PostMapping("/prices")
    public Map<String, Object> runPrices(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate today) {
        LocalDate d = today != null ? today : LocalDate.now(ET);
        int rows = orchestrator.ingestAllPrices(d);
        return Map.of("today", d.toString(), "rowsUpserted", rows);
    }

    /**
     * One-shot Part F PDF ingestion. Pass the fund ticker and the local file path
     * (relative to the backend working dir or absolute), e.g.
     *   POST /api/admin/etl/partf?ticker=CTEF&path=../docs/sources/CTEF_part_f_q3.pdf
     */
    @PostMapping("/partf")
    public ResponseEntity<Map<String, Object>> runPartF(
            @RequestParam String ticker,
            @RequestParam String path) throws IOException {

        Optional<Etf> etfOpt = etfRepo.findByTicker(ticker.toUpperCase());
        if (etfOpt.isEmpty()) return ResponseEntity.notFound().build();

        Optional<HoldingsFetcher.Snapshot> snap = partFParser.parseFile(Path.of(path));
        if (snap.isEmpty()) {
            return ResponseEntity.unprocessableEntity()
                .body(Map.of("error", "could not parse Part F at " + path));
        }

        HoldingsIngestService.IngestResult res =
            ingestService.persistSnapshot(etfOpt.get(), snap.get());
        return ResponseEntity.ok(Map.of(
            "ticker",    res.ticker(),
            "asOfDate",  String.valueOf(res.asOfDate()),
            "rowsUpserted", res.rowsUpserted()
        ));
    }

    @PostMapping("/resolve-tickers")
    public Map<String, Object> resolveTickers() {
        NameTickerResolver.BackfillResult r = tickerResolver.backfillAll();
        return Map.of(
            "learnedMappings", r.learned(),
            "rowsUpdated",     r.updated(),
            "unmappedRows",    r.unmapped()
        );
    }
}
