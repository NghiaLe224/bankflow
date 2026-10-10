package com.bankflow.user.entity;

import com.bankflow.account.entity.AccountEntity;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "user")
    private List<AccountEntity> accounts = new ArrayList<>();

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "email", nullable = false, length = 255, unique = true)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    protected UserEntity() {
    }

    public UserEntity(String fullName, String email) {
        this.fullName = fullName;
        this.email = email;
    }

    public UserEntity(String fullName, String email, String passwordHash) {
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public List<AccountEntity> getAccounts() {
        return accounts;
    }

    public void addAccount(AccountEntity account) {
        this.accounts.add(account);
        account.assignUser(this);
    }

    public String getPasswordHash() {
        return passwordHash;
    }
}
