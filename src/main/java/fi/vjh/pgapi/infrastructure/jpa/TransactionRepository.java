package fi.vjh.pgapi.infrastructure.jpa;

import fi.vjh.pgapi.domain.Account;
import fi.vjh.pgapi.entity.AccountRow;
import fi.vjh.pgapi.entity.TransactionRow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransactionRepository extends JpaRepository<TransactionRow, UUID> {
}
