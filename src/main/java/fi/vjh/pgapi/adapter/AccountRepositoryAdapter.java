package fi.vjh.pgapi.adapter;

import fi.vjh.pgapi.application.port.AccountRepositoryPort;
import fi.vjh.pgapi.domain.Account;
import fi.vjh.pgapi.entity.AccountRow;
import fi.vjh.pgapi.infrastructure.jpa.AccountRepository;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class AccountRepositoryAdapter implements AccountRepositoryPort {

    private final AccountRepository accountRepository;

    public AccountRepositoryAdapter(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return accountRepository.findById(id)
                .map(row -> new Account(row.getId(), row.getOwnerName(), row.getBalanceInCents()));
    }

    @Override
    public List<Account> findAll() {
        return accountRepository.findAll().stream()
                .map(row -> new Account(row.getId(), row.getOwnerName(), row.getBalanceInCents()))
                .toList();
    }

    @Override
    public void save(Account account) {
        AccountRow row = new AccountRow(account.getId(), account.getOwnerName(), account.getBalanceInCents());
        accountRepository.save(row);
    }
}
