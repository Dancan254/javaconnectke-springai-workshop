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

/**
 * Centralised exception handler for all REST controllers in Lab 1.
 *
 * <p>Returns {@link ProblemDetail} (RFC 9457 / RFC 7807) responses so that
 * every error — validation failure, model unavailable, unexpected crash —
 * has a consistent, machine-readable shape.  Clients never receive a raw
 * stack trace.
 *
 * <h2>Why ProblemDetail?</h2>
 * <ul>
 *   <li>It is the Spring Boot 3+ standard error format.</li>
 *   <li>Fields are standardised ({@code type}, {@code title}, {@code status},
 *       {@code detail}, {@code instance}) so API consumers know what to expect.</li>
 *   <li>Custom properties can be added per-error (e.g. {@code conversationId}).</li>
 * </ul>
 *
 * <h2>Example error response</h2>
 * <pre>
 * HTTP/1.1 400 Bad Request
 * Content-Type: application/problem+json
 *
 * {
 *   "type":      "https://javaconnect.ke/problems/validation-error",
 *   "title":     "Validation Error",
 *   "status":    400,
 *   "detail":    "message: must not be blank",
 *   "timestamp": "2025-06-10T09:15:30.123Z"
 * }
 * </pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Handles {@code @NotBlank}, {@code @Size}, and other Bean Validation
     * failures on {@code @RequestParam} fields.
     *
     * <p>These are <em>client errors</em> — no stack trace is logged, only a
     * concise warning.
     *
     * @param ex the constraint violation thrown by the validation framework
     * @return a 400 ProblemDetail listing which constraint was violated
     */
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

    // ── Model / Ollama connectivity ───────────────────────────────────────────

    /**
     * Catches any exception whose message indicates that Ollama is unreachable.
     *
     * <p>In production you would catch the specific Spring AI exception type.
     * Here we use a broad pattern match so the demo still works if Spring AI's
     * internal exception hierarchy changes between milestone releases.
     *
     * <p><b>Production tip:</b> pair this with Spring Boot Actuator's health
     * indicator.  Configure a liveness/readiness probe that checks the Ollama
     * base URL so Kubernetes stops routing traffic when the model is down.
     *
     * @param ex the connectivity exception
     * @return a 503 ProblemDetail with a human-readable cause
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneral(Exception ex) {
        // Distinguish "model unavailable" from genuinely unexpected errors
        // so the log stays actionable.
        if (isOllamaConnectivityError(ex)) {
            log.error("Ollama is unreachable: {}", ex.getMessage());

            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
            problem.setType(URI.create("https://javaconnect.ke/problems/model-unavailable"));
            problem.setTitle("Model Unavailable");
            problem.setDetail(
                    "Could not reach Ollama at the configured base-url. " +
                    "Make sure 'docker compose up -d' has been run and the model is pulled.");
            problem.setProperty("timestamp", Instant.now());
            return problem;
        }

        // Unexpected server error — log the full stack trace for diagnostics.
        log.error("Unexpected error", ex);

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setType(URI.create("https://javaconnect.ke/problems/internal-error"));
        problem.setTitle("Internal Server Error");
        problem.setDetail("An unexpected error occurred. Check server logs for details.");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Heuristic to detect Ollama connectivity failures by inspecting the
     * exception message chain.
     *
     * <p>Spring AI wraps low-level I/O failures in various exception types
     * across versions.  Checking the message text is the most resilient
     * approach for a workshop that may be run on different milestone releases.
     */
    private boolean isOllamaConnectivityError(Exception ex) {
        Throwable cause = ex;
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null && (
                    message.contains("Connection refused") ||
                    message.contains("Connection reset") ||
                    message.contains("11434") ||
                    message.contains("ollama"))) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
