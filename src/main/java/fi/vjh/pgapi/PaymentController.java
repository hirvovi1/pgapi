package fi.vjh.pgapi;

import fi.vjh.pgapi.application.port.TransactionRepositoryPort;
import fi.vjh.pgapi.application.usecase.CreateAccount;
import fi.vjh.pgapi.domain.Account;
import fi.vjh.pgapi.domain.CallbackMessage;
import fi.vjh.pgapi.domain.TransactionStatus;
import fi.vjh.pgapi.infrastructure.queue.PaymentMessageQueue;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST endpoints for account creation and asynchronous money transfers.
 */
@RestController
public class PaymentController {
    private final CreateAccount createAccount;
    private final PaymentMessageQueue messageQueue;
    private final TransactionRepositoryPort transactionRepositoryPort;

    /**
     * Creates a controller backed by the account service, message queue, and
     * transaction repository.
     *
     * @param createAccount account creation use case
     * @param messageQueue queue used to process transfers asynchronously
     * @param transactionRepositoryPort port used to persist transactions
     */
    public PaymentController(CreateAccount createAccount, PaymentMessageQueue messageQueue, TransactionRepositoryPort transactionRepositoryPort) {
        this.createAccount = createAccount;
        this.messageQueue = messageQueue;
        this.transactionRepositoryPort = transactionRepositoryPort;
    }


    /**
     * Creates an account with the requested owner and initial balance.
     *
     * @param request account owner and initial balance
     * @return the created account
     */
    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public Account createAccount(@RequestBody CreateAccountRequest request) {
        return createAccount.execute(request.ownerName(), request.initialBalanceCents());
    }

    /**
     * Request body for account creation.
     *
     * @param ownerName account owner's name
     * @param initialBalanceCents initial balance in cents
     */
    public record CreateAccountRequest(String ownerName, long initialBalanceCents) {
    }

    /**
     * Queues a money transfer for asynchronous processing.
     *
     * @param request transfer details
     * @return an accepted response with the transaction ID, or a conflict when
     *         the idempotency key has already been used
     */
    @PostMapping("/transfer")
    public ResponseEntity<String> transfer(@RequestBody TransferRequest request) {

        UUID transactionId = UUID.randomUUID();
        CallbackMessage message = new CallbackMessage(
                request.idempotencyKey(),
                transactionId,
                request.accountIdFrom(),
                request.accountIdTo(),
                request.amountInCents(),
                TransactionStatus.PENDING
        );


        boolean addedForProcessing = messageQueue.enqueue(message);

        if (!addedForProcessing) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Idempotency key already used. Request ignored.");
        }

        transactionRepositoryPort.createPendingTransaction(message);

        // 3. Palautetaan 202 Accepted. Asiakas tietää, että pyyntö on otettu vastaan ja käsitellään taustalla.
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body("Transfer request accepted for processing. Transaction ID: " + message.transactionId());
    }

    /**
     * Request body for a money transfer.
     *
     * @param idempotencyKey key preventing duplicate transfer requests
     * @param accountIdFrom source account ID
     * @param accountIdTo destination account ID
     * @param amountInCents amount to transfer in cents
     */
    public record TransferRequest(
            UUID idempotencyKey,
            UUID accountIdFrom,
            UUID accountIdTo,
            long amountInCents
    ) {}

    @GetMapping("/{id}/status")
    public ResponseEntity<?> getTransactionStatus(@PathVariable UUID id) {
        return transactionRepositoryPort.findStatusById(id)
                .map(status -> ResponseEntity.ok(Map.of(
                        "transactionId", id,
                        "status", status
                )))
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of(
                        "error", "Transaction not found",
                        "transactionId", id
                )));
    }
}
