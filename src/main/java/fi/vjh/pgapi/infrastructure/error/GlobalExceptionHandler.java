package fi.vjh.pgapi.infrastructure.error;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.databind.exc.MismatchedInputException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

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

        return ResponseEntity.badRequest().body(errorMessage);
    }
}
