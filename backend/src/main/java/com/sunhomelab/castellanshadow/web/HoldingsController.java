package com.sunhomelab.castellanshadow.web;

import com.sunhomelab.castellanshadow.domain.Etf;
import com.sunhomelab.castellanshadow.domain.Holding;
import com.sunhomelab.castellanshadow.domain.SecurityType;
import com.sunhomelab.castellanshadow.repo.EtfRepository;
import com.sunhomelab.castellanshadow.repo.HoldingRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/etfs/{ticker}/holdings")
public class HoldingsController {

    private final EtfRepository etfRepo;
    private final HoldingRepository holdingRepo;

    public HoldingsController(EtfRepository etfRepo, HoldingRepository holdingRepo) {
        this.etfRepo = etfRepo;
        this.holdingRepo = holdingRepo;
    }

    /** Return a snapshot. If asOf is omitted, returns the latest persisted snapshot. */
    @GetMapping
    public ResponseEntity<HoldingsSnapshot> get(
        @PathVariable String ticker,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {

        Etf etf = etfRepo.findByTicker(ticker.toUpperCase()).orElse(null);
        if (etf == null) return ResponseEntity.notFound().build();

        LocalDate date = (asOf != null) ? asOf
            : holdingRepo.findLatestAsOfDate(etf.getId()).orElse(null);
        if (date == null) {
            return ResponseEntity.ok(new HoldingsSnapshot(etf.getTicker(), null, List.of()));
        }

        List<HoldingDto> rows = holdingRepo
            .findByEtfIdAndAsOfDateOrderByWeightPctDesc(etf.getId(), date)
            .stream().map(HoldingDto::from).toList();
        return ResponseEntity.ok(new HoldingsSnapshot(etf.getTicker(), date, rows));
    }

    @GetMapping("/dates")
    public ResponseEntity<List<LocalDate>> dates(@PathVariable String ticker) {
        return etfRepo.findByTicker(ticker.toUpperCase())
            .map(e -> ResponseEntity.ok(holdingRepo.findAvailableDates(e.getId())))
            .orElse(ResponseEntity.notFound().build());
    }

    public record HoldingsSnapshot(String ticker, LocalDate asOf, List<HoldingDto> holdings) {}

    public record HoldingDto(
        String instrumentSymbol,
        String securityName,
        String cusip,
        SecurityType securityType,
        String underlyingTicker,
        BigDecimal shares,
        BigDecimal price,
        BigDecimal marketValue,
        BigDecimal weightPct,
        BigDecimal notionalValue
    ) {
        static HoldingDto from(Holding h) {
            return new HoldingDto(
                h.getInstrumentSymbol(),
                h.getSecurityName(),
                h.getCusip(),
                h.getSecurityType(),
                h.getUnderlyingTicker(),
                h.getShares(),
                h.getPrice(),
                h.getMarketValue(),
                h.getWeightPct(),
                h.getNotionalValue()
            );
        }
    }
}
