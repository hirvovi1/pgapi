package fi.vjh.pgapi.infrastructure.queue;

import fi.vjh.pgapi.application.port.TransactionRepositoryPort; // Tuodaan uusi portti mukaan
import fi.vjh.pgapi.application.usecase.TransferMoney;
import fi.vjh.pgapi.domain.CallbackMessage;
import fi.vjh.pgapi.domain.Order;
import fi.vjh.pgapi.domain.TransactionStatus; // Tuodaan enumi
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Objects;
import java.util.UUID;

import static fi.vjh.pgapi.domain.TransactionStatus.PENDING;

@Component
public class PaymentQueueWorker {

    @Value("${pg-api.hostname:pg-api-facade}")
    private String hostName;

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
        return "http://" + hostName + ":8091/api/v1/frontend/";
    }

    @PostConstruct
    public void start() {
        this.workerThread = new Thread(this::processQueue, "payment-worker-thread");
        this.workerThread.start();
        log.info("Asynkroninen maksutyöntekijä (Worker Thread) käynnistetty taustalle.");
    }

    private void processQueue() {
        CallbackMessage currentMessage = null;

        while (running) {
            try {
                currentMessage = messageQueue.take();
                process(currentMessage);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Työntekijäsäie keskeytettiin.");
                break;
            } catch (Exception e) {
                handleError(e, currentMessage);
            }
        }
    }

    private void process(CallbackMessage currentMessage) {
        log.info("Poimittu viesti jonosta. Aloitetaan transaktion {} käsittely...", currentMessage.transactionId());

        if (PENDING.equals(currentMessage.status())) {
            executeTranfer(currentMessage);
            markTransactionAsSuccess(currentMessage);
            Order order = markOrderCompleted(currentMessage);
            emptyCart(order.cartId());
        } else {
            log.warn("Maksun callback ilmoitti virheestä transaktiolle {}. Perutaan varaus.", currentMessage.transactionId());
            transactionRepositoryPort.updateStatus(currentMessage.transactionId(), TransactionStatus.FAILED, "");
        }
    }

    private @NonNull Order markOrderCompleted(CallbackMessage currentMessage) {
        Order order = Objects.requireNonNull(fetchOrder(currentMessage.orderId()));
        finishOrder(order);
        return order;
    }

    private void executeTranfer(CallbackMessage currentMessage) {
        transferMoney.execute(
                currentMessage.accountIdFrom(),
                currentMessage.accountIdTo(),
                currentMessage.amountInCents()
        );
    }

    private void markTransactionAsSuccess(CallbackMessage currentMessage) {
        UUID transactionId = currentMessage.transactionId();
        transactionRepositoryPort.updateStatus(transactionId, TransactionStatus.SUCCESS, "");
        log.info("Transaktio {} merkitty onnistuneeksi kannassa.", transactionId);
    }

    protected void emptyCart(Long cartId) {
        log.info("Tyhjennetään kori id:llä {} palvelusta {}", cartId, baseUrl() + "cart/" + cartId + "/pay");

        String result = RestClient.create().put()
                .uri(baseUrl() + "cart/" + cartId + "/pay")
                .retrieve()
                .body(String.class);
        if (result == null || !result.equals("OK")) {
            log.warn("Korin tyhjennys epäonnistui id:llä {} palvelusta {}. Palvelu palautti: {}", cartId, baseUrl() + "cart/" + cartId + "/pay", result);
        } else {
            log.info("Kori id:llä {} tyhjennetty onnistuneesti palvelusta {}", cartId, baseUrl() + "cart/" + cartId + "/pay");
        }
    }

    private void handleError(Exception e, CallbackMessage currentMessage) {
        log.error("transaktio epäonnistui", e);

        if (currentMessage != null) {
            log.warn("Päivitetään transaktion {} tilaksi FAILED virheen vuoksi.", currentMessage.transactionId());
            transactionRepositoryPort.updateStatus(
                    currentMessage.transactionId(), TransactionStatus.FAILED, e.getMessage());
        }
    }

    protected void finishOrder(Order o) {
        log.info("Merkitään tilaus id:llä {} maksetuksi palvelusta {}", o.id(), baseUrl() + "orders/" + o.id() + "/pay");
        String result = RestClient.create().put()
                .uri(baseUrl() + "orders/" + Objects.requireNonNull(o.id()) + "/pay")
                .retrieve()
                .body(String.class);
        if (result == null || !result.equals("OK")) {
            log.warn("Tilauksen maksu epäonnistui id:llä {} palvelusta {}. Palvelu palautti: {}", o.id(), baseUrl() + "orders/" + o.id() + "/pay", result);
        } else {
            log.info("Tilaus id:llä {} maksettu onnistuneesti palvelusta {}", o.id(), baseUrl() + "orders/" + o.id() + "/pay");
        }
    }

    protected Order fetchOrder(Long orderId) {
        log.info("Haetaan tilaus orderId:llä {} palvelusta {}", orderId, baseUrl() + "orders/" + orderId);
        Order order = RestClient.create().get()
                .uri(baseUrl() + "orders/" + orderId)
                .retrieve()
                .body(Order.class);
        log.info("Tilaus haettu {} palvelusta {}", orderId, baseUrl() + "orders/" + orderId);
        return order;
    }

    @PreDestroy
    public void stop() {
        this.running = false;
        if (workerThread != null) {
            workerThread.interrupt();
        }
        log.info("Maksutyöntekijä pysäytetty siististi.");
    }
}
