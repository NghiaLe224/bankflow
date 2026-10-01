package com.bankflow.account.service;

import com.bankflow.account.dto.AccountResponse;
import com.bankflow.account.dto.CreateAccountRequest;
import com.bankflow.account.entity.AccountEntity;
import com.bankflow.account.enums.AccountStatus;
import com.bankflow.account.repository.AccountRepository;
import com.bankflow.common.exception.AccountNotFoundException;
import com.bankflow.common.exception.UserNotFoundException;
import com.bankflow.user.entity.UserEntity;
import com.bankflow.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AccountService {
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public AccountService(AccountRepository accountRepository, UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        UserEntity user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new UserNotFoundException(request.getUserId()));

        AccountEntity account = new AccountEntity(
                user,
                request.getAccountNumber(),
                request.getInitialBalance(),
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

        account.block();
    }

    @Transactional
    public AccountResponse getAccount(Long id) {
        AccountEntity account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));

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

        return new AccountResponse(
                account.getId(),
                account.getUser().getFullName(),
                account.getBalance(),
                account.getStatus().name()
        );
    }

    @Transactional
    public List<AccountResponse> getAccountsByUserId(Long userId) {
        return accountRepository.findByUserId(userId)
                .stream()
                .map(account -> new AccountResponse(
                        account.getId(),
                        account.getUser().getFullName(),
                        account.getBalance(),
                        account.getStatus().name()
                ))
                .toList();
    }


}
