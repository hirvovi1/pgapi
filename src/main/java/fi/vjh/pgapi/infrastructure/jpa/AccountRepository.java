package fi.vjh.pgapi.infrastructure.jpa;

import fi.vjh.pgapi.domain.Account;
import fi.vjh.pgapi.entity.AccountRow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountRepository extends JpaRepository<AccountRow, UUID> {
    void save(Account account);
}
