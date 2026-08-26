package fi.vjh.pgapi;

import fi.vjh.pgapi.domain.TransactionStatus;
import fi.vjh.pgapi.entity.TransactionRow;
import fi.vjh.pgapi.infrastructure.jpa.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TransactionStatusControllerTest {

    @Autowired
    private PaymentController paymentController;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void clearTransactions() {
        transactionRepository.deleteAll();
    }

    @Test
    void shouldReturnHttpStatus200AndCorrectStatusWhenTransactionExists() {
        // Given
        UUID txId = UUID.randomUUID();
        TransactionRow row = new TransactionRow(
                txId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                1_000,
                TransactionStatus.SUCCESS
        );

        transactionRepository.save(row);

        // Act
        ResponseEntity<?> response = paymentController.getTransactionStatus(txId);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("transactionId")).isEqualTo(txId);
        assertThat(body.get("status")).isEqualTo(TransactionStatus.SUCCESS);
    }

    @Test
    void shouldReturnHttpStatus404WhenTransactionDoesNotExist() {
        // Given
        UUID nonExistentId = UUID.randomUUID();

        // Act
        ResponseEntity<?> response = paymentController.getTransactionStatus(nonExistentId);

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("error")).isEqualTo("Transaction not found");
    }
}
