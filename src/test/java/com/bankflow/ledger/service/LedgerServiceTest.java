package com.bankflow.ledger.service;

import com.bankflow.account.entity.AccountEntity;
import com.bankflow.ledger.entity.LedgerEntryEntity;
import com.bankflow.ledger.enums.LedgerEntryType;
import com.bankflow.ledger.repository.LedgerEntryRepository;
import com.bankflow.transfer.entity.TransferEntity;
import com.bankflow.transfer.enums.Currency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @InjectMocks
    private LedgerService ledgerService;

    @Test
    void createBalancedEntries_shouldCreateDebitAndCreditEntries_whenTransferIsValid() {
        // Arrange
        TransferEntity transfer = mock(TransferEntity.class);
        AccountEntity fromAccount = mock(AccountEntity.class);
        AccountEntity toAccount = mock(AccountEntity.class);

        BigDecimal amount = new BigDecimal("500000");
        Currency currency = Currency.VND;

        when(transfer.getFromAccount()).thenReturn(fromAccount);
        when(transfer.getToAccount()).thenReturn(toAccount);
        when(transfer.getAmount()).thenReturn(amount);
        when(transfer.getCurrency()).thenReturn(currency);

        when(ledgerEntryRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        List<LedgerEntryEntity> result =
                ledgerService.createBalancedEntries(transfer);

        // Assert
        ArgumentCaptor<List<LedgerEntryEntity>> captor =
                ArgumentCaptor.forClass(List.class);

        verify(ledgerEntryRepository, times(1)).saveAll(captor.capture());

        List<LedgerEntryEntity> savedEntries = captor.getValue();

        assertEquals(2, savedEntries.size());

        LedgerEntryEntity debitEntry = savedEntries.get(0);
        LedgerEntryEntity creditEntry = savedEntries.get(1);

        assertSame(transfer, debitEntry.getTransfer());
        assertSame(fromAccount, debitEntry.getAccount());
        assertEquals(LedgerEntryType.DEBIT, debitEntry.getEntryType());
        assertEquals(amount, debitEntry.getAmount());
        assertEquals(currency, debitEntry.getCurrency());

        assertSame(transfer, creditEntry.getTransfer());
        assertSame(toAccount, creditEntry.getAccount());
        assertEquals(LedgerEntryType.CREDIT, creditEntry.getEntryType());
        assertEquals(amount, creditEntry.getAmount());
        assertEquals(currency, creditEntry.getCurrency());

        BigDecimal totalDebit = savedEntries.stream()
                .filter(entry -> entry.getEntryType() == LedgerEntryType.DEBIT)
                .map(LedgerEntryEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredit = savedEntries.stream()
                .filter(entry -> entry.getEntryType() == LedgerEntryType.CREDIT)
                .map(LedgerEntryEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertEquals(0, totalDebit.compareTo(totalCredit));

        assertSame(savedEntries, result);

        verifyNoMoreInteractions(ledgerEntryRepository);
    }

}
