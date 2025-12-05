package ch.multispace.backend.game;

import ch.multispace.backend.ws.GameWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GameLoop: ticks rooms and optionally prunes totally unused rooms to avoid memory leak.
 */
@Component
public class GameLoop {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameLoop.class);

    private static final Set<GameRoom> rooms = ConcurrentHashMap.newKeySet();

    // optionally remove rooms that have been empty for this many seconds
    private static final long CLEANUP_THRESHOLD_SECONDS = 60L * 30; // 30 minutes

    public static void registerRoom(GameRoom r) { rooms.add(r); }

    public static void unregisterRoom(GameRoom r) { rooms.remove(r); }

    @Scheduled(fixedRate = 16)
    public void tick() {
        double dt = 0.016;

        // 1. Remove fully closed rooms
        if (rooms.removeIf(GameRoom::isClosed)) {
            LOGGER.info("Removed {} closed rooms", rooms.size());
        }

        // 2. Optional cleanup: remove rooms idle for too long
        if (rooms.removeIf(room ->
                room.isEmpty() &&
                        Duration.between(room.getLastActiveAt(), Instant.now()).getSeconds() > CLEANUP_THRESHOLD_SECONDS
        )) {
            LOGGER.info("Removed {} idle rooms", rooms.size());
        }

        // 3. Update active rooms
        for (GameRoom r : rooms) {
            if (!r.isClosed()) {
                r.update(dt);
                LOGGER.debug("Updated room {}", r.getRoomId());
            }
        }
    }

    /**
     * Finds a room with free space or creates a new one.
     */
    public static GameRoom findAvailableRoom() {
        return rooms.stream().filter(r -> !r.isFull()).findFirst().orElseGet(() -> {
            GameRoom newRoom = new GameRoom();
            return newRoom;
        });
    }

    /**
     * Find a room by ID
     */
    public static GameRoom getRoom(UUID roomId) { return rooms.stream().filter(r -> r.getRoomId().equals(roomId)).findFirst().orElse(null); }

    /**
     * Get an existing room by id or create a new one with that id.
     */
    public static GameRoom getOrCreate(UUID roomId) {
        GameRoom existing = getRoom(roomId);
        if (existing != null) return existing;
        return new GameRoom(roomId);
    }
}
