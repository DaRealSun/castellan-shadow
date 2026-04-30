package com.sunhomelab.castellanshadow.etl;

import com.fasterxml.jackson.databind.JsonNode;
import com.sunhomelab.castellanshadow.config.DataSourceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Pulls daily OHLCV via the unofficial Yahoo Finance v8 chart endpoint:
 *   /v8/finance/chart/{ticker}?period1={epochSeconds}&period2={epochSeconds}&interval=1d
 *
 * Public, undocumented, rate-limited. Acceptable for a low-volume educational tool.
 * For production we would swap this for a paid feed (Polygon, EODHD, etc.).
 */
@Component
public class YahooFinancePriceFetcher implements PriceFetcher {

    private static final Logger log = LoggerFactory.getLogger(YahooFinancePriceFetcher.class);

    private final WebClient webClient;
    private final DataSourceProperties props;

    public YahooFinancePriceFetcher(WebClient webClient, DataSourceProperties props) {
        this.webClient = webClient;
        this.props = props;
    }

    @Override
    public List<Bar> fetchDaily(String ticker, LocalDate start, LocalDate end) {
        long p1 = start.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        long p2 = end.plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        String url = props.getYahooFinanceBaseUrl()
            + "/v8/finance/chart/" + ticker
            + "?period1=" + p1 + "&period2=" + p2 + "&interval=1d&events=history";

        log.debug("Fetching prices: {} {}..{}", ticker, start, end);
        JsonNode root;
        try {
            root = webClient.get().uri(url)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(Duration.ofSeconds(20));
        } catch (Exception e) {
            log.warn("Yahoo fetch failed for {}: {}", ticker, e.getMessage());
            return List.of();
        }

        if (root == null) return List.of();
        JsonNode result = root.path("chart").path("result").path(0);
        if (result.isMissingNode() || result.isNull()) {
            log.warn("Yahoo returned no result for {}", ticker);
            return List.of();
        }

        JsonNode timestamps = result.path("timestamp");
        JsonNode indicators = result.path("indicators");
        JsonNode quote      = indicators.path("quote").path(0);
        JsonNode adj        = indicators.path("adjclose").path(0).path("adjclose");

        JsonNode opens   = quote.path("open");
        JsonNode highs   = quote.path("high");
        JsonNode lows    = quote.path("low");
        JsonNode closes  = quote.path("close");
        JsonNode volumes = quote.path("volume");

        List<Bar> bars = new ArrayList<>(timestamps.size());
        for (int i = 0; i < timestamps.size(); i++) {
            long ts = timestamps.path(i).asLong(0);
            if (ts == 0) continue;
            LocalDate date = LocalDate.ofEpochDay(ts / 86400L);

            BigDecimal close = decimal(closes, i);
            if (close == null) continue;  // skip rows missing the only required field

            bars.add(new Bar(
                ticker, date,
                decimal(opens,  i),
                decimal(highs,  i),
                decimal(lows,   i),
                close,
                decimal(adj,    i),
                volumes.path(i).isNumber() ? volumes.path(i).asLong() : null
            ));
        }
        return bars;
    }

    private static BigDecimal decimal(JsonNode arr, int i) {
        JsonNode n = arr.path(i);
        if (!n.isNumber()) return null;
        return BigDecimal.valueOf(n.asDouble()).setScale(4, RoundingMode.HALF_UP);
    }
}
