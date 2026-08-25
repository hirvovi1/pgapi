package fi.vjh.pgapi;

import fi.vjh.pgapi.application.port.AccountRepositoryPort;
import fi.vjh.pgapi.application.usecase.TransferMoney;
import fi.vjh.pgapi.domain.Account;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TransferMoneyTest {

    // Pure domain unit test - executes instantly without bootstrapping Spring
    private final AccountRepositoryPort repositoryPortMock = mock(AccountRepositoryPort.class);
    private final TransferMoney transferMoney = new TransferMoney(repositoryPortMock);

    @Test
    void shouldThrowExceptionWhenSourceAccountHasInsufficientFunds() {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        Account sourceAccount = new Account(sourceId, "John", 1000L); // 10.00 EUR
        Account targetAccount = new Account(targetId, "Jane", 5000L); // 50.00 EUR

        when(repositoryPortMock.findById(sourceId)).thenReturn(Optional.of(sourceAccount));
        when(repositoryPortMock.findById(targetId)).thenReturn(Optional.of(targetAccount));

        // Act & Assert - Attempting to transfer 40.00 EUR when balance is only 10.00 EUR
        assertThatThrownBy(() -> transferMoney.execute(sourceId, targetId, 4000L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient funds");

        // Verify that database state was never corrupted or saved after exception occurred
        verify(repositoryPortMock, never()).save(any());
    }
}
