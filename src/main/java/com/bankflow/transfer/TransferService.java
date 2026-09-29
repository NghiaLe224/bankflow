package com.bankflow.transfer;

import com.bankflow.config.TransferProperties;
import org.springframework.stereotype.Service;

@Service
public class TransferService {
    private final TransferRepository transferRepository;
    private final TransferProperties transferProperties;

    public TransferService(TransferRepository transferRepository,
                           TransferProperties transferProperties
    ) {
        this.transferRepository = transferRepository;
        this.transferProperties = transferProperties;
    }

    public void transfer() {
        transferRepository.save();
        System.out.println("Transfer completed");
        System.out.println("Currency: " + transferProperties.getCurrency());
        System.out.println("Max amount: " + transferProperties.getMaxAmount());
        System.out.println("Daily limit: " + transferProperties.getDailyLimit());
    }


}
