package ke.javaconnect.lab2.dto;

import java.time.Instant;

/**
 * Returned by POST /conversations when a new chat session is created.
 *
 * <p>The client must supply {@code conversationId} in every subsequent
 * POST /story/ask request to participate in this session.
 *
 * <h2>Example JSON</h2>
 * <pre>
 * {
 *   "conversationId": "550e8400-e29b-41d4-a716-446655440000",
 *   "createdAt": "2026-05-08T09:00:00.000Z"
 * }
 * </pre>
 */
public record SessionCreatedResponse(String conversationId, Instant createdAt) {}
