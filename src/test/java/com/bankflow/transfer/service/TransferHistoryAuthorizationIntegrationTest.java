package com.bankflow.transfer.service;

import com.bankflow.account.entity.AccountEntity;
import com.bankflow.account.enums.AccountStatus;
import com.bankflow.account.repository.AccountRepository;
import com.bankflow.common.dto.PageResponse;
import com.bankflow.security.CurrentUserService;
import com.bankflow.transfer.dto.TransferResponse;
import com.bankflow.transfer.entity.TransferEntity;
import com.bankflow.transfer.enums.Currency;
import com.bankflow.transfer.enums.TransferStatus;
import com.bankflow.transfer.repository.TransferRepository;
import com.bankflow.user.entity.UserEntity;
import com.bankflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
public class TransferHistoryAuthorizationIntegrationTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferRepository transferRepository;

    @MockitoBean
    private CurrentUserService currentUserService;

    @Test
    void getTransferHistory_shouldReturnOnlyTransfersInvolvingCurrentUser() {
        UserEntity userA = new UserEntity("User A", "usera@example.com");
        UserEntity userB = new UserEntity("User B", "userb@example.com");
        UserEntity userC = new UserEntity("User C", "userc@example.com");
        UserEntity userD = new UserEntity("User D", "userd@example.com");

        AccountEntity accountA = new AccountEntity(userA, "accounta", new BigDecimal("2000000"), AccountStatus.ACTIVE);
        AccountEntity accountB = new AccountEntity(userB, "accountb", new BigDecimal("2000000"), AccountStatus.ACTIVE);
        AccountEntity accountC = new AccountEntity(userC, "accountc", new BigDecimal("2000000"), AccountStatus.ACTIVE);
        AccountEntity accountD = new AccountEntity(userD, "accountd", new BigDecimal("2000000"), AccountStatus.ACTIVE);

        TransferEntity transferAB = new TransferEntity(
                accountA,
                accountB,
                new BigDecimal("100000"),
                Currency.VND,
                TransferStatus.COMPLETED
        );

        TransferEntity transferCD = new TransferEntity(
                accountC,
                accountD,
                new BigDecimal("100000"),
                Currency.VND,
                TransferStatus.COMPLETED
        );

        TransferEntity transferBA = new TransferEntity(
                accountB,
                accountA,
                new BigDecimal("100000"),
                Currency.VND,
                TransferStatus.COMPLETED
        );

        userRepository.saveAll(List.of(userA, userB, userC, userD));
        accountRepository.saveAll(List.of(accountA, accountB, accountC, accountD));
        transferRepository.saveAll(List.of(transferAB, transferCD, transferBA));
        when(currentUserService.getCurrentUserId()).thenReturn(userA.getId());

        PageResponse<TransferResponse> result = transferService.getTransferHistory(0, 20, null, null, null, null);

        assertEquals(2L, result.content().size());
        assertTrue(result.content().stream().anyMatch(rs -> rs.id().equals(transferAB.getId())));
        assertTrue(result.content().stream().anyMatch(rs -> rs.id().equals(transferBA.getId())));
        assertFalse(result.content().stream().anyMatch(rs -> rs.id().equals(transferCD.getId())));
        assertEquals(2L, result.totalElements());
        assertEquals(20, result.size());
        assertEquals(0, result.page());
        assertEquals(1, result.totalPages());
        assertTrue(result.first());
        assertTrue(result.last());

        verify(currentUserService)
                .getCurrentUserId();

    }

    @Test
    void getTransferHistory_shouldNotLeakTransfers_whenFilteringByUnrelatedAccount() {
        UserEntity userA = new UserEntity("User A", "usera@example.com");
        UserEntity userB = new UserEntity("User B", "userb@example.com");
        UserEntity userC = new UserEntity("User C", "userc@example.com");
        UserEntity userD = new UserEntity("User D", "userd@example.com");

        AccountEntity accountA = new AccountEntity(userA, "accounta", new BigDecimal("2000000"), AccountStatus.ACTIVE);
        AccountEntity accountB = new AccountEntity(userB, "accountb", new BigDecimal("2000000"), AccountStatus.ACTIVE);
        AccountEntity accountC = new AccountEntity(userC, "accountc", new BigDecimal("2000000"), AccountStatus.ACTIVE);
        AccountEntity accountD = new AccountEntity(userD, "accountd", new BigDecimal("2000000"), AccountStatus.ACTIVE);

        TransferEntity transferAB = new TransferEntity(
                accountA,
                accountB,
                new BigDecimal("100000"),
                Currency.VND,
                TransferStatus.COMPLETED
        );

        TransferEntity transferCD = new TransferEntity(
                accountC,
                accountD,
                new BigDecimal("100000"),
                Currency.VND,
                TransferStatus.COMPLETED
        );

        TransferEntity transferBA = new TransferEntity(
                accountB,
                accountA,
                new BigDecimal("100000"),
                Currency.VND,
                TransferStatus.COMPLETED
        );

        userRepository.saveAll(List.of(userA, userB, userC, userD));
        accountRepository.saveAll(List.of(accountA, accountB, accountC, accountD));
        transferRepository.saveAll(List.of(transferAB, transferCD, transferBA));
        when(currentUserService.getCurrentUserId()).thenReturn(userA.getId());

        PageResponse<TransferResponse> result = transferService.getTransferHistory(0, 20, null, accountC.getId(), null, null);

        assertTrue(result.content().isEmpty());
        assertEquals(0L, result.totalElements());
        assertFalse(result.content().stream().anyMatch(rs -> rs.id().equals(transferAB.getId())));
        assertFalse(result.content().stream().anyMatch(rs -> rs.id().equals(transferBA.getId())));
        assertFalse(result.content().stream().anyMatch(rs -> rs.id().equals(transferCD.getId())));

        verify(currentUserService)
                .getCurrentUserId();
    }

}
