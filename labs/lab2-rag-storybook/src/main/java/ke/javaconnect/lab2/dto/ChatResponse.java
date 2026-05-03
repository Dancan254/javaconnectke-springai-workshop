package ke.javaconnect.lab2.dto;

import java.time.Instant;

/**
 * Structured response returned by the story chat endpoint.
 *
 * <p>Wraps the model's reply with metadata that the caller needs to
 * display, audit, or route follow-up requests correctly.
 *
 * <h2>Fields</h2>
 * <ul>
 *   <li>{@code conversationId} — echoed from the request so the client can
 *       track which session produced this reply.</li>
 *   <li>{@code reply} — the model's text output, grounded in retrieved
 *       story context by the {@code QuestionAnswerAdvisor}.</li>
 *   <li>{@code model} — the Azure OpenAI deployment that generated the reply.</li>
 *   <li>{@code timestamp} — server-side UTC instant; useful for latency
 *       measurement and audit logs.</li>
 * </ul>
 *
 * <h2>Example JSON</h2>
 * <pre>
 * {
 *   "conversationId": "session-42",
 *   "reply": "Ayana is the Keeper of Listening, a seventeen-year-old girl who arrived...",
 *   "model": "gpt-4o",
 *   "timestamp": "2025-06-10T09:15:30.123Z"
 * }
 * </pre>
 */
public record ChatResponse(
        String conversationId,
        String reply,
        String model,
        Instant timestamp
) {

    /**
     * Convenience factory — stamps the response with the current UTC time.
     */
    public static ChatResponse of(String conversationId, String reply, String model) {
        return new ChatResponse(conversationId, reply, model, Instant.now());
    }
}
