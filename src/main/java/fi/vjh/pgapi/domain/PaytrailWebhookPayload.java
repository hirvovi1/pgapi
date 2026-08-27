package fi.vjh.pgapi.domain;

import java.util.UUID;

public record PaytrailWebhookPayload(
        UUID transactionId,
        String status,
        long amountCents
) {}
