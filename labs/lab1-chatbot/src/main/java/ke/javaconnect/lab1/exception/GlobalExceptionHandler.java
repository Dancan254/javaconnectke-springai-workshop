package ke.javaconnect.lab1.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleValidation(ConstraintViolationException ex) {
        log.warn("Validation failed: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create("https://javaconnect.ke/problems/validation-error"));
        problem.setTitle("Validation Error");
        problem.setDetail(ex.getMessage());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneral(Exception ex) {
        if (isGroqError(ex)) {
            log.error("Groq error: {}", ex.getMessage());
            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
            problem.setType(URI.create("https://javaconnect.ke/problems/model-unavailable"));
            problem.setTitle("Model Unavailable");
            problem.setDetail("Groq request failed. Check GROQ_API_KEY and base-url configuration.");
            problem.setProperty("timestamp", Instant.now());
            return problem;
        }

        log.error("Unexpected error", ex);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setType(URI.create("https://javaconnect.ke/problems/internal-error"));
        problem.setTitle("Internal Server Error");
        problem.setDetail("An unexpected error occurred. Check server logs for details.");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    private boolean isGroqError(Exception ex) {
        Throwable cause = ex;
        while (cause != null) {
            String msg = cause.getMessage();
            if (msg != null && (msg.contains("api.groq.com")
                    || msg.contains("Connection refused")
                    || msg.contains("Connection reset")
                    || msg.contains("401")
                    || msg.contains("403"))) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
