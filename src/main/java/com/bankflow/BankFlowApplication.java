package com.bankflow;

import com.bankflow.transfer.TransferService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class BankFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankFlowApplication.class, args);
    }

    @Bean
    CommandLineRunner run(TransferService transferService) {
        return args -> transferService.transfer();
    }

}
