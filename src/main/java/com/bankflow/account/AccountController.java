package com.bankflow.account;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/{id}")
    public String getAccount(@PathVariable Long id) {
        return accountService.getAccount(id);
    }

    @GetMapping("/search")
    public String searchAccount(@RequestParam String owner) {
        return accountService.searchAccount(owner);
    }

    @PostMapping
    public String createAccount(@RequestBody CreateAccountRequest account) {
        return accountService.createAccount(account);
    }

}
