package ch.multispace.backend.game;

import ch.multispace.backend.model.GameRoom;
import ch.multispace.backend.model.PlayerEntity;
import ch.multispace.backend.repositories.GameRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GameRoomService {

    private final GameRoomRepository gameRoomRepository;

    /**
     * List open rooms (not full)
     */
    public List<GameRoom> listOpenRooms() {
        return gameRoomRepository.findByStatus("WAITING");
    }

    /**
     * Create a new room
     */
    public GameRoom createRoom(PlayerEntity host, String roomName) {
        GameRoom session = new GameRoom();
        session.setHost(host);
        session.setRoomName(roomName);
        session.setStatus("WAITING");
        session.setMaxPlayer(ch.multispace.backend.game.GameRoom.MAX_PLAYERS);
        return gameRoomRepository.save(session);
    }

    /**
     * Find a room by its database ID
     */
    public Optional<GameRoom> findById(UUID id) {
        return gameRoomRepository.findById(id);
    }

    /**
     * Delete a room
     */
    public void deleteRoom(GameRoom session) {
        gameRoomRepository.delete(session);
    }

    /**
     * Join a room
     */
    public Optional<GameRoom> joinRoom(UUID roomId, PlayerEntity player) {
        Optional<GameRoom> opt = gameRoomRepository.findByRoomId(roomId);
        if (opt.isEmpty()) return Optional.empty();

        GameRoom session = opt.get();
        if (session.getPlayerIds().size() >= 2) return Optional.empty(); // max players
        if (!session.getPlayerIds().contains(player.getId())) {
            session.getPlayerIds().add(player.getId());
            // If room now full, mark as STARTED; otherwise keep as WAITING
            if (session.getPlayerIds().size() >= ch.multispace.backend.game.GameRoom.MAX_PLAYERS) {
                session.setStatus("STARTED");
            } else if (session.getStatus() == null || session.getStatus().isBlank()) {
                session.setStatus("WAITING");
            }
            gameRoomRepository.save(session);
        }
        return Optional.of(session);
    }

    /**
     * Find a session by roomId
     */
    public Optional<GameRoom> getRoom(UUID roomId) {
        return gameRoomRepository.findByRoomId(roomId);
    }

    /** Save/update room */
    public GameRoom save(GameRoom room) { return gameRoomRepository.save(room); }
}
