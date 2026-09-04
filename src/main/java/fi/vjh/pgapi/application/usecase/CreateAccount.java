package fi.vjh.pgapi.application.usecase;

import fi.vjh.pgapi.application.port.AccountRepositoryPort;
import fi.vjh.pgapi.domain.Account;
import org.springframework.stereotype.Service;

@Service
public class CreateAccount {
    private final AccountRepositoryPort accountRepositoryPort;

    public CreateAccount(AccountRepositoryPort accountRepositoryPort) {
        this.accountRepositoryPort = accountRepositoryPort;
    }

    public Account execute(String ownerName, long balanceInCents) {
        Account newAccount = new Account(ownerName, balanceInCents);
        accountRepositoryPort.save(newAccount);
        return newAccount;
    }
}
