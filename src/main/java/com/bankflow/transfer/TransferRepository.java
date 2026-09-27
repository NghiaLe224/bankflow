package com.bankflow.transfer;

import org.springframework.stereotype.Repository;

@Repository
public class TransferRepository {
    public void save() {
        System.out.println("transfer saving");
    }
}
