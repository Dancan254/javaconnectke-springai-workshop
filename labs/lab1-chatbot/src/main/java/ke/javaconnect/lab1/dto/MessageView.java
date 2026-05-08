package ke.javaconnect.lab1.dto;

import org.springframework.ai.chat.messages.Message;

public record MessageView(String role, String content) {

    public static MessageView from(Message message) {
        return new MessageView(message.getMessageType().getValue(), message.getText());
    }
}
