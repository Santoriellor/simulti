package ch.multispace.backend.game;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GameLoop {
    private static final Set<GameRoom> rooms = ConcurrentHashMap.newKeySet();

    public static void registerRoom(GameRoom r) { rooms.add(r); }

    @Scheduled(fixedRate = 16)
    public void tick() {
        double dt = 0.016;
        for (GameRoom r : rooms) r.update(dt);
    }

    public static GameRoom findAvailableRoom() {
        return rooms.stream().filter(r -> !r.isFull()).findFirst().orElseGet(() -> {
            GameRoom newRoom = new GameRoom();
            rooms.add(newRoom);
            return newRoom;
        });
    }
}
