package fi.vjh.pgapi.application.usecase;

import fi.vjh.pgapi.application.port.AccountRepositoryPort;
import fi.vjh.pgapi.domain.Account;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class CreateAccount {
    private final AccountRepositoryPort accountRepositoryPort;

    public CreateAccount(AccountRepositoryPort accountRepositoryPort) {
        this.accountRepositoryPort = accountRepositoryPort;
    }

    public Account create(String ownerName, long balanceInCents) {
        Account newAccount = new Account(ownerName, balanceInCents);
        accountRepositoryPort.save(newAccount);
        return newAccount;
    }

    public List<Account> getAllAccounts() {
        return accountRepositoryPort.findAll();
    }

    public void delete(UUID id) {
        accountRepositoryPort.delete(id);
    }
}
