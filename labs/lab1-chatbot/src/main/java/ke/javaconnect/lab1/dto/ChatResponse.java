package ke.javaconnect.lab1.dto;

import java.time.Instant;

/**
 * Structured response returned by all chat endpoints.
 *
 * <p>Wrapping the raw model output in a DTO is a production habit worth
 * establishing early.  It gives clients stable, versioned fields to rely on
 * rather than a plain string, and makes it easy to add metadata later without
 * breaking existing consumers.
 *
 * <h2>Fields</h2>
 * <ul>
 *   <li>{@code conversationId} — echoed back so the client does not need to
 *       track it separately.  Useful when requests are made concurrently.</li>
 *   <li>{@code reply} — the model's text response.</li>
 *   <li>{@code model} — which Ollama model produced the reply.  Vital in
 *       production when you A/B test models or need to reproduce a response.</li>
 *   <li>{@code timestamp} — server-side generation time in UTC ISO-8601.
 *       Useful for audit trails, latency measurement, and UI display.</li>
 * </ul>
 *
 * <h2>Example JSON</h2>
 * <pre>
 * {
 *   "conversationId": "user-42",
 *   "reply": "Spring AI is a framework that...",
 *   "model": "llama3.2",
 *   "timestamp": "2025-06-10T09:15:30.123Z"
 * }
 * </pre>
 *
 * @param conversationId the session identifier echoed from the request
 * @param reply          the model's text output
 * @param model          the Ollama model tag that generated the reply
 * @param timestamp      UTC instant at which the response was assembled
 */
public record ChatResponse(
        String conversationId,
        String reply,
        String model,
        Instant timestamp
) {

    /**
     * Convenience factory — creates a response stamped with the current UTC time.
     *
     * @param conversationId the session identifier
     * @param reply          the model's text output
     * @param model          the model tag (e.g. {@code "llama3.2"})
     * @return a fully populated {@link ChatResponse}
     */
    public static ChatResponse of(String conversationId, String reply, String model) {
        return new ChatResponse(conversationId, reply, model, Instant.now());
    }
}
