package com.bankflow.config;

import com.bankflow.payment.resilience.ExponentialJitterDelayStrategy;
import com.bankflow.payment.resilience.RetryExecutor;
import com.bankflow.payment.resilience.ThreadSleeper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentRetryConfig {

    @Bean
    public RetryExecutor retryExecutor() {
        return new RetryExecutor(
                3,
                new ExponentialJitterDelayStrategy(200, 2000),
                new ThreadSleeper()
        );
    }
}
