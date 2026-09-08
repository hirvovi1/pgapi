package fi.vjh.pgapi.adapter;

import fi.vjh.pgapi.application.port.TransactionRepositoryPort;
import fi.vjh.pgapi.domain.CallbackMessage;
import fi.vjh.pgapi.domain.TransactionStatus;
import fi.vjh.pgapi.entity.TransactionRow;
import fi.vjh.pgapi.infrastructure.jpa.TransactionRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class TransactionRepositoryAdapter implements TransactionRepositoryPort {

    private final TransactionRepository transactionRepository;

    public TransactionRepositoryAdapter(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
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
}
