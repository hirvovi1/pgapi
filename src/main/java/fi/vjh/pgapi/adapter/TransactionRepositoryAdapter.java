package fi.vjh.pgapi.adapter;

import fi.vjh.pgapi.application.port.TransactionRepositoryPort;
import fi.vjh.pgapi.domain.CallbackMessage;
import fi.vjh.pgapi.domain.TransactionStatus;
import fi.vjh.pgapi.entity.TransactionRow;
import fi.vjh.pgapi.infrastructure.jpa.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

@Component
public class TransactionRepositoryAdapter implements TransactionRepositoryPort {
    private static final Logger log = LoggerFactory.getLogger(TransactionRepositoryAdapter.class);

    private final TransactionRepository transactionRepository;
    private final DataSource dataSource;

    public TransactionRepositoryAdapter(TransactionRepository transactionRepository, DataSource dataSource) {
        this.transactionRepository = transactionRepository;
        this.dataSource = dataSource;
    }

    @Override
    public void createPendingTransaction(CallbackMessage message) {
        TransactionRow row = new TransactionRow(
                message.transactionId(),
                message.idempotencyKey(),
                message.accountIdFrom(),
                message.accountIdTo(),
                message.amountInCents(),
                TransactionStatus.PENDING,
                ""
        );
        transactionRepository.save(row);
    }

    @Override
    public void updateStatus(UUID id, TransactionStatus status, String message) {
        transactionRepository.findById(id).ifPresent(row -> {
            row.setStatus(status);
            row.setStatusMessage(message);
            transactionRepository.save(row);
        });
    }

    @Override
    public Optional<TransactionStatusInfo> findStatusById(UUID id) {
        return transactionRepository.findById(id)
                .map(row -> new TransactionStatusInfo(row.getStatus(), row.getStatusMessage()));
    }

    @Override
    public Optional<CallbackMessage> findById(UUID id) {
        return transactionRepository.findById(id).map(row -> new CallbackMessage(
                row.getIdempotencyKey(),
                row.getId(),
                row.getAccountIdFrom(),
                row.getAccountIdTo(),
                row.getAmountInCents(),
                row.getStatus()
        ));
    }

    @Override
    public boolean existsByIdempotencyKey(UUID idempotencyKey) {
        return transactionRepository.existsByIdempotencyKey(idempotencyKey);
    }

    @Override
    public boolean ping() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (SQLException exception) {
            log.warn("Database connection check failed", exception);
            return false;
        }
    }
}
