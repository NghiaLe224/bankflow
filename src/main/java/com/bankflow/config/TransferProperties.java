package com.bankflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "bankflow.transfer")
public class TransferProperties {
    private String currency;
    private BigDecimal maxAmount;
    private BigDecimal dailyLimit;

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public void setMaxAmount(BigDecimal maxAmount) {
        this.maxAmount = maxAmount;
    }

    public void setDailyLimit(BigDecimal dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    public BigDecimal getDailyLimit() {
        return dailyLimit;
    }
}
