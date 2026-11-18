package com.bankflow.transfer.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IdempotencyRaceHookConfig {

    @Bean
    public IdempotencyRaceHook idempotencyRaceHook() {
        return () -> {};
    }
}
