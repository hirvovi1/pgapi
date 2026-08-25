package fi.vjh.pgapi.infrastructure.queue;

import fi.vjh.pgapi.domain.CallbackMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class PaymentMessageQueue {
    private static final Logger log = LoggerFactory.getLogger(PaymentMessageQueue.class);

    // 1. Thread-safe muistijono asynkronisille callbackeille
    private final BlockingQueue<CallbackMessage> queue = new LinkedBlockingQueue<>();

    // 2. Hajautettu "välimuisti" jo käsitellyille idempotenssiavaimille (estää duplikaatit)
    private final Set<UUID> processedKeys = ConcurrentHashMap.newKeySet();


    /**
     * Vastaanottaa callback-viestin Paytraililta.
     * Palauttaa true, jos viesti oli uusi ja otettiin jonoon.
     * Palauttaa false, jos kyseessä oli duplikaatti (Idempotent block).
     */
    public boolean enqueue(CallbackMessage message) {
        // Atominen tarkistus: Jos avain on jo setissä, kyseessä on duplikaatti!
        if (!processedKeys.add(message.idempotencyKey())) {
            log.warn("Idempotentti esto laukesi! Duplikaattiviesti hylätty avaimella: {}", message.idempotencyKey());
            return false;
        }

        boolean added = queue.offer(message);
        if (added) {
            log.info("Callback-viesti lisätty taustajonoon onnistuneesti. Transaktio: {}", message.transactionId());
        }
        return added;
    }

    /**
     * Taustaprosessi (Worker) kutsuu tätä. Estää säikeen (blocks), kunnes jonoon tulee viesti.
     */
    public CallbackMessage take() throws InterruptedException {
        return queue.take();
    }
}
