package com.sunhomelab.castellanshadow.etl;

import com.sunhomelab.castellanshadow.config.DataSourceProperties;
import com.sunhomelab.castellanshadow.domain.OptionType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scrapes the daily holdings table embedded at https://castellanetf.com/{ticker}.
 *
 * Active ETFs are required by Rule 6c-11 to publish daily holdings on the fund's website.
 * Castellan publishes them as an HTML table with the columns:
 *   Ticker | Name | CUSIP | Shares | Price | Market Value ($mm) | % of Net Assets | EFFECTIVE_DATE
 *
 * Market values are reported in millions; we expand to whole dollars for storage.
 */
@Component
public class CastellanWebsiteHoldingsFetcher implements HoldingsFetcher {

    private static final Logger log = LoggerFactory.getLogger(CastellanWebsiteHoldingsFetcher.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final BigDecimal MILLION = new BigDecimal("1000000");

    private final WebClient webClient;
    private final DataSourceProperties props;

    public CastellanWebsiteHoldingsFetcher(WebClient webClient, DataSourceProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    @Override
    public Optional<Snapshot> fetch(String etfTicker) {
        String url = props.getHoldingsUrlTemplate().replace("{ticker}", etfTicker.toLowerCase(Locale.ROOT));
        log.info("Scraping holdings: ticker={} url={}", etfTicker, url);

        String html;
        try {
            html = webClient.get().uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(30));
        } catch (Exception e) {
            log.warn("Holdings page fetch failed for {}: {}", etfTicker, e.getMessage());
            return Optional.empty();
        }
        if (html == null || html.isBlank()) return Optional.empty();
        return parseHtml(html);
    }

    Optional<Snapshot> parseHtml(String html) {
        Document doc = Jsoup.parse(html);
        Element table = findHoldingsTable(doc);
        if (table == null) {
            log.warn("No holdings table found on page");
            return Optional.empty();
        }

        Map<String, Integer> cols = mapHeaderColumns(table);
        if (cols.isEmpty()) return Optional.empty();

        LocalDate asOf = null;
        List<HoldingRow> rows = new ArrayList<>();

        for (Element tr : table.select("tbody tr")) {
            Elements tds = tr.select("td");
            if (tds.isEmpty()) continue;

            HoldingRow row = mapRow(tds, cols);
            if (row != null) rows.add(row);

            LocalDate dateInRow = parseDate(text(tds, cols, "EFFECTIVE_DATE"));
            if (dateInRow != null && (asOf == null || dateInRow.isAfter(asOf))) {
                asOf = dateInRow;
            }
        }

        if (rows.isEmpty()) {
            log.warn("Holdings table parsed 0 rows");
            return Optional.empty();
        }
        if (asOf == null) asOf = LocalDate.now();
        return Optional.of(new Snapshot(asOf, rows));
    }

    private static Element findHoldingsTable(Document doc) {
        for (Element t : doc.select("table")) {
            String headerText = t.select("thead, tr:first-child").text().toUpperCase(Locale.ROOT);
            if (headerText.contains("TICKER") && headerText.contains("CUSIP") &&
                (headerText.contains("MARKET VALUE") || headerText.contains("NET ASSETS"))) {
                return t;
            }
        }
        return null;
    }

    private static Map<String, Integer> mapHeaderColumns(Element table) {
        Map<String, Integer> map = new HashMap<>();
        Elements ths = table.select("thead th");
        if (ths.isEmpty()) ths = table.select("tr:first-child th, tr:first-child td");

        for (int i = 0; i < ths.size(); i++) {
            map.put(normalizeHeader(ths.get(i).text()), i);
        }
        return map;
    }

    /** Normalize a column name so that "Market Value ($mm)" matches the lookup key "MARKET VALUE". */
    static String normalizeHeader(String raw) {
        if (raw == null) return "";
        return raw.toUpperCase(Locale.ROOT)
            .replaceAll("\\([^)]*\\)", "")  // strip parenthetical units like ($mm)
            .replaceAll("\\s+", " ")
            .trim();
    }

    private HoldingRow mapRow(Elements tds, Map<String, Integer> cols) {
        try {
            String ticker = text(tds, cols, "TICKER");
            if (ticker == null || ticker.isBlank()) return null;

            String name   = text(tds, cols, "NAME", "SECURITY NAME", "DESCRIPTION");
            String cusip  = text(tds, cols, "CUSIP");
            BigDecimal shares = decimal(text(tds, cols, "SHARES", "QUANTITY", "UNITS"));
            BigDecimal price  = decimal(text(tds, cols, "PRICE"));
            BigDecimal mvMm   = decimal(text(tds, cols, "MARKET VALUE", "MARKET VALUE ($MM)", "VALUE"));
            BigDecimal weight = decimal(text(tds, cols, "% OF NET ASSETS", "WEIGHT", "WEIGHTING"));

            BigDecimal mv = mvMm == null ? null : mvMm.multiply(MILLION).setScale(2, RoundingMode.HALF_UP);
            OptionMeta optMeta = parseFlexOcc(ticker, name);
            String secType = optMeta != null ? "OPTION" : classify(ticker, name);
            String underlying = optMeta != null ? optMeta.underlyingTicker()
                              : "EQUITY".equals(secType) ? ticker : null;

            return new HoldingRow(ticker, name, cusip, secType, underlying,
                shares, price, mv, weight, null, optMeta);
        } catch (Exception e) {
            log.debug("Skipping row: {}", e.getMessage());
            return null;
        }
    }

    private static String classify(String ticker, String name) {
        if (name != null) {
            String n = name.toUpperCase(Locale.ROOT);
            if (n.contains(" CALL") || n.contains(" PUT") || n.endsWith("CALL") || n.endsWith("PUT")) return "OPTION";
            if (n.contains("CASH") || n.contains("MONEY MARKET")) return "CASH";
        }
        return "EQUITY";
    }

    /**
     * ETF Architect publishes FLEX option positions with a leading multiplier digit
     * followed by an OCC-style symbol, e.g. {@code 2DECK 260918C00120010}:
     *   "2"          — multiplier digit (typically 1 or 2 for adjusted contracts)
     *   "DECK"       — underlying root
     *   "260918"     — yy/mm/dd expiration
     *   "C" or "P"   — option type
     *   "00120010"   — strike × 1000 ⇒ $120.010
     *
     * Returns null for non-option symbols.
     */
    static OptionMeta parseFlexOcc(String symbol, String description) {
        if (symbol == null) return null;
        Matcher m = OCC_PATTERN.matcher(symbol);
        if (!m.matches()) return null;
        try {
            String root = m.group("root");
            int yy = Integer.parseInt(m.group("yy"));
            int year = (yy < 70 ? 2000 : 1900) + yy;
            LocalDate expiry = LocalDate.of(year,
                Integer.parseInt(m.group("mm")),
                Integer.parseInt(m.group("dd")));
            BigDecimal strike = new BigDecimal(m.group("strike"))
                .divide(new BigDecimal(1000), 4, RoundingMode.HALF_UP);
            OptionType type = "C".equals(m.group("type")) ? OptionType.CALL : OptionType.PUT;
            return new OptionMeta(root, expiry, strike, type, true, description);
        } catch (Exception e) {
            return null;
        }
    }

    // Allow optional leading digit + space, then root, then 6-digit date, C/P, 8-digit strike.
    private static final Pattern OCC_PATTERN = Pattern.compile(
        "^\\d?\\s*(?<root>[A-Z]{1,6})\\s+" +
        "(?<yy>\\d{2})(?<mm>\\d{2})(?<dd>\\d{2})" +
        "(?<type>[CP])(?<strike>\\d{8})$"
    );

    private static String text(Elements tds, Map<String, Integer> cols, String... names) {
        for (String n : names) {
            Integer idx = cols.get(normalizeHeader(n));
            if (idx != null && idx < tds.size()) {
                String t = tds.get(idx).text().trim();
                if (!t.isEmpty()) return t;
            }
        }
        return null;
    }

    private static BigDecimal decimal(String raw) {
        if (raw == null) return null;
        String v = raw.replace(",", "").replace("$", "").replace("%", "").trim();
        if (v.isEmpty() || "-".equals(v) || "N/A".equalsIgnoreCase(v)) return null;
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s, DATE); }
        catch (Exception e) { return null; }
    }
}
