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

    public Account execute(String ownerName, long initialBalanceCents) {
        Account newAccount = new Account(ownerName, initialBalanceCents);
        accountRepositoryPort.save(newAccount);
        return newAccount;
    }
}
