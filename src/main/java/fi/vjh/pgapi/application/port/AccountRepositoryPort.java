package fi.vjh.pgapi.application.port;

import fi.vjh.pgapi.domain.Account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepositoryPort {
    Optional<Account> findById(UUID id);
    List<Account> findAll();
    void save(Account account);
}
