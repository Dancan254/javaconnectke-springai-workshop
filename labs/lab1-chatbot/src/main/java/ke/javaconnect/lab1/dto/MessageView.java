package ke.javaconnect.lab1.dto;

import org.springframework.ai.chat.messages.Message;

/**
 * Read-only projection of a stored {@link Message} for the history endpoint.
 *
 * <p>Spring AI's internal {@link Message} type is not designed for direct
 * JSON serialisation — it carries model-specific metadata and may change
 * across Spring AI versions.  This lightweight record decouples the API
 * contract from the internal representation.
 *
 * <h2>Example JSON</h2>
 * <pre>
 * { "role": "user",      "content": "My name is Dan"   }
 * { "role": "assistant", "content": "Nice to meet you, Dan!" }
 * </pre>
 *
 * @param role    the message role: {@code "user"}, {@code "assistant"},
 *                {@code "system"}, or {@code "tool"}
 * @param content the text content of the message
 */
public record MessageView(String role, String content) {

    /**
     * Maps a Spring AI {@link Message} to a {@link MessageView}.
     *
     * <p>{@code MessageType.getValue()} returns the lowercase role string
     * ({@code "user"}, {@code "assistant"}, etc.) that is idiomatic in the
     * OpenAI / Ollama message format.
     *
     * @param message the internal Spring AI message object
     * @return a serialisation-safe projection
     */
    public static MessageView from(Message message) {
        return new MessageView(
                message.getMessageType().getValue(),
                message.getText()
        );
    }
}
