package com.bankflow.transfer;

import com.bankflow.account.AccountRepository;
import org.springframework.stereotype.Service;

@Service
public class TransferService {
    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;

    public TransferService(TransferRepository transferRepository, AccountRepository accountRepository) {
        this.transferRepository = transferRepository;
        this.accountRepository = accountRepository;
    }

    public void transfer(){
        accountRepository.checkAccount();
        transferRepository.save();
        System.out.println("Transfer completed");
    }


}
