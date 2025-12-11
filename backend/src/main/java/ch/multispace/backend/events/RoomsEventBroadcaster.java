package ch.multispace.backend.events;

import ch.multispace.backend.model.GameRoom;
import ch.multispace.backend.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Simple SSE broadcaster for waiting room updates.
 * Clients connect to /api/rooms/stream and receive JSON events.
 */
@Component
public class RoomsEventBroadcaster {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoomsEventBroadcaster.class);

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * Subscribe a new client; caller should have validated JWT before calling this
     */
    public SseEmitter subscribe() {
        // Set a long timeout (30 minutes)
        SseEmitter emitter = new SseEmitter(30L * 60L * 1000L);

        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        // Optional: send a hello event
        trySend(emitter, new RoomsEvent("hello", null));

        return emitter;
    }

    public void broadcastRoomCreated(GameRoom room) {
        broadcast(new RoomsEvent("room.created", room));
    }

    public void broadcastRoomUpdated(GameRoom room) {
        broadcast(new RoomsEvent("room.updated", room));
    }

    public void broadcastRoomDeleted(UUID roomId) {
        broadcast(new RoomsEvent("room.deleted", roomId));
    }

    public void broadcastRoomStarted(GameRoom room) {
        broadcast(new RoomsEvent("room.started", room));
    }

    public void broadcast(RoomsEvent event) {
        for (SseEmitter emitter : emitters) {
            trySend(emitter, event);
        }
    }

    private void trySend(SseEmitter emitter, RoomsEvent event) {
        try {
            SseEmitter.SseEventBuilder builder = SseEmitter.event()
                    .name(event.type())
                    .data(event, MediaType.APPLICATION_JSON);
            emitter.send(builder);
        } catch (IOException e) {
            LOGGER.debug("SSE send failed, removing emitter: {}", e.getMessage());
            emitter.complete();
            emitters.remove(emitter);
        }
    }

    /**
     * Simple event record for SSE payloads
     */
    public record RoomsEvent(String type, Object payload) {}
}
