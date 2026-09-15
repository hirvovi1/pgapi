package fi.vjh.pgapi;

import fi.vjh.pgapi.application.port.TransactionRepositoryPort;
import fi.vjh.pgapi.application.usecase.CreateAccount;
import fi.vjh.pgapi.domain.Account;
import fi.vjh.pgapi.domain.CallbackMessage;
import fi.vjh.pgapi.domain.PaytrailWebhookPayload;
import fi.vjh.pgapi.domain.TransactionStatus;
import fi.vjh.pgapi.infrastructure.mock.PaytrailMockProvider;
import fi.vjh.pgapi.infrastructure.queue.PaymentMessageQueue;
import fi.vjh.pgapi.infrastructure.security.SecurityUtils;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * REST endpoints for account creation and asynchronous money transfers.
 */
@RestController
public class PaymentController {
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final CreateAccount accountService;
    private final PaymentMessageQueue messageQueue;
    private final TransactionRepositoryPort transactionRepositoryPort;
    private final PaytrailMockProvider paytrailMockProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();


    /**
     * Creates a controller backed by the account service, message queue, and
     * transaction repository.
     *
     * @param accountService account creation use case
     * @param messageQueue queue used to process transfers asynchronously
     * @param transactionRepositoryPort port used to persist transactions
     */
    public PaymentController(CreateAccount accountService, PaymentMessageQueue messageQueue,
                             TransactionRepositoryPort transactionRepositoryPort, PaytrailMockProvider paytrailMockProvider) {
        this.accountService = accountService;
        this.messageQueue = messageQueue;
        this.transactionRepositoryPort = transactionRepositoryPort;
        this.paytrailMockProvider = paytrailMockProvider;
    }

    @GetMapping("/db/health")
    public ResponseEntity<Boolean> pingDatabase() {
        return ResponseEntity.ok(transactionRepositoryPort.ping());
    }

    @DeleteMapping("/accounts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@PathVariable UUID id) {
        accountService.delete(id);
    }

    @GetMapping("/accounts")
    public ResponseEntity<List<Account>> getAllAccounts() {
        List<Account> accounts = accountService.getAllAccounts();
        log.info("Listed {} accounts", accounts.size());
        return ResponseEntity.ok(accounts);
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
        Account account = accountService.create(request.ownerName(), request.balanceInCents());
        log.info("Created account {} with initial balance of {} cents",
                account.getId(), request.balanceInCents());
        return account;
    }

    /**
     * Request body for account creation.
     *
     * @param ownerName account owner's name
     * @param balanceInCents initial balance in cents
     */
    public record CreateAccountRequest(String ownerName, long balanceInCents) {
    }

    public record TransferResponse(
            String message,
            UUID transactionId,
            String paymentUrl
    ) {}


    @PostMapping("/transfer")
    public ResponseEntity<?> transfer(@RequestBody TransferRequest request) {

        UUID transactionId = UUID.randomUUID();
        log.info("Received transfer request {} from account {} to account {} for {} cents",
                transactionId, request.accountIdFrom(), request.accountIdTo(), request.amountInCents());

        if (transactionRepositoryPort.existsByIdempotencyKey(request.idempotencyKey())) {
            log.warn("Rejected duplicate transfer request {} using idempotency key {}",
                    transactionId, request.idempotencyKey());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Idempotency key already used. Request ignored.");
        }

        // 1. TALLENNUS: Luodaan PENDING-transaktio tietokantaan ennen maksua
        transactionRepositoryPort.createPendingTransaction(createPendingTransactionMsg(request, transactionId));

        // 2. MOCK PAYTRAIL CALL: Generoidaan maksusivun URL ja käynnistetään asynkroninen ajastin
        String redirectUrl = paytrailMockProvider.initiatePayment(transactionId, request.amountInCents());

        TransferResponse response = new TransferResponse(
                "Redirect to payment gateway",
                transactionId,
                redirectUrl
        );

        log.info("Accepted transfer {} and initiated payment", transactionId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    private static @NonNull CallbackMessage createPendingTransactionMsg(TransferRequest request, UUID transactionId) {
        return new CallbackMessage(
                request.idempotencyKey(),
                transactionId,
                request.accountIdFrom(),
                request.accountIdTo(),
                request.amountInCents(),
                TransactionStatus.PENDING
        );
    }

    @PostMapping("/api/v1/callbacks/paytrail")
    public ResponseEntity<Void> paytrailCallback(
            @RequestHeader(value = "X-Paytrail-Signature", required = false) String signature,
            @RequestBody byte[] rawBody) {

        if (signature == null || signature.isBlank()) {
            log.warn("Rejected Paytrail callback without a signature (body length: {} bytes)", rawBody.length);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String bodyText = new String(rawBody, StandardCharsets.UTF_8);

        if (!SecurityUtils.isValidSignature(bodyText, normalizeSHASignature(signature), SecurityUtils.SECRET)) {
            log.warn("Rejected Paytrail callback with an invalid signature (body length: {} bytes)",
                    rawBody.length);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        PaytrailWebhookPayload payload = objectMapper.readValue(rawBody, PaytrailWebhookPayload.class);
        log.info("Received valid Paytrail callback for transaction {} with status {}",
                payload.transactionId(), payload.status());

        Optional<CallbackMessage> message = createMessageFromRequest(payload);
        if (message.isEmpty() || !messageQueue.enqueue(message.get())) {
            log.warn("Ignored Paytrail callback for unknown or already queued transaction {}",
                    payload.transactionId());
            return ResponseEntity.notFound().build();
        }

        log.info("Accepted Paytrail callback for transaction {}", payload.transactionId());
        return ResponseEntity.accepted().build();
    }

    private static @NonNull String normalizeSHASignature(String signature) {
        String normalizedSignature = signature.trim();
        if (normalizedSignature.startsWith("sha256=")) {
            normalizedSignature = normalizedSignature.substring("sha256=".length());
        }
        return normalizedSignature;
    }

    private Optional<CallbackMessage> createMessageFromRequest(PaytrailWebhookPayload payload) {
        return transactionRepositoryPort.findById(payload.transactionId()).
                map(transaction -> new CallbackMessage(
                transaction.idempotencyKey(),
                transaction.transactionId(),
                transaction.accountIdFrom(),
                transaction.accountIdTo(),
                payload.amountCents(),
                "OK".equalsIgnoreCase(payload.status())
                        ? TransactionStatus.PENDING
                        : TransactionStatus.FAILED
        ));
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
    ) {    }

    @GetMapping("/{id}/status")
    public ResponseEntity<TransactionStatusResponse> getTransactionStatus(@PathVariable UUID id) {
        return transactionRepositoryPort.findStatusById(id)
                .map(statusInfo -> {
                    log.info("Returned status {} for transaction {}", statusInfo.status(), id);
                    return ResponseEntity.ok(new TransactionStatusResponse(
                            id, statusInfo.status(), statusInfo.message()
                    ));
                })
                .orElseGet(() -> {
                    log.warn("Transaction {} was not found", id);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(new TransactionStatusResponse(
                                    id, TransactionStatus.FAILED,  "Transaction not found"
                            ));
                });
    }

    public record TransactionStatusResponse(
            UUID transactionId ,
            TransactionStatus status ,
            String message) {}


}
