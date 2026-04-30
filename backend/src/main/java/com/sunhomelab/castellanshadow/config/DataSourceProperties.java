package com.sunhomelab.castellanshadow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "castellan.data-sources")
public class DataSourceProperties {

    private String holdingsUrlTemplate = "https://castellanetf.com/{ticker}";
    private String yahooFinanceBaseUrl = "https://query1.finance.yahoo.com";

    public String getHoldingsUrlTemplate() { return holdingsUrlTemplate; }
    public void setHoldingsUrlTemplate(String v) { this.holdingsUrlTemplate = v; }

    public String getYahooFinanceBaseUrl() { return yahooFinanceBaseUrl; }
    public void setYahooFinanceBaseUrl(String v) { this.yahooFinanceBaseUrl = v; }
}
