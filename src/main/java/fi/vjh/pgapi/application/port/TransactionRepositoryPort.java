package fi.vjh.pgapi.application.port;

import fi.vjh.pgapi.domain.CallbackMessage;
import fi.vjh.pgapi.domain.TransactionStatus;
import java.util.UUID;

public interface TransactionRepositoryPort {
    void createPendingTransaction(CallbackMessage message);
    void updateStatus(UUID id, TransactionStatus status);
}

