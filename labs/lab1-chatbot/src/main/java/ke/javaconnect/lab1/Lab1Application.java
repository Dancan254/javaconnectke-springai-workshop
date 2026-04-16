package ke.javaconnect.lab1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for Lab 1 — ChatClient &amp; Chat Memory.
 *
 * <p>This lab walks through three increasingly powerful uses of Spring AI's
 * {@code ChatClient}:
 *
 * <ol>
 *   <li><b>Step 1 — Stateless chat</b> ({@code GET /chat/simple})<br>
 *       A single question gets a single answer. No context is preserved between
 *       requests. Great for one-shot prompts.</li>
 *
 *   <li><b>Step 2 — Stateful chat with memory</b> ({@code GET /chat/memory})<br>
 *       Each request carries a {@code conversationId}. Spring AI stores previous
 *       messages in a {@link org.springframework.ai.chat.memory.ChatMemory} and
 *       injects them back into every outgoing prompt — the model can now remember
 *       what was said earlier in the conversation.</li>
 *
 *   <li><b>Step 3 — Streaming response</b> ({@code GET /chat/stream})<br>
 *       Instead of waiting for the full response, tokens are streamed back as they
 *       are generated. Essential for production chatbots where latency matters.</li>
 * </ol>
 *
 * <h2>How to run</h2>
 * <pre>
 *   # Make sure Ollama is running with llama3.2 pulled
 *   ./mvnw spring-boot:run
 * </pre>
 *
 * <h2>Quick test commands</h2>
 * <pre>
 *   curl "http://localhost:8080/chat/simple?message=What+is+Spring+AI"
 *
 *   curl "http://localhost:8080/chat/memory?message=My+name+is+Dan&amp;conversationId=demo"
 *   curl "http://localhost:8080/chat/memory?message=What+is+my+name&amp;conversationId=demo"
 *
 *   curl -N "http://localhost:8080/chat/stream?message=Tell+me+a+short+story&amp;conversationId=story1"
 * </pre>
 *
 * @see ke.javaconnect.lab1.config.MemoryConfig
 * @see ke.javaconnect.lab1.controller.ChatController
 */
@SpringBootApplication
public class Lab1Application {

    public static void main(String[] args) {
        SpringApplication.run(Lab1Application.class, args);
    }
}
