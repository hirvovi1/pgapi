package fi.vjh.pgapi.application.port;

import fi.vjh.pgapi.domain.Account;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepositoryPort {
    Optional<Account> findById(UUID id);
    void save(Account account);
}

