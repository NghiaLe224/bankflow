package com.bankflow.account;

import org.springframework.stereotype.Service;

@Service
public class AccountService {
    private final AccountRepository accountRepository;

    public AccountService(AccountRepository repository) {
        this.accountRepository = repository;
    }

    public String createAccount(CreateAccountRequest account) {
        return "Created account for " + account.getOwner() + " with balance " + account.getInitialBalance();
    }

    public String getAccount(Long id) {
        return "Account: " + id;
    }

    public String searchAccount(String owner) {
        return "Searching account of: " + owner;
    }
}
