package com.sunhomelab.castellanshadow.etl;

import com.sunhomelab.castellanshadow.domain.Holding;
import com.sunhomelab.castellanshadow.domain.SecurityType;
import com.sunhomelab.castellanshadow.repo.HoldingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Backfills real tickers (PG, AMG, ...) onto historical holdings whose
 * instrumentSymbol is a placeholder derived from the company name.
 *
 * Learning step: scan all holdings whose instrumentSymbol looks like a real ticker
 * AND have securityName populated. Build a name→ticker map.
 *
 * Resolve step: for every other equity/cash holding, normalize its name and look
 * up the canonical ticker. If found and different, update the row.
 */
@Service
public class NameTickerResolver {

    private static final Logger log = LoggerFactory.getLogger(NameTickerResolver.class);

    private final HoldingRepository holdingRepo;

    public NameTickerResolver(HoldingRepository holdingRepo) {
        this.holdingRepo = holdingRepo;
    }

    @Transactional
    public BackfillResult backfillAll() {
        Map<String, String> nameToTicker = learnMap();
        log.info("Learned {} name→ticker mappings from live data", nameToTicker.size());

        int updated = 0;
        int unmapped = 0;
        for (Holding h : holdingRepo.findAll()) {
            if (h.getSecurityType() != SecurityType.EQUITY
                && h.getSecurityType() != SecurityType.CASH) continue;
            if (h.getSecurityName() == null) continue;
            if (looksLikeRealTicker(h.getInstrumentSymbol())) continue;

            String resolved = nameToTicker.get(canonicalize(h.getSecurityName()));
            if (resolved == null) { unmapped++; continue; }
            if (resolved.equals(h.getInstrumentSymbol())) continue;

            // The unique constraint is on (etf_id, as_of_date, instrument_symbol).
            // If a row with the resolved ticker already exists for the same fund/date,
            // delete the placeholder row in favor of the canonical one.
            var existing = holdingRepo.findByEtfIdAndAsOfDateAndInstrumentSymbol(
                h.getEtfId(), h.getAsOfDate(), resolved);
            if (existing.isPresent() && !existing.get().getId().equals(h.getId())) {
                holdingRepo.delete(h);
                continue;
            }

            h.setInstrumentSymbol(resolved);
            h.setUnderlyingTicker(resolved);
            holdingRepo.save(h);
            updated++;
        }
        log.info("Ticker backfill complete: updated={} unmapped={}", updated, unmapped);
        return new BackfillResult(nameToTicker.size(), updated, unmapped);
    }

    private Map<String, String> learnMap() {
        Map<String, String> map = new HashMap<>();
        for (Holding h : holdingRepo.findAll()) {
            if (h.getSecurityName() == null) continue;
            if (!looksLikeRealTicker(h.getInstrumentSymbol())) continue;
            map.putIfAbsent(canonicalize(h.getSecurityName()), h.getInstrumentSymbol());
        }
        return map;
    }

    /**
     * Heuristic: a real ticker is 1-5 uppercase letters, possibly with one dot for
     * share classes (e.g. BRK.B). Placeholder tickers are 7+ chars derived from
     * the company name (PROCTER, AMERIPRISE, etc.).
     */
    static boolean looksLikeRealTicker(String s) {
        return s != null && s.matches("[A-Z]{1,5}(\\.[A-Z])?");
    }

    /** Lowercase, drop legal suffixes and punctuation, collapse whitespace. */
    static String canonicalize(String name) {
        if (name == null) return "";
        String c = name.toLowerCase(Locale.ROOT)
            .replaceAll("[“”\"]", "")
            .replaceAll(",", "")
            .replaceAll("\\.", "")
            .replaceAll("\\s+(inc|corp|company|co|ltd|plc|llc|llp|ag|se|nv|sa)\\b", "")
            .replaceAll("[^a-z0-9 ]", " ")
            .replaceAll("\\s+", " ")
            .trim();
        return c;
    }

    public Optional<String> resolve(String name) {
        return Optional.ofNullable(learnMap().get(canonicalize(name)));
    }

    public record BackfillResult(int learned, int updated, int unmapped) {}
}
