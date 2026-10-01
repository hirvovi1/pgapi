package fi.vjh.pgapi.infrastructure.queue;

import fi.vjh.pgapi.domain.CallbackMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.Stack;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class PaymentMessageQueue {
    private static final Logger log = LoggerFactory.getLogger(PaymentMessageQueue.class);
    private static final int RETRY_LIMIT = 2;

    private final BlockingQueue<CallbackMessage> queue = new LinkedBlockingQueue<>();

    private final Set<UUID> processedKeys = ConcurrentHashMap.newKeySet();

    private final Stack<UUID> retriedKeys = new Stack<>();

    public boolean enqueue(CallbackMessage message) {

        if (!processedKeys.add(message.idempotencyKey())) {
            log.warn("Idempotency block triggered! Duplicate message rejected with key: {}", message.idempotencyKey());
            return false;
        }

        boolean added = queue.offer(message);
        if (added) {
            log.info("Callback message added to background queue successfully. Transaction: {}", message.transactionId());
        }
        return added;
    }

    void requeue(CallbackMessage message) {
        log.info("Requeing message for transaction {} due to technical failure...", message.transactionId());
        if (requeueLimitExceeded(message.idempotencyKey())) {
            log.error("Requeue limit exceeded for transaction {}. Message will be discarded.", message.transactionId());
            return;
        }
        boolean added = queue.offer(message);
        if (added) {
            retriedKeys.push(message.idempotencyKey());
        } else {
            log.error("FATAL: Failed to requeue message for transaction {}. Queue might be full!", message.transactionId());
        }
    }

    private boolean requeueLimitExceeded(UUID idempotencyKey) {
        return retriedKeys.stream().filter(idempotencyKey::equals).count() > RETRY_LIMIT;
    }

    public CallbackMessage take() throws InterruptedException {
        return queue.take();
    }
}
