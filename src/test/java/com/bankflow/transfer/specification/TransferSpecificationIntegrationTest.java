package com.bankflow.transfer.specification;

import com.bankflow.account.entity.AccountEntity;
import com.bankflow.account.enums.AccountStatus;
import com.bankflow.account.repository.AccountRepository;
import com.bankflow.transfer.entity.TransferEntity;
import com.bankflow.transfer.enums.Currency;
import com.bankflow.transfer.enums.TransferStatus;
import com.bankflow.transfer.repository.TransferRepository;
import com.bankflow.user.entity.UserEntity;
import com.bankflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class TransferSpecificationIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Test
    void involvesUser_shouldReturnOnlyTransfersWhereUserIsSenderOrRecipient() {
        UserEntity userA = new UserEntity("User A", "usera@example.com");
        UserEntity userB = new UserEntity("User B", "userb@example.com");
        UserEntity userC = new UserEntity("User C", "userc@example.com");
        UserEntity userD = new UserEntity("User D", "userd@example.com");

        AccountEntity accountA = new AccountEntity(userA,"accounta", new BigDecimal("2000000"), AccountStatus.ACTIVE);
        AccountEntity accountB = new AccountEntity(userB,"accountb", new BigDecimal("2000000"), AccountStatus.ACTIVE);
        AccountEntity accountC = new AccountEntity(userC,"accountc", new BigDecimal("2000000"), AccountStatus.ACTIVE);
        AccountEntity accountD = new AccountEntity(userD,"accountd", new BigDecimal("2000000"), AccountStatus.ACTIVE);

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

        List<TransferEntity> results =
                transferRepository.findAll(
                        TransferSpecification.involvesUser(
                                userA.getId()
                        )
                );

        assertEquals(2, results.size());

        assertTrue(results.stream().anyMatch(result -> result.getId().equals(transferAB.getId())));
        assertTrue(results.stream().anyMatch(result -> result.getId().equals(transferBA.getId())));
        assertFalse(results.stream().anyMatch(result -> result.getId().equals(transferCD.getId())));

    }
}
