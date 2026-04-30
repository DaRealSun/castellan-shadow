package com.sunhomelab.castellanshadow.etl;

import com.sunhomelab.castellanshadow.domain.OptionType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses ETF Architect's "Part F" Schedule of Investments PDFs (CTEF/CTIF) into
 * a {@link HoldingsFetcher.Snapshot}.
 *
 * Identifies five sections: COMMON STOCKS, PURCHASED OPTIONS, WRITTEN OPTIONS,
 * EXCHANGE TRADED FUNDS, MONEY MARKET FUNDS. Equity rows are single-line; option
 * rows often wrap across two lines, so we run a DOTALL regex per option section.
 *
 * Ticker resolution is intentionally deferred: parsed rows carry the company name
 * as {@code instrumentSymbol} until a downstream step backfills the ticker.
 */
@Component
public class PartFParser {

    private static final Logger log = LoggerFactory.getLogger(PartFParser.class);
    private static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final DateTimeFormatter LONG_DATE =
        DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US);

    private static final Pattern AS_OF =
        Pattern.compile("(January|February|March|April|May|June|July|August|September|October|November|December) (\\d{1,2}), (\\d{4})");

    private static final Pattern OPTION_ROW = Pattern.compile(
        "(?<name>[A-Z][^\\n]*?(?:Inc|Corp|Co|Ltd|Group|Holdings|plc|N\\.A\\.|S\\.A\\.|AG|SE|LLC|LLP|Class\\s+[A-Z])\\.?)" +
        ",\\s+Expiration:\\s+(?<exp>\\d{2}/\\d{2}/\\d{4})" +
        "[;\\s]+Exercise Price:\\s*\\$?(?<strike>[\\d.,]+)" +
        "\\s+\\$?\\s*(?<notional>\\(?[\\d,]+\\)?)" +
        "\\s+(?<contracts>\\(?[\\d,]+\\)?)" +
        "\\s+\\$?\\s*(?<value>\\(?[\\d,]+\\)?)",
        Pattern.DOTALL);

    private static final Pattern EQUITY_ROW = Pattern.compile(
        "^(?<name>[A-Za-z].+?)" +
        "(?:\\s*\\(([a-z),(]+)\\))?" +
        "\\s+(?<shares>[\\d,]+)" +
        "\\s+\\$?\\s*(?<value>[\\d,]+)\\s*$");

    /** Convenience entrypoint for the admin endpoint. */
    public Optional<HoldingsFetcher.Snapshot> parseFile(Path pdfPath) throws IOException {
        return parseBytes(Files.readAllBytes(pdfPath));
    }

    public Optional<HoldingsFetcher.Snapshot> parseBytes(byte[] bytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(doc);
            return Optional.of(parseText(text));
        }
    }

    HoldingsFetcher.Snapshot parseText(String text) {
        LocalDate asOf = findAsOfDate(text)
            .orElseThrow(() -> new IllegalStateException("Could not find as-of date in Part F"));

        // The "WRITTEN OPTIONS" boundary tells us if a given option-row match is short.
        // PDFBox text-extraction order varies by PDF layout, so we scan globally rather
        // than depend on a fragile "COMMON STOCKS" section header position.
        int writtenStart = findWrittenOptionsBoundary(text);

        List<HoldingsFetcher.HoldingRow> rows = new ArrayList<>();
        java.util.BitSet consumed = new java.util.BitSet(text.length());

        Matcher om = OPTION_ROW.matcher(text);
        while (om.find()) {
            boolean written = om.start() >= writtenStart;
            HoldingsFetcher.HoldingRow row = buildOptionRow(om, written);
            if (row != null) rows.add(row);
            consumed.set(om.start(), om.end());
        }

        for (LinePos lp : splitLines(text)) {
            if (consumed.get(lp.start) || consumed.get(lp.end - 1)) continue;
            HoldingsFetcher.HoldingRow row = tryEquityRow(lp.line);
            if (row != null) rows.add(row);
        }

        log.info("Parsed Part F: asOf={} rows={}", asOf, rows.size());
        return new HoldingsFetcher.Snapshot(asOf, rows);
    }

    private static int findWrittenOptionsBoundary(String text) {
        int idx = text.indexOf("SCHEDULE OF WRITTEN OPTIONS");
        if (idx >= 0) return idx;
        Matcher m = Pattern.compile("^\\s*WRITTEN OPTIONS\\s*-", Pattern.MULTILINE).matcher(text);
        return m.find() ? m.start() : text.length();
    }

    private record LinePos(String line, int start, int end) {}

    private static List<LinePos> splitLines(String text) {
        List<LinePos> out = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                out.add(new LinePos(text.substring(start, i), start, i + 1));
                start = i + 1;
            }
        }
        if (start < text.length())
            out.add(new LinePos(text.substring(start), start, text.length()));
        return out;
    }

    private HoldingsFetcher.HoldingRow buildOptionRow(Matcher m, boolean written) {
        String name = cleanName(m.group("name"));
        LocalDate exp;
        try { exp = LocalDate.parse(m.group("exp"), MDY); }
        catch (Exception e) { return null; }

        BigDecimal strike    = decimal(m.group("strike"));
        BigDecimal notional  = signed(m.group("notional"), written);
        BigDecimal contracts = signed(m.group("contracts"), written);
        BigDecimal value     = signed(m.group("value"), written);
        if (strike == null || contracts == null) return null;

        String underlying = placeholderTicker(name);
        HoldingsFetcher.OptionMeta meta = new HoldingsFetcher.OptionMeta(
            underlying, exp, strike, OptionType.CALL, true,
            "%s %s $%s %s Call".formatted(name, exp, strike, written ? "Written" : "Long"));

        String synthSym = "%s|%s|%s|C|%s".formatted(
            underlying, exp, strike.toPlainString(), written ? "W" : "L");

        return new HoldingsFetcher.HoldingRow(
            synthSym, name, null, "OPTION", underlying,
            contracts, null, value, null, notional, meta);
    }

    private HoldingsFetcher.HoldingRow tryEquityRow(String raw) {
        String line = raw.trim();
        if (line.isEmpty()) return null;
        if (isHeaderLike(line)) return null;
        if (line.startsWith("Total ") || line.startsWith("TOTAL ")) return null;
        if (line.startsWith("Cost ") || line.startsWith("(Cost")) return null;
        if (line.startsWith("Percentages ") || line.startsWith("(")) return null;
        if (line.startsWith("CASTELLAN ") || line.startsWith("SCHEDULE")) return null;

        Matcher m = EQUITY_ROW.matcher(line);
        if (!m.matches()) return null;
        String name      = cleanName(m.group("name"));
        BigDecimal shr   = decimal(m.group("shares"));
        BigDecimal value = decimal(m.group("value"));
        if (name.isBlank() || shr == null || value == null) return null;
        if (!name.matches(".*[A-Za-z].*")) return null;  // require letters in name

        String secType = classifyEquityName(name);
        return new HoldingsFetcher.HoldingRow(
            placeholderTicker(name), name, null, secType,
            placeholderTicker(name),
            shr, null, value, null, null, null);
    }

    private static String classifyEquityName(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        if (upper.contains("MONEY MARKET")
            || upper.contains("GOVERNMENT OBLIGATIONS FUND")
            || upper.contains("TREASURY OBLIGATIONS")) return "CASH";
        return "EQUITY";
    }

    // ---------- date + sectioning ----------

    Optional<LocalDate> findAsOfDate(String text) {
        Matcher m = AS_OF.matcher(text);
        if (!m.find()) return Optional.empty();
        try {
            return Optional.of(LocalDate.parse(
                m.group(1) + " " + m.group(2) + ", " + m.group(3), LONG_DATE));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static boolean isHeaderLike(String line) {
        // Sector / industry headers look like "Industrials - 14.7%" or "Building Products - 5.4%"
        return line.matches(".+\\s+-\\s+[\\d.]+%.*")
            || line.matches("^[A-Z &-]+$");
    }

    // ---------- helpers ----------

    private static BigDecimal decimal(String raw) {
        if (raw == null) return null;
        String v = raw.replace(",", "").replace("$", "")
            .replace("(", "").replace(")", "").trim();
        if (v.isEmpty() || "-".equals(v)) return null;
        try { return new BigDecimal(v); } catch (NumberFormatException e) { return null; }
    }

    private static BigDecimal signed(String raw, boolean negate) {
        BigDecimal v = decimal(raw);
        if (v == null) return null;
        boolean wasParen = raw != null && raw.contains("(");
        return (negate || wasParen) ? v.negate() : v;
    }

    private static String cleanName(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("\\s+", " ")
            .replaceAll("\\s*\\([a-z, )(]+\\)\\s*$", "")  // strip trailing footnote tags
            .trim();
    }

    /**
     * Until we wire in name→ticker resolution, fall back to a stable placeholder
     * built from the company name. Downstream backfill can swap these for tickers.
     */
    private static String placeholderTicker(String name) {
        if (name == null || name.isBlank()) return "UNKNOWN";
        // Simplify: take first word, uppercased, alpha-only, capped at 10 chars
        String first = name.split("[ ,.]")[0].toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z]", "");
        return first.isEmpty() ? "UNKNOWN" : first.substring(0, Math.min(10, first.length()));
    }
}
