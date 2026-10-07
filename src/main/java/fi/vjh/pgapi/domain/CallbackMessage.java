package fi.vjh.pgapi.domain;

import org.jspecify.annotations.NonNull;

import java.util.UUID;

public record CallbackMessage(
        UUID idempotencyKey,
        UUID transactionId,
        UUID accountIdFrom,
        UUID accountIdTo,
        long amountInCents,
        TransactionStatus status,
        Long orderId
) {

    @NonNull
    public String toString() {
        return "CallbackMessage(idempotencyKey=%s, transactionId=%s, accountIdFrom=%s, accountIdTo=%s, amountInCents=%d, status=%s, orderId=%s)".formatted(
                idempotencyKey, transactionId, accountIdFrom, accountIdTo, amountInCents, status, orderId);
    }
}
