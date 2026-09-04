package fi.vjh.pgapi;

import fi.vjh.pgapi.infrastructure.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.exc.MismatchedInputException;

import static org.assertj.core.api.Assertions.assertThat;

public class ValidationTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    void shouldReturnBadRequestWithMissingFieldPath() {
        MismatchedInputException exception = MismatchedInputException.from(
                null,
                String.class,
                "Required field is missing"
        );
        exception.prependPath(Object.class, "email");
        exception.prependPath(Object.class, "account");

        ResponseEntity<String> response = exceptionHandler.handleMismatchedInput(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(
                "Invalid request payload: The required field 'account.email' is missing, empty, or an invalid data type."
        );
    }

    @Test
    void shouldReturnBadRequestWithArrayIndexWhenFieldPathContainsCollectionEntry() {
        MismatchedInputException exception = MismatchedInputException.from(
                null,
                String.class,
                "Invalid collection entry"
        );
        exception.prependPath(Object.class, 2);

        ResponseEntity<String> response = exceptionHandler.handleMismatchedInput(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(
                "Invalid request payload: The required field '[2]' is missing, empty, or an invalid data type."
        );
    }

    @Test
    void shouldUseUnknownFieldWhenExceptionHasNoPath() {
        MismatchedInputException exception = MismatchedInputException.from(
                null,
                String.class,
                "Invalid request"
        );

        ResponseEntity<String> response = exceptionHandler.handleMismatchedInput(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(
                "Invalid request payload: The required field 'unknown_field' is missing, empty, or an invalid data type."
        );
    }
}
