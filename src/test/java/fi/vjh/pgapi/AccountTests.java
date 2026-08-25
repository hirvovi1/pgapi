package fi.vjh.pgapi;

import fi.vjh.pgapi.application.usecase.CreateAccount;
import fi.vjh.pgapi.domain.Account;
import fi.vjh.pgapi.entity.AccountRow;
import fi.vjh.pgapi.infrastructure.jpa.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest // Boots the Spring context and H2 in-memory database for validation
class AccountTests {

    @Autowired
    private CreateAccount createAccount;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void clearAccounts() {
        // Ensure a clean slate in the database before each test execution
        accountRepository.deleteAll();
    }

    @Test
    void createAccountReturnsAndPersistsAccount() {
        // Given & Act - Execute the use case to create an account with cents (12.50 EUR)
        Account account = createAccount.execute("Alice", 1_250);

        // Then - Validate the domain object returned from the use case
        assertThat(account.getId()).isNotNull();
        assertThat(account.getOwnerName()).isEqualTo("Alice");
        assertThat(account.getBalanceInCents()).isEqualTo(1_250);

        // Then - Validate that the data layer mapped and persisted the AccountRow correctly
        AccountRow persistedAccount = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(persistedAccount.getOwnerName()).isEqualTo("Alice");
        assertThat(persistedAccount.getBalanceInCents()).isEqualTo(1_250);
    }

    @Test
    void createAccountGeneratesDifferentIds() {
        // Given & Act - Create multiple accounts
        Account firstAccount = createAccount.execute("Alice", 100);
        Account secondAccount = createAccount.execute("Bob", 200);

        // Then - Verify unique identity generation and correct record count
        assertThat(firstAccount.getId()).isNotEqualTo(secondAccount.getId());
        assertThat(accountRepository.count()).isEqualTo(2);
    }
}
