package fi.vjh.pgapi.application.port;

import fi.vjh.pgapi.domain.CallbackMessage;
import fi.vjh.pgapi.domain.TransactionStatus;

import java.util.Optional;
import java.util.UUID;

public interface TransactionRepositoryPort {
    void createPendingTransaction(CallbackMessage message);
    void updateStatus(UUID id, TransactionStatus status, String message);
    Optional<TransactionStatusInfo> findStatusById(UUID id);
    Optional<CallbackMessage> findById(UUID id);
    boolean existsByIdempotencyKey(UUID idempotencyKey);

    boolean ping();

    record TransactionStatusInfo(TransactionStatus status, String message) {
    }
}
