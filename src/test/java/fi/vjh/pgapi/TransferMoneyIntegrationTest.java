package fi.vjh.pgapi;

import fi.vjh.pgapi.entity.AccountRow;
import fi.vjh.pgapi.infrastructure.jpa.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
// Boots Spring context, HTTP callback endpoint, H2 in-memory DB, and the async background Worker
class TransferMoneyIntegrationTest {

    @Autowired
    private PaymentController paymentController;

    @Autowired
    private AccountRepository accountRepository;

    private UUID sourceAccountId;
    private UUID targetAccountId;

    @BeforeEach
    void setUp() {
        sourceAccountId = UUID.randomUUID();
        targetAccountId = UUID.randomUUID();

        // Seed clean database state before each test execution
        accountRepository.save(new AccountRow(sourceAccountId, "John Doe", 10000L)); // 100.00 EUR
        accountRepository.save(new AccountRow(targetAccountId, "Jane Doe", 5000L));   // 50.00 EUR
    }

    @Test
    void shouldProcessPaymentAsynchronouslyAndSettleBalances() {
        UUID idempotencyKey = UUID.randomUUID();
        PaymentController.TransferRequest request = new PaymentController.TransferRequest(
                idempotencyKey, sourceAccountId, targetAccountId, 4000L
        );

        // Act - Invoke controller (Returns immediately with 202 Accepted)
        ResponseEntity<?> response = paymentController.transfer(request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        PaymentController.TransferResponse responseBody =
                (PaymentController.TransferResponse) response.getBody();
        assertThat(responseBody).isNotNull();
        assertThat(responseBody.paymentUrl()).startsWith("https://paytrail.mock");

        // Assert - Use Awaitility to poll H2 database until background Worker completes the transfer
        await().atMost(Duration.ofSeconds(6))
                .untilAsserted(() -> {
                    AccountRow updatedSource = accountRepository.findById(sourceAccountId).orElseThrow();
                    AccountRow updatedTarget = accountRepository.findById(targetAccountId).orElseThrow();

                    assertThat(updatedSource.getBalanceInCents()).isEqualTo(6000L);  // 10000 - 4000
                    assertThat(updatedTarget.getBalanceInCents()).isEqualTo(9000L);  // 5000 + 4000
                });
    }

    @Test
    void shouldBlockDuplicateRequestsWithSameIdempotencyKey() {
        UUID idempotencyKey = UUID.randomUUID();
        PaymentController.TransferRequest request = new PaymentController.TransferRequest(
                idempotencyKey, sourceAccountId, targetAccountId, 1000L
        );

        // Act & Assert 1 - First message passes through and goes into the queue
        ResponseEntity<?> response1 = paymentController.transfer(request);
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // Act & Assert 2 - Immediate duplicate execution fails with 409 Conflict due to idempotency block
        ResponseEntity<?> response2 = paymentController.transfer(request);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response2.getBody()).isEqualTo("Idempotency key already used. Request ignored.");
    }
}
