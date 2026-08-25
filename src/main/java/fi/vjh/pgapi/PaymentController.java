package fi.vjh.pgapi;

import fi.vjh.pgapi.application.port.TransactionRepositoryPort;
import fi.vjh.pgapi.application.usecase.CreateAccount;
import fi.vjh.pgapi.domain.Account;
import fi.vjh.pgapi.domain.CallbackMessage;
import fi.vjh.pgapi.domain.TransactionStatus;
import fi.vjh.pgapi.entity.TransactionRow;
import fi.vjh.pgapi.infrastructure.jpa.TransactionRepository;
import fi.vjh.pgapi.infrastructure.queue.PaymentMessageQueue;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class PaymentController {
    private final CreateAccount createAccount;
    private final PaymentMessageQueue messageQueue;
    private final TransactionRepositoryPort transactionRepositoryPort;

    public PaymentController(CreateAccount createAccount, PaymentMessageQueue messageQueue, TransactionRepositoryPort transactionRepositoryPort) {
        this.createAccount = createAccount;
        this.messageQueue = messageQueue;
        this.transactionRepositoryPort = transactionRepositoryPort;
    }


    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public Account createAccount(@RequestBody CreateAccountRequest request) {
        return createAccount.execute(request.ownerName(), request.initialBalanceCents());
    }

    public record CreateAccountRequest(String ownerName, long initialBalanceCents) {
    }

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

        // 2. Luo PENDING-maksu & varaa saldo.
        transactionRepositoryPort.createPendingTransaction(message);

        // 3. Palautetaan 202 Accepted. Asiakas tietää, että pyyntö on otettu vastaan ja käsitellään taustalla.
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body("Transfer request accepted for processing. Transaction ID: " + message.transactionId());
    }

    public record TransferRequest(
            UUID idempotencyKey,
            UUID accountIdFrom,
            UUID accountIdTo,
            long amountInCents
    ) {}
}
