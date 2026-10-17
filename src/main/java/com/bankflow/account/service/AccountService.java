package com.bankflow.account.service;

import com.bankflow.account.dto.AccountResponse;
import com.bankflow.account.dto.CreateAccountRequest;
import com.bankflow.account.entity.AccountEntity;
import com.bankflow.account.enums.AccountStatus;
import com.bankflow.account.repository.AccountRepository;
import com.bankflow.account.security.AccountAuthorizationPolicy;
import com.bankflow.common.exception.AccountNotFoundException;
import com.bankflow.common.exception.UserNotFoundException;
import com.bankflow.security.CurrentUserService;
import com.bankflow.user.entity.UserEntity;
import com.bankflow.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AccountService {
    private final CurrentUserService currentUserService;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final AccountAuthorizationPolicy accountAuthorizationPolicy;

    public AccountService(
            AccountRepository accountRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService,
            AccountAuthorizationPolicy accountAuthorizationPolicy
    ) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.accountAuthorizationPolicy = accountAuthorizationPolicy;
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        Long currentUserId = currentUserService.getCurrentUserId();

        UserEntity user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new UserNotFoundException(currentUserId));

        AccountEntity account = new AccountEntity(
                user,
                request.getAccountNumber(),
                BigDecimal.ZERO,
                AccountStatus.ACTIVE
        );

        AccountEntity savedAccount = accountRepository.save(account);

        return new AccountResponse(
                savedAccount.getId(),
                savedAccount.getUser().getFullName(),
                savedAccount.getBalance(),
                savedAccount.getStatus().name()

        );
    }

    @Transactional
    public void blockAccount(Long id) {
        AccountEntity account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));

        accountAuthorizationPolicy.assertCanAccess(account);

        account.block();
    }

    @Transactional
    public AccountResponse getAccount(Long id) {
        AccountEntity account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));

        accountAuthorizationPolicy.assertCanAccess(account);

        return new AccountResponse(
                account.getId(),
                account.getUser().getFullName(),
                account.getBalance(),
                account.getStatus().name());
    }

    @Transactional
    public AccountResponse getByAccountNumber(String accountNumber) {
        AccountEntity account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));

        accountAuthorizationPolicy.assertCanAccess(account);

        return new AccountResponse(
                account.getId(),
                account.getUser().getFullName(),
                account.getBalance(),
                account.getStatus().name()
        );
    }

    @Transactional
    public List<AccountResponse> getCurrentUserAccounts() {
        Long currentUserId = currentUserService.getCurrentUserId();

        List<AccountEntity> accounts = accountRepository.findByUserId(currentUserId);

        return accounts.stream()
                .map(account ->
                        new AccountResponse(
                                account.getId(),
                                account.getUser().getFullName(),
                                account.getBalance(),
                                account.getStatus().name()
                        )
                ).toList();
    }

}
