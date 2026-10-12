package com.bankflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BankFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankFlowApplication.class, args);
    }

}
