package com.sunhomelab.castellanshadow.etl;

import com.sunhomelab.castellanshadow.domain.Etf;
import com.sunhomelab.castellanshadow.domain.Holding;
import com.sunhomelab.castellanshadow.domain.OptionContract;
import com.sunhomelab.castellanshadow.domain.SecurityType;
import com.sunhomelab.castellanshadow.repo.EtfRepository;
import com.sunhomelab.castellanshadow.repo.HoldingRepository;
import com.sunhomelab.castellanshadow.repo.OptionContractRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
public class HoldingsIngestService {

    private static final Logger log = LoggerFactory.getLogger(HoldingsIngestService.class);

    private final HoldingsFetcher fetcher;
    private final EtfRepository etfRepo;
    private final HoldingRepository holdingRepo;
    private final OptionContractRepository optionRepo;

    public HoldingsIngestService(HoldingsFetcher fetcher,
                                 EtfRepository etfRepo,
                                 HoldingRepository holdingRepo,
                                 OptionContractRepository optionRepo) {
        this.fetcher = fetcher;
        this.etfRepo = etfRepo;
        this.holdingRepo = holdingRepo;
        this.optionRepo = optionRepo;
    }

    @Transactional
    public IngestResult ingest(String ticker) {
        Etf etf = etfRepo.findByTicker(ticker)
            .orElseThrow(() -> new IllegalArgumentException("Unknown ETF: " + ticker));

        Optional<HoldingsFetcher.Snapshot> snap = fetcher.fetch(ticker);
        if (snap.isEmpty()) {
            log.info("No snapshot returned for {}", ticker);
            return new IngestResult(ticker, null, 0);
        }
        return persistSnapshot(etf, snap.get());
    }

    /** Persist a pre-built snapshot (used by Part F PDF ingestion). */
    @Transactional
    public IngestResult persistSnapshot(Etf etf, HoldingsFetcher.Snapshot snap) {
        LocalDate asOf = snap.asOfDate();
        int upserted = 0;
        for (HoldingsFetcher.HoldingRow r : snap.rows()) {
            Long optionId = r.option() != null ? upsertOption(r.option()).getId() : null;

            Holding existing = holdingRepo
                .findByEtfIdAndAsOfDateAndInstrumentSymbol(etf.getId(), asOf, r.instrumentSymbol())
                .orElseGet(() -> Holding.builder()
                    .etfId(etf.getId())
                    .asOfDate(asOf)
                    .instrumentSymbol(r.instrumentSymbol())
                    .build());

            existing.setSecurityType(SecurityType.valueOf(r.securityType()));
            existing.setSecurityName(r.securityName());
            existing.setCusip(r.cusip());
            existing.setUnderlyingTicker(r.underlyingTicker());
            existing.setShares(r.shares());
            existing.setPrice(r.price());
            existing.setMarketValue(r.marketValue());
            existing.setWeightPct(r.weightPct());
            existing.setNotionalValue(r.notionalValue());
            existing.setOptionContractId(optionId);

            holdingRepo.save(existing);
            upserted++;
        }
        log.info("Upserted {} holdings for {} on {}", upserted, etf.getTicker(), asOf);
        return new IngestResult(etf.getTicker(), asOf, upserted);
    }

    private OptionContract upsertOption(HoldingsFetcher.OptionMeta meta) {
        return optionRepo
            .findByUnderlyingTickerAndExpirationAndStrikeAndOptionType(
                meta.underlyingTicker(), meta.expiration(), meta.strike(), meta.optionType())
            .orElseGet(() -> optionRepo.save(OptionContract.builder()
                .underlyingTicker(meta.underlyingTicker())
                .expiration(meta.expiration())
                .strike(meta.strike())
                .optionType(meta.optionType())
                .contractSize(100)
                .flex(meta.flex())
                .description(meta.description())
                .build()));
    }

    public record IngestResult(String ticker, LocalDate asOfDate, int rowsUpserted) {}
}
