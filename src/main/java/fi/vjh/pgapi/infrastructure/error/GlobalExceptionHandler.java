package fi.vjh.pgapi.infrastructure.error;

import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.databind.exc.MismatchedInputException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MismatchedInputException.class)
    public ResponseEntity<String> handleMismatchedInput(MismatchedInputException ex) {
        // 💡 Jackson 3 upgrade: 'getFieldName()' was renamed to 'getPropertyName()'
        String missingField = ex.getPath().stream()
                .map(ref -> ref.getPropertyName() != null ? ref.getPropertyName() : "[" + ref.getIndex() + "]")
                .filter(name -> !name.isEmpty())
                .collect(Collectors.joining("."));

        if (missingField.isEmpty()) {
            missingField = "unknown_field";
        }

        String errorMessage = String.format(
                "Invalid request payload: The required field '%s' is missing, empty, or an invalid data type.",
                missingField
        );

        log.warn("Rejected request with invalid payload: {}", errorMessage);
        return ResponseEntity.badRequest().body(errorMessage);
    }
}
