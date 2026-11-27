package com.bankflow.learning.retry;

import java.util.function.Supplier;

public class RetryExercise {
    public static String retry(int maxAttempts, Supplier<String> operation) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("Max attempts must be greater than 0");
        }

        if (operation == null) {
            throw new IllegalArgumentException("operation cannot be null");
        }

        for (int i = 1; i <= maxAttempts; ++i) {
            try {
                return operation.get();
            } catch (TransientProviderException e) {
                if (i == maxAttempts) {
                    throw e;
                }
            }
        }
        throw new IllegalStateException("Unexpected retry state");
    }
}
