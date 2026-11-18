package com.bankflow.transfer.service;

public interface IdempotencyRaceHook {
    void afterRecordNotFound();
}
