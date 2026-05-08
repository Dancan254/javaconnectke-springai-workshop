package ke.javaconnect.lab2.session;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionRegistry {

    private final ConcurrentHashMap<String, Instant> sessions = new ConcurrentHashMap<>();

    public String create() {
        String id = UUID.randomUUID().toString();
        sessions.put(id, Instant.now());
        return id;
    }

    public boolean exists(String id) {
        return sessions.containsKey(id);
    }

    public Instant createdAt(String id) {
        return sessions.get(id);
    }

    public void remove(String id) {
        sessions.remove(id);
    }

    public void removeAll() {
        sessions.clear();
    }

    public List<String> ids() {
        return List.copyOf(sessions.keySet());
    }
}
