package ch.multispace.backend.ws;

import ch.multispace.backend.game.GameLoop;
import ch.multispace.backend.game.GameRoom;
import ch.multispace.backend.game.GameRoomService;
import ch.multispace.backend.events.RoomsEventBroadcaster;
import ch.multispace.backend.score.ScoreService;
import ch.multispace.backend.repositories.UserRepository;
import ch.multispace.backend.model.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GameWebSocketHandler.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final GameRoomService gameRoomService;
    private final RoomsEventBroadcaster roomsEventBroadcaster;
    private final ScoreService scoreService;
    private final UserRepository userRepository;

    // Maps session → userId
    private final Map<WebSocketSession, String> sessionUserMap = new ConcurrentHashMap<>();
    // Maps userId → roomId
    private final Map<String, UUID> userRoomMap = new ConcurrentHashMap<>();

    public GameWebSocketHandler(GameRoomService gameRoomService, RoomsEventBroadcaster roomsEventBroadcaster, ScoreService scoreService, UserRepository userRepository) {
        this.gameRoomService = gameRoomService;
        this.roomsEventBroadcaster = roomsEventBroadcaster;
        this.scoreService = scoreService;
        this.userRepository = userRepository;
    }

    @Override
    public void afterConnectionEstablished(@Nonnull WebSocketSession session) throws Exception {
        // Retrieve validated JWT attributes set by JwtHandshakeInterceptor
        String userId = getAttribute(session, "userId");
        String email  = getAttribute(session, "email");
        String roomIdAttr = getAttribute(session, "roomId");

        if (userId == null || email == null) {
            closeUnauthorized(session, "Missing JWT attributes");
            return;
        }

        // Join specific room if provided (unify identity with persisted room), otherwise allocate any available
        GameRoom room;
        if (roomIdAttr != null) {
            try {
                UUID targetId = UUID.fromString(roomIdAttr);
                room = GameLoop.getOrCreate(targetId);
            } catch (IllegalArgumentException ex) {
                // fallback if roomId invalid
                room = GameLoop.findAvailableRoom();
            }
        } else {
            room = GameLoop.findAvailableRoom();
        }
        // Resolve the display username from the persisted User (email is used for authentication)
        String displayUsername = userRepository
                .findByEmail(email)
                .map(User::getUsername)
                .orElse(email);

        room.addPlayer(userId, displayUsername, session);

        sessionUserMap.put(session, userId);
        userRoomMap.put(userId, room.getRoomId());

        LOGGER.info("✅ Player connected: {} (userId={}) in room {}", displayUsername, userId, room.getRoomId());
    }

    @Override
    protected void handleTextMessage(@Nonnull WebSocketSession session, @Nonnull TextMessage message) throws Exception {
        String userId = sessionUserMap.get(session);
        if (userId == null) return;

        JsonNode node = mapper.readTree(message.getPayload());
        if (!node.has("type")) return;

        String type = node.get("type").asText();
        if ("input".equals(type)) {
            GameRoom room = getUserGameRoom(userId);
            if (room == null) {
                LOGGER.error("⚠️ Player sent input but no room found: {}", userId);
                return;
            }

            JsonNode payload = node.path("payload");
            boolean left = payload.path("left").asBoolean();
            boolean right = payload.path("right").asBoolean();
            boolean fire = payload.path("fire").asBoolean();

            room.handleInput(userId, left, right, fire);
            return;
        }

        if ("quit".equals(type)) {
            // Player asks to quit the room voluntarily
            GameRoom room = getUserGameRoom(userId);
            if (room != null) {
                boolean removed = room.removePlayer(userId);
                LOGGER.info("Player {} quit room {} (removed={})", userId, room.getRoomId(), removed);
                if (removed) {
                    // Update persistence + broadcast
                    handlePersistenceAfterLeave(room, userId);
                }
            }
            // remove mappings and close session
            sessionUserMap.remove(session);
            userRoomMap.remove(userId);
            try { session.close(CloseStatus.NORMAL.withReason("Player quit")); } catch (Exception _) {
                // ignore
            }
            return;
        }

        // other message types can be handled here
    }

    @Override
    public void afterConnectionClosed(@Nonnull WebSocketSession session, @Nonnull CloseStatus status) {
        String userId = sessionUserMap.remove(session);
        if (userId == null) return;

        GameRoom room = getUserGameRoom(userId);
        if (room != null) {
            String removedUser = room.removeSession(session); // removes player entry if session matched
            LOGGER.info("Session closed. Removed userId={} from room={}", removedUser, room.getRoomId());
            if (removedUser != null) {
                handlePersistenceAfterLeave(room, removedUser);
            }
        }

        userRoomMap.remove(userId);
        LOGGER.info("👋 Player disconnected (userId={})", userId);
    }

    // ------------------
    // Helper functions
    // ------------------

    private GameRoom getUserGameRoom(String userId) {
        UUID roomId = userRoomMap.get(userId);
        return roomId != null ? GameLoop.getRoom(roomId) : null;
    }

    @SuppressWarnings("unchecked")
    private <T> T getAttribute(WebSocketSession session, String key) {
        return (T) session.getAttributes().get(key);
    }

    private void closeUnauthorized(@Nonnull WebSocketSession session,@Nonnull String reason) throws Exception {
        LOGGER.info("❌ Closing connection: {}", reason);
        session.close(CloseStatus.NOT_ACCEPTABLE.withReason(reason));
    }

    private void deletePersistedRoom(UUID roomId) {
        try {
            var opt = gameRoomService.getRoom(roomId);
            if (opt.isPresent()) {
                gameRoomService.deleteRoom(opt.get());
                LOGGER.info("🗑️ Deleted persisted GameRoom with id {} after it became empty", roomId);
                roomsEventBroadcaster.broadcastRoomDeleted(roomId);
            } else {
                LOGGER.info("No persisted GameRoom found for id {} to delete (already gone?)", roomId);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to delete persisted GameRoom {}: {}", roomId, e.getMessage());
        }
    }

    private void handlePersistenceAfterLeave(GameRoom room, String userIdStr) {
        UUID roomId = room.getRoomId();
        // If the in-memory room is now empty, delete the DB room and broadcast
        if (room.isEmpty()) {
            // Persist final scores once per room
            if (!room.isScoresPersisted()) {
                try {
                    scoreService.persistRoomScores(room.getScoresSnapshotUuidMap());
                } catch (Exception ignored) { }
                room.markScoresPersisted();
            }
            deletePersistedRoom(roomId);
            return;
        }

        // Otherwise, remove the player from persisted list and broadcast an update
        try {
            UUID playerUuid = UUID.fromString(userIdStr);
            var opt = gameRoomService.getRoom(roomId);
            if (opt.isPresent()) {
                ch.multispace.backend.model.GameRoom dbRoom = opt.get();
                dbRoom.getPlayerIds().remove(playerUuid);
                // If players remain, ensure status reflects availability
                Integer max = dbRoom.getMaxPlayer() != null ? dbRoom.getMaxPlayer() : 2;
                if (dbRoom.getPlayerIds().size() < max) {
                    dbRoom.setStatus("WAITING");
                }
                var saved = gameRoomService.save(dbRoom);
                roomsEventBroadcaster.broadcastRoomUpdated(saved);
            } else {
                LOGGER.info("No persisted room {} found to update on leave", roomId);
            }
        } catch (IllegalArgumentException ex) {
            LOGGER.warn("Could not parse userId as UUID for persistence update: {}", userIdStr);
        } catch (Exception e) {
            LOGGER.warn("Failed to update persisted room {} after leave: {}", roomId, e.getMessage());
        }
    }
}

