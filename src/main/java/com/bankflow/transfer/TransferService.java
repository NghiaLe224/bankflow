package com.bankflow.transfer;

import com.bankflow.account.AccountRepository;
import com.bankflow.config.TransferProperties;
import org.springframework.stereotype.Service;

@Service
public class TransferService {
    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final TransferProperties transferProperties;

    public TransferService(TransferRepository transferRepository,
                           AccountRepository accountRepository,
                           TransferProperties transferProperties
    ) {
        this.transferRepository = transferRepository;
        this.accountRepository = accountRepository;
        this.transferProperties = transferProperties;
    }

    public void transfer() {
        accountRepository.checkAccount();
        transferRepository.save();
        System.out.println("Transfer completed");
        System.out.println("Currency: " + transferProperties.getCurrency());
        System.out.println("Max amount: " + transferProperties.getMaxAmount());
        System.out.println("Daily limit: " + transferProperties.getDailyLimit());
    }


}
