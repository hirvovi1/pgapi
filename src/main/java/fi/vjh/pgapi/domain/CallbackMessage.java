package fi.vjh.pgapi.domain;

import java.util.UUID;

public record CallbackMessage(
        UUID idempotencyKey,
        UUID transactionId,
        UUID accountIdFrom,
        UUID accountIdTo,
        long amountInCents,
        TransactionStatus status
) {}
