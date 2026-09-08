package fi.vjh.pgapi.infrastructure.queue;

import fi.vjh.pgapi.application.port.TransactionRepositoryPort; // Tuodaan uusi portti mukaan
import fi.vjh.pgapi.application.usecase.TransferMoney;
import fi.vjh.pgapi.domain.CallbackMessage;
import fi.vjh.pgapi.domain.TransactionStatus; // Tuodaan enumi
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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
                // Tämä blokkaa säikeen (0% CPU-kulutus), kunnes jonoon tulee viesti
                currentMessage = messageQueue.take();

                log.info("Poimittu viesti jonosta. Aloitetaan transaktion {} käsittely...", currentMessage.transactionId());

                if (PENDING.equals(currentMessage.status())) {

                    transferMoney.execute(
                            currentMessage.accountIdFrom(),
                            currentMessage.accountIdTo(),
                            currentMessage.amountInCents()
                    );

                    // Päivitetään olemassa olevan transaktion tila SUCCESS-muotoon kannassa portin kautta
                    transactionRepositoryPort.updateStatus(currentMessage.transactionId(), TransactionStatus.SUCCESS, "");
                    log.info("Transaktio {} merkitty onnistuneeksi kannassa.", currentMessage.transactionId());

                } else {
                    log.warn("Maksun callback ilmoitti virheestä transaktiolle {}. Perutaan varaus.", currentMessage.transactionId());
                    transactionRepositoryPort.updateStatus(currentMessage.transactionId(), TransactionStatus.FAILED, "");
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Työntekijäsäie keskeytettiin.");
                break;
            } catch (Exception e) {
                log.error("transaktio epäonnistui", e);

                if (currentMessage != null) {
                    log.warn("Päivitetään transaktion {} tilaksi FAILED virheen vuoksi.", currentMessage.transactionId());
                    transactionRepositoryPort.updateStatus(
                            currentMessage.transactionId(), TransactionStatus.FAILED, e.getMessage());
                }
            }
        }
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
