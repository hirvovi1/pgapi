package fi.vjh.pgapi.infrastructure.queue;

import fi.vjh.pgapi.application.port.TransactionRepositoryPort;
import fi.vjh.pgapi.application.usecase.TransferMoney;
import fi.vjh.pgapi.domain.CallbackMessage;
import fi.vjh.pgapi.domain.TransactionStatus;
import fi.vjh.pgapi.domain.UnsuccessfulPayment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PaymentChainTest {

    private PaymentQueueWorker worker;
    private TransferMoney transferMoney;
    private TransactionRepositoryPort transactionRepositoryPort;
    private MockRestServiceServer mockServer;

    private final UUID transactionId = UUID.randomUUID();
    private final UUID idempotencyKey = UUID.randomUUID();
    private final UUID accountIdFrom = UUID.randomUUID();
    private final UUID accountIdTo = UUID.randomUUID();
    private final Long orderId = 12345L;
    private final Long cartId = 54321L;
    private final long amountInCents = 10000L;

    @BeforeEach
    void setUp() {
        transferMoney = mock(TransferMoney.class);
        transactionRepositoryPort = mock(TransactionRepositoryPort.class);

        RestClient.Builder restClientBuilder = RestClient.builder()
                .baseUrl("http://pg-api-facade:8091/api/v1/frontend/");

        // MockRestServiceServer kaappaa dynaamisesti kaikki tähän builderiin sidotut kutsut
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();

        worker = new PaymentQueueWorker(
                mock(PaymentMessageQueue.class),
                transferMoney,
                transactionRepositoryPort,
                restClientBuilder.build()
        );
    }

    @Test
    void testSuccessfulPaymentChain() throws UnsuccessfulPayment {
        CallbackMessage message = new CallbackMessage(
                idempotencyKey, transactionId, accountIdFrom, accountIdTo, amountInCents, TransactionStatus.PENDING, orderId
        );

        String base = "http://pg-api-facade:8091/api/v1/frontend";

        mockServer.expect(requestTo(base + "/orders/" + orderId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        String.format("{\"id\": %d, \"cartId\": %d}", orderId, cartId),
                        MediaType.APPLICATION_JSON
                ));

        mockServer.expect(requestTo(base + "/orders/" + orderId + "/pay"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());

        mockServer.expect(requestTo(base + "/carts/" + cartId + "/pay"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());

        worker.process(message);

        verify(transferMoney).execute(accountIdFrom, accountIdTo, amountInCents);
        verify(transactionRepositoryPort).updateStatus(transactionId, TransactionStatus.SUCCESS, "");

        mockServer.verify();
    }


    @Test
    void testPaymentChainFailsWhenTransactionNotPending() throws UnsuccessfulPayment {
        CallbackMessage message = new CallbackMessage(
                idempotencyKey, transactionId, accountIdFrom, accountIdTo, amountInCents, TransactionStatus.FAILED, orderId
        );

        assertThrows(UnsuccessfulPayment.class, () -> worker.process(message));

        verify(transactionRepositoryPort).updateStatus(eq(transactionId), eq(TransactionStatus.FAILED), contains("is not pending"));
        verifyNoInteractions(transferMoney);

        mockServer.verify(); // Varmistaa, ettei HTTP-kutsuja tehty
    }

    @Test
    void testPaymentChainFailsWhenTransferFails() throws UnsuccessfulPayment {
        CallbackMessage message = new CallbackMessage(
                idempotencyKey, transactionId, accountIdFrom, accountIdTo, amountInCents, TransactionStatus.PENDING, orderId
        );

        doThrow(new IllegalArgumentException("Insufficient funds"))
                .when(transferMoney).execute(accountIdFrom, accountIdTo, amountInCents);

        assertThrows(UnsuccessfulPayment.class, () -> worker.process(message));

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(transactionRepositoryPort).updateStatus(eq(transactionId), eq(TransactionStatus.FAILED), messageCaptor.capture());
        assertThat(messageCaptor.getValue()).contains("Insufficient funds");

        mockServer.verify();
    }
}
