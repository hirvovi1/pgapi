package fi.vjh.pgapi.infrastructure.queue;

import fi.vjh.pgapi.application.port.TransactionRepositoryPort; // Tuodaan uusi portti mukaan
import fi.vjh.pgapi.application.usecase.TransferMoney;
import fi.vjh.pgapi.domain.CallbackMessage;
import fi.vjh.pgapi.domain.Order;
import fi.vjh.pgapi.domain.TransactionStatus; // Tuodaan enumi
import fi.vjh.pgapi.domain.UnsuccessfulPayment;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.UUID;

import static fi.vjh.pgapi.domain.TransactionStatus.PENDING;

@Component
public class PaymentQueueWorker {


    private static final Logger log = LoggerFactory.getLogger(PaymentQueueWorker.class);

    private final PaymentMessageQueue messageQueue;
    private final TransferMoney transferMoney;
    private final TransactionRepositoryPort transactionRepositoryPort;
    private Thread workerThread;
    private volatile boolean running = true;

    public PaymentQueueWorker(PaymentMessageQueue messageQueue, TransferMoney transferMoney, TransactionRepositoryPort transactionRepositoryPort) {
        this.messageQueue = messageQueue;
        this.transferMoney = transferMoney;
        this.transactionRepositoryPort = transactionRepositoryPort;
    }

    String baseUrl() {
        return "http://pg-api-facade:8091/api/v1/frontend/";
    }

    @PostConstruct
    public void start() {
        this.workerThread = new Thread(this::processQueue, "payment-worker-thread");
        this.workerThread.start();
        log.info(" Worker Thread started");
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
            } catch (Exception e) {
                handleError(e, currentMessage);
            }
        }
    }

    private void process(CallbackMessage currentMessage) {
        log.info("Message picked from queue. Starting transaction {} processing...", currentMessage.transactionId());
        log.info("current msg {}", currentMessage);
        if (PENDING.equals(currentMessage.status())) {
            try {
                executeTranfer(currentMessage);
                markTransactionAsSuccess(currentMessage);
                Order order = markOrderCompleted(currentMessage);
                emptyCart(order.cartId());
            } catch (UnsuccessfulPayment e) {
                log.error("Payment transaction {} failed: {}", currentMessage.transactionId(), e.getMessage());
                transactionRepositoryPort.updateStatus(currentMessage.transactionId(), TransactionStatus.FAILED, e.getMessage());
            }
        } else {
            log.warn("Payment status was incorrect. Transaction {} Cancel reservation.", currentMessage.transactionId());
            transactionRepositoryPort.updateStatus(currentMessage.transactionId(), TransactionStatus.FAILED, "");
        }
    }

    private void executeTranfer(CallbackMessage currentMessage) throws UnsuccessfulPayment {
        log.info("Transaction {} is being processed...", currentMessage.transactionId());

        try {
            transferMoney.execute(
                    currentMessage.accountIdFrom(),
                    currentMessage.accountIdTo(),
                    currentMessage.amountInCents()
            );
        } catch (Exception e) {
            throw new UnsuccessfulPayment("Transaction " + currentMessage.transactionId() + " failed: " + e.getMessage(), e);
        }
    }

    private void markTransactionAsSuccess(CallbackMessage currentMessage) throws UnsuccessfulPayment {
        log.info("Transaction {} is being marked as successful...", currentMessage.transactionId());
        UUID transactionId = currentMessage.transactionId();

        try {
            transactionRepositoryPort.updateStatus(transactionId, TransactionStatus.SUCCESS, "");
        } catch (Exception e) {
            throw new UnsuccessfulPayment("updating transaction state failed", e);
        }
        log.info("Transaction {} marked as successful in database.", transactionId);
    }

    private @NonNull Order markOrderCompleted(CallbackMessage currentMessage) throws UnsuccessfulPayment {
        log.info("marking order {} as completed", currentMessage.orderId());
        Order order = Objects.requireNonNull(fetchOrder(currentMessage.orderId()));
        finishOrder(order);
        return order;
    }

    protected void finishOrder(Order o) throws UnsuccessfulPayment {
        log.info("Marking order with id {} as paid from service {}", o.id(), baseUrl() + "orders/" + o.id() + "/pay");
        RestClient.ResponseSpec result = RestClient.create().put()
                .uri(baseUrl() + "orders/" + Objects.requireNonNull(o.id()) + "/pay")
                .retrieve();

        log.info("order service response was http {}", result.toBodilessEntity().getStatusCode().value());

        if (result.toBodilessEntity().getStatusCode().isError()) {
            log.warn("Order update failed with id {} from service {}. Service returned: {}", o.id(), baseUrl() + "orders/" + o.id() + "/pay", result);
            throw new UnsuccessfulPayment("order " + o.id() + " payment failed");
        } else {
            log.info("Order with id {} paid successfully from service {}", o.id(), baseUrl() + "orders/" + o.id() + "/pay");
        }
    }

    protected void emptyCart(Long cartId) {
        log.info("Emptying cart with id {} from service {}", cartId, baseUrl() + "cart/" + cartId + "/pay");

        String result = RestClient.create().put()
                .uri(baseUrl() + "cart/" + cartId + "/pay")
                .retrieve()
                .body(String.class);
        if (result == null || !result.equals("OK")) {
            log.warn("Cart emptying failed with id {} from service {}. Service returned: {}", cartId, baseUrl() + "cart/" + cartId + "/pay", result);
        } else {
            log.info("Cart with id {} emptied successfully from service {}", cartId, baseUrl() + "cart/" + cartId + "/pay");
        }
    }

    private void handleError(Exception e, CallbackMessage currentMessage) {
        log.error("transaction failed", e);

        if (currentMessage != null) {
            log.warn("Updating transaction {} status to FAILED due to error.", currentMessage.transactionId());
            transactionRepositoryPort.updateStatus(
                    currentMessage.transactionId(), TransactionStatus.FAILED, e.getMessage());
        }
    }

    protected Order fetchOrder(Long orderId) {
        log.info("Fetching order with orderId {} from service {}", orderId, baseUrl() + "orders/" + orderId);
        Order order = RestClient.create().get()
                .uri(baseUrl() + "orders/" + orderId)
                .retrieve()
                .body(Order.class);
        log.info("Order fetched {} from service {}", orderId, baseUrl() + "orders/" + orderId);
        return order;
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
