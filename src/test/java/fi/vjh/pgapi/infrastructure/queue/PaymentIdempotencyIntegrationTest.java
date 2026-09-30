package fi.vjh.pgapi.infrastructure.queue;

import fi.vjh.pgapi.application.port.TransactionRepositoryPort;
import fi.vjh.pgapi.application.usecase.TransferMoney;
import fi.vjh.pgapi.domain.CallbackMessage;
import fi.vjh.pgapi.domain.TransactionStatus;
import fi.vjh.pgapi.domain.UnsuccessfulPayment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PaymentIdempotencyIntegrationTest {

    private PaymentQueueWorker worker;
    private PaymentMessageQueue messageQueue;
    private TransferMoney transferMoney;
    private TransactionRepositoryPort transactionRepositoryPort;
    private MockRestServiceServer mockServer;

    private final UUID transactionId = UUID.randomUUID();
    private final UUID idempotencyKey = UUID.randomUUID();
    private final UUID accountIdFrom = UUID.randomUUID();
    private final UUID accountIdTo = UUID.randomUUID();
    private final Long orderId = 999L;
    private final Long cartId = 888L;
    private final long amountInCents = 5000L;

    @BeforeEach
    void setUp() {
        transferMoney = mock(TransferMoney.class);
        transactionRepositoryPort = mock(TransactionRepositoryPort.class);

        messageQueue = new PaymentMessageQueue();

        RestClient.Builder restClientBuilder = RestClient.builder()
                .baseUrl("http://pg-api-facade:8091/api/v1/frontend/");

        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();

        worker = new PaymentQueueWorker(
                messageQueue,
                transferMoney,
                transactionRepositoryPort,
                restClientBuilder.build()
        );
    }

    @Test
    void testSystemRecoversFromTechnicalFailureUsingIdempotencyAndRequeue() throws Exception {
        CallbackMessage message = new CallbackMessage(
                idempotencyKey, transactionId, accountIdFrom, accountIdTo, amountInCents, TransactionStatus.PENDING, orderId
        );

        String base = "http://pg-api-facade:8091/api/v1/frontend";

        firstTryFailsAndReQueuesMessage(base, message);

        mockServer.reset(); // -------

        secondTrySucceeds(base, message);

        mockServer.verify();
    }

    private void firstTryFailsAndReQueuesMessage(String base, CallbackMessage message) {
        mockServer.expect(requestTo(base + "/orders/" + orderId)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(String.format("{\"id\": %d, \"cartId\": %d}", orderId, cartId), MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(base + "/orders/" + orderId + "/pay")).andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        try {
            worker.process(message);
        } catch (Exception e) {
            messageQueue.requeue(message);
        }

        verify(transferMoney, times(1)).execute(accountIdFrom, accountIdTo, amountInCents);
        verify(transactionRepositoryPort, never()).updateStatus(eq(transactionId), eq(TransactionStatus.FAILED), anyString());
    }

    private void secondTrySucceeds(String base, CallbackMessage message) throws UnsuccessfulPayment, InterruptedException {

        assertThat(messageQueue.take()).isEqualTo(message);

        createMockExpectationsForSecondTry(base);

        when(transactionRepositoryPort.existsByIdempotencyKey(eq(idempotencyKey))).thenReturn(true);

        worker.process(message);

        verify(transactionRepositoryPort, times(2)).updateStatus(transactionId, TransactionStatus.SUCCESS, "");
        verify(transferMoney, times(1)).execute(accountIdFrom, accountIdTo, amountInCents);
    }

    private void createMockExpectationsForSecondTry(String base) {
        mockServer.expect(requestTo(base + "/orders/" + orderId)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(String.format("{\"id\": %d, \"cartId\": %d}", orderId, cartId), MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(base + "/orders/" + orderId + "/pay")).andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());

        mockServer.expect(requestTo(base + "/cart/" + cartId + "/pay")).andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());
    }
}
