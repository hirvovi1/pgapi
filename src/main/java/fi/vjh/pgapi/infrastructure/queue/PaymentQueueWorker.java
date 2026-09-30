package fi.vjh.pgapi.infrastructure.queue;

import fi.vjh.pgapi.application.port.TransactionRepositoryPort;
import fi.vjh.pgapi.application.usecase.TransferMoney;
import fi.vjh.pgapi.domain.CallbackMessage;
import fi.vjh.pgapi.domain.Order;
import fi.vjh.pgapi.domain.TransactionStatus;
import fi.vjh.pgapi.domain.UnsuccessfulPayment;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Objects;
import java.util.UUID;

import static fi.vjh.pgapi.domain.TransactionStatus.PENDING;

@Component
public class PaymentQueueWorker {

    private static final Logger log = LoggerFactory.getLogger(PaymentQueueWorker.class);

    private final PaymentMessageQueue messageQueue;
    private final TransferMoney transferMoney;
    private final TransactionRepositoryPort transactionRepositoryPort;
    private final RestClient restClient;
    private Thread workerThread;
    private volatile boolean running = true;

    public PaymentQueueWorker(PaymentMessageQueue messageQueue,
                              TransferMoney transferMoney,
                              TransactionRepositoryPort transactionRepositoryPort,
                              RestClient restClient) {
        this.messageQueue = messageQueue;
        this.transferMoney = transferMoney;
        this.transactionRepositoryPort = transactionRepositoryPort;
        this.restClient = restClient;
    }

    @PostConstruct
    public void start() {
        this.workerThread = new Thread(this::processQueue, "payment-worker-thread");
        this.workerThread.start();
        log.info("Worker Thread started");
    }

    private void processQueue() {
        CallbackMessage currentMessage = null;

        while (running) {
            try {
                currentMessage = messageQueue.take();
                process(currentMessage);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Worker thread interrupted.");
                break;
            } catch (UnsuccessfulPayment e) {
                log.error("Payment transaction {} failed definitively: {}", currentMessage.transactionId(), e.getMessage());
            } catch (Exception e) {
                log.error("Technical error processing transaction {}. Requeuing for retry.",
                        currentMessage != null ? currentMessage.transactionId() : "unknown", e);

                if (currentMessage != null) {
                    messageQueue.requeue(currentMessage);
                    try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }
        }
    }

    void process(CallbackMessage currentMessage) throws UnsuccessfulPayment {
        log.info("Message picked from queue. Starting transaction {} processing...", currentMessage.transactionId());
        log.debug("current message: {}", currentMessage);

        try {
            checkPaymentStatus(currentMessage);
            executeTransfer(currentMessage);
            markTransactionAsSuccess(currentMessage);
            Order order = markOrderCompleted(currentMessage);
            emptyCart(order.cartId());
        } catch (UnsuccessfulPayment e) {
            transactionRepositoryPort.updateStatus(currentMessage.transactionId(), TransactionStatus.FAILED, e.getMessage());
            throw e;
        }
    }









    private void checkPaymentStatus(CallbackMessage currentMessage) throws UnsuccessfulPayment {
        if (PENDING.equals(currentMessage.status())) {
            log.debug("Transaction {} is pending. Proceeding with transfer.", currentMessage.transactionId());
        } else {
            throw new UnsuccessfulPayment("Transaction " + currentMessage.transactionId() + " is not pending. Current status: " + currentMessage.status());
        }
    }

    private void executeTransfer(CallbackMessage currentMessage) throws UnsuccessfulPayment {
        log.info("Money transfer started for tx: {}", currentMessage.transactionId());

        try {
            if (!isUsed(currentMessage.idempotencyKey())) {
                transferMoney.execute(
                        currentMessage.accountIdFrom(),
                        currentMessage.accountIdTo(),
                        currentMessage.amountInCents()
                );
            } else {
                log.info("duplicate money transfer {} detected. skipping.", currentMessage.transactionId());
            }
        } catch (Exception e) {
            throw new UnsuccessfulPayment("Transaction " + currentMessage.transactionId() + " failed: " + e.getMessage(), e);
        }
    }

    private boolean isUsed(UUID idempotencyKey) {
        return transactionRepositoryPort.existsByIdempotencyKey(idempotencyKey);
    }

    private void markTransactionAsSuccess(CallbackMessage currentMessage) {
        log.info("Transaction {} is being marked as successful...", currentMessage.transactionId());
        UUID transactionId = currentMessage.transactionId();
        transactionRepositoryPort.updateStatus(transactionId, TransactionStatus.SUCCESS, "");
        log.info("Transaction {} marked as successful in database.", transactionId);
    }

    private @NonNull Order markOrderCompleted(CallbackMessage currentMessage) {
        Long orderId = currentMessage.orderId();
        log.info("Marking order with id {} as paid", orderId);

        Order order = Objects.requireNonNull(fetchOrder(orderId));
        finishOrder(order);
        return order;
    }

    protected void finishOrder(Order o) {
        Long orderId = Objects.requireNonNull(o.id());

        RestClient.ResponseSpec result = restClient.put()
                .uri("/orders/{id}/pay", orderId)
                .retrieve();

        if (result.toBodilessEntity().getStatusCode().isError()) {
            log.warn("Order update failed with id {}", orderId);
            throw new RuntimeException("order " + orderId + " payment failed");
        } else {
            log.info("Order with id {} was updated successfully as paid", orderId);
        }
    }

    protected void emptyCart(Long cartId) {
        log.info("Emptying cart with id {}", cartId);

        RestClient.ResponseSpec response = restClient.put()
                .uri("/cart/{id}/pay", cartId)
                .retrieve();

        if (response.toBodilessEntity().getStatusCode().isError()) {
            log.warn("Cart emptying failed with id {}", cartId);
        } else {
            log.info("Cart with id {} emptied successfully", cartId);
        }
    }

    protected Order fetchOrder(Long orderId) {
        log.debug("Fetching order with orderId {}", orderId);

        Order order = restClient.get()
                .uri("/orders/{id}", orderId) // Siisti polkuparametri
                .retrieve()
                .body(Order.class);

        log.debug("Order fetched {}", orderId);
        return order;
    }

    private void handleError(Exception e, CallbackMessage currentMessage) {
        log.error("transaction failed", e);

        if (currentMessage != null) {
            log.warn("Updating transaction {} status to FAILED due to error.", currentMessage.transactionId());
            transactionRepositoryPort.updateStatus(
                    currentMessage.transactionId(), TransactionStatus.FAILED, e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        this.running = false;
        if (workerThread != null) {
            workerThread.interrupt();
        }
        log.info("Payment worker stopped gracefully.");
    }
}
