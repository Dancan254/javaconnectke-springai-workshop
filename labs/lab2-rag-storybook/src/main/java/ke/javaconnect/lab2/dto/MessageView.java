package ke.javaconnect.lab2.dto;

import org.springframework.ai.chat.messages.Message;

/**
 * Read-only projection of a stored chat message.
 *
 * <p>Used by the conversation history endpoint to expose what the AI
 * model actually sees on each turn without leaking internal Spring AI types.
 *
 * @param role    {@code "user"} or {@code "assistant"}
 * @param content the message text
 */
public record MessageView(String role, String content) {

    /**
     * Creates a view from a Spring AI {@link Message}.
     * The message type maps to a human-readable role string.
     */
    public static MessageView from(Message message) {
        String role = switch (message.getMessageType()) {
            case USER      -> "user";
            case ASSISTANT -> "assistant";
            case SYSTEM    -> "system";
            default        -> message.getMessageType().getValue();
        };
        return new MessageView(role, message.getText());
    }
}
