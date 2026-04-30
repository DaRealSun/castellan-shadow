package com.sunhomelab.castellanshadow.web;

import com.sunhomelab.castellanshadow.domain.PriceBar;
import com.sunhomelab.castellanshadow.repo.PriceBarRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/prices")
public class PricesController {

    private final PriceBarRepository priceRepo;

    public PricesController(PriceBarRepository priceRepo) {
        this.priceRepo = priceRepo;
    }

    @GetMapping("/{ticker}")
    public List<BarDto> get(
        @PathVariable String ticker,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        return priceRepo
            .findByTickerAndBarDateBetweenOrderByBarDate(ticker.toUpperCase(), from, to)
            .stream().map(BarDto::from).toList();
    }

    public record BarDto(
        LocalDate date,
        BigDecimal open, BigDecimal high, BigDecimal low,
        BigDecimal close, BigDecimal adjClose, Long volume
    ) {
        static BarDto from(PriceBar b) {
            return new BarDto(b.getBarDate(), b.getOpen(), b.getHigh(), b.getLow(),
                b.getClose(), b.getAdjClose(), b.getVolume());
        }
    }
}
