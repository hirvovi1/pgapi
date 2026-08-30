package fi.vjh.pgapi.domain;

import fi.vjh.pgapi.PaymentController;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public record CallbackMessage(
        UUID idempotencyKey,
        UUID transactionId,
        UUID accountIdFrom,
        UUID accountIdTo,
        long amountInCents,
        TransactionStatus status
) {
}
