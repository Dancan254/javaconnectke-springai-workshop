package ke.javaconnect.lab2.exception;

public class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException(String conversationId) {
        super("No active session found for conversationId: '" + conversationId +
              "'. Call POST /conversations first.");
    }
}
