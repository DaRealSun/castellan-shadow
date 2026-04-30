package com.sunhomelab.castellanshadow.etl;

import com.sunhomelab.castellanshadow.config.DataSourceProperties;
import com.sunhomelab.castellanshadow.domain.OptionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CastellanWebsiteHoldingsFetcherTest {

    private final CastellanWebsiteHoldingsFetcher fetcher =
        new CastellanWebsiteHoldingsFetcher(null, new DataSourceProperties());

    @Test
    void parsesEquityRowsAndDate() {
        String html = """
            <html><body>
              <h2>Fund Holdings</h2>
              <table>
                <thead>
                  <tr>
                    <th>Ticker</th><th>Name</th><th>CUSIP</th>
                    <th>Shares</th><th>Price</th>
                    <th>Market Value ($mm)</th><th>% of Net Assets</th>
                    <th>EFFECTIVE_DATE</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td>AMG</td><td>Affiliated Managers Group Inc</td><td>008252108</td>
                    <td>74,618</td><td>289.10</td>
                    <td>21.57</td><td>3.93</td>
                    <td>04/30/2026</td>
                  </tr>
                  <tr>
                    <td>BFH</td><td>Bread Financial Holdings Inc</td><td>018581108</td>
                    <td>284,969</td><td>85.42</td>
                    <td>24.34</td><td>4.43</td>
                    <td>04/30/2026</td>
                  </tr>
                </tbody>
              </table>
            </body></html>
            """;

        Optional<HoldingsFetcher.Snapshot> result = fetcher.parseHtml(html);

        assertThat(result).isPresent();
        HoldingsFetcher.Snapshot snap = result.get();
        assertThat(snap.asOfDate()).isEqualTo(LocalDate.of(2026, 4, 30));
        assertThat(snap.rows()).hasSize(2);

        HoldingsFetcher.HoldingRow amg = snap.rows().get(0);
        assertThat(amg.instrumentSymbol()).isEqualTo("AMG");
        assertThat(amg.securityName()).isEqualTo("Affiliated Managers Group Inc");
        assertThat(amg.cusip()).isEqualTo("008252108");
        assertThat(amg.securityType()).isEqualTo("EQUITY");
        assertThat(amg.shares()).isEqualByComparingTo(new BigDecimal("74618"));
        assertThat(amg.price()).isEqualByComparingTo(new BigDecimal("289.10"));
        assertThat(amg.marketValue()).isEqualByComparingTo(new BigDecimal("21570000.00"));
        assertThat(amg.weightPct()).isEqualByComparingTo(new BigDecimal("3.93"));
    }

    @Test
    void classifiesOptionsAndExtractsFlexMetadata() {
        String html = """
            <html><body><table>
              <thead><tr>
                <th>Ticker</th><th>Name</th><th>CUSIP</th>
                <th>Shares</th><th>Price</th>
                <th>Market Value ($mm)</th><th>% of Net Assets</th>
                <th>EFFECTIVE_DATE</th>
              </tr></thead>
              <tbody>
                <tr>
                  <td>2DECK 260918C00120010</td><td>Deckers 18Sep26 120.01 Call</td><td></td>
                  <td>400</td><td>15.54</td>
                  <td>0.62</td><td>0.11</td>
                  <td>04/30/2026</td>
                </tr>
                <tr>
                  <td>USD</td><td>Cash & Cash Equivalents</td><td></td>
                  <td>5000000</td><td>1.00</td>
                  <td>5.00</td><td>0.91</td>
                  <td>04/30/2026</td>
                </tr>
              </tbody>
            </table></body></html>
            """;

        Optional<HoldingsFetcher.Snapshot> result = fetcher.parseHtml(html);

        assertThat(result).isPresent();
        assertThat(result.get().rows()).hasSize(2);

        HoldingsFetcher.HoldingRow opt = result.get().rows().get(0);
        assertThat(opt.securityType()).isEqualTo("OPTION");
        assertThat(opt.option()).isNotNull();
        assertThat(opt.option().underlyingTicker()).isEqualTo("DECK");
        assertThat(opt.option().expiration()).isEqualTo(LocalDate.of(2026, 9, 18));
        assertThat(opt.option().strike()).isEqualByComparingTo(new BigDecimal("120.01"));
        assertThat(opt.option().optionType()).isEqualTo(OptionType.CALL);
        assertThat(opt.option().flex()).isTrue();
        assertThat(opt.underlyingTicker()).isEqualTo("DECK");

        assertThat(result.get().rows().get(1).securityType()).isEqualTo("CASH");
        assertThat(result.get().rows().get(1).option()).isNull();
    }

    @Test
    void parsesPutOptionWithoutMultiplierDigit() {
        HoldingsFetcher.OptionMeta meta = CastellanWebsiteHoldingsFetcher
            .parseFlexOcc("MU 260117P00080000", null);
        assertThat(meta).isNotNull();
        assertThat(meta.underlyingTicker()).isEqualTo("MU");
        assertThat(meta.expiration()).isEqualTo(LocalDate.of(2026, 1, 17));
        assertThat(meta.strike()).isEqualByComparingTo(new BigDecimal("80"));
        assertThat(meta.optionType()).isEqualTo(OptionType.PUT);
    }

    @Test
    void returnsEmptyWhenNoTableMatches() {
        String html = "<html><body><p>No holdings here</p></body></html>";
        assertThat(fetcher.parseHtml(html)).isEmpty();
    }
}
