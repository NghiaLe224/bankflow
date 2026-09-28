package com.bankflow.account;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class AccountService {
    private final AccountRepository accountRepository;

    public AccountService(AccountRepository repository) {
        this.accountRepository = repository;
    }

    public AccountResponse createAccount(CreateAccountRequest account) {
        return new AccountResponse(1L, account.getOwner(), account.getInitialBalance(), "ACTIVE");
    }

    public AccountResponse getAccount(Long id) {
        return new AccountResponse(id, "Nghia", new BigDecimal("1000000000"), "ACTIVE");
    }

    public String searchAccount(String owner) {
        return "Searching account of: " + owner;
    }
}
