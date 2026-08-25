package fi.vjh.pgapi.application.usecase;

import fi.vjh.pgapi.application.port.AccountRepositoryPort;
import fi.vjh.pgapi.domain.Account;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class TransferMoney {
    private final AccountRepositoryPort accountRepositoryPort;

    public TransferMoney(AccountRepositoryPort accountRepositoryPort) {
        this.accountRepositoryPort = accountRepositoryPort;
    }


    @Transactional
    public void execute(UUID sourceAccountId, UUID targetAccountId, long amountCents) {
        Objects.requireNonNull(sourceAccountId, "Source account ID must not be null");
        Objects.requireNonNull(targetAccountId, "Target account ID must not be null");

        if (amountCents <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }
        if (sourceAccountId.equals(targetAccountId)) {
            throw new IllegalArgumentException("Source and target accounts must differ");
        }

        Account sourceAccount = accountRepositoryPort.findById(sourceAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Source account not found"));
        Account targetAccount = accountRepositoryPort.findById(targetAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Target account not found"));

        if (sourceAccount.getBalanceInCents() < amountCents) {
            throw new IllegalArgumentException("Insufficient funds");
        }

        sourceAccount.withdraw(amountCents);
        targetAccount.deposit(amountCents);

        accountRepositoryPort.save(sourceAccount);
        accountRepositoryPort.save(targetAccount);
    }
}
