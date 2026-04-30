package com.sunhomelab.castellanshadow.web;

import com.sunhomelab.castellanshadow.domain.Etf;
import com.sunhomelab.castellanshadow.repo.EtfRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/etfs")
public class EtfController {

    private final EtfRepository etfRepo;

    public EtfController(EtfRepository etfRepo) {
        this.etfRepo = etfRepo;
    }

    @GetMapping
    public List<EtfDto> list() {
        return etfRepo.findAll().stream().map(EtfDto::from).toList();
    }

    @GetMapping("/{ticker}")
    public ResponseEntity<EtfDto> get(@PathVariable String ticker) {
        return etfRepo.findByTicker(ticker.toUpperCase())
            .map(EtfDto::from)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    public record EtfDto(
        Long id,
        String ticker,
        String name,
        BigDecimal expenseRatio,
        LocalDate inceptionDate,
        boolean castellanFund
    ) {
        static EtfDto from(Etf e) {
            return new EtfDto(e.getId(), e.getTicker(), e.getName(),
                e.getExpenseRatio(), e.getInceptionDate(), e.isCastellanFund());
        }
    }
}
