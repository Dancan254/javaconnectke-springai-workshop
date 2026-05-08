package ke.javaconnect.lab1.dto;

import java.time.Instant;

public record ChatResponse(
        String conversationId,
        String reply,
        String model,
        Instant timestamp
) {
    public static ChatResponse of(String conversationId, String reply, String model) {
        return new ChatResponse(conversationId, reply, model, Instant.now());
    }
}
