package com.bankflow.account.controller;

import com.bankflow.account.dto.AccountResponse;
import com.bankflow.account.dto.CreateAccountRequest;
import com.bankflow.account.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable Long id) {
        return ResponseEntity.ok(accountService.getAccount(id));
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAccount(request));
    }

    @PatchMapping("{id}/block")
    public ResponseEntity<Void> blockAccount(@PathVariable Long id) {
        accountService.blockAccount(id);
        return ResponseEntity.noContent().build();
    }
}
