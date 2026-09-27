package com.bankflow.account;

import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {
    public void checkAccount() {
        System.out.println("checking account...");
    }

    public void save() {
        System.out.println("account saving...");
    }
}
