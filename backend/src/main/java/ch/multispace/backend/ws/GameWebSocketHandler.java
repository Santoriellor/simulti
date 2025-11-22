package ch.multispace.backend.ws;

import ch.multispace.backend.game.GameLoop;
import ch.multispace.backend.game.GameRoom;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<WebSocketSession, String> sessionUserMap = new ConcurrentHashMap<>();
    private final Map<String, GameRoom> userRoomMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Retrieve validated JWT attributes set by JwtHandshakeInterceptor
        String userId = (String) session.getAttributes().get("userId");
        String email = (String) session.getAttributes().get("email");

        if (userId == null || email == null) {
            System.out.println("❌ Missing JWT attributes — closing WebSocket session");
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Unauthorized"));
            return;
        }

        // Find or create a game room for the player
        GameRoom room = GameLoop.findAvailableRoom();
        room.addPlayer(userId, email, session);

        sessionUserMap.put(session, userId);
        userRoomMap.put(userId, room.getRoom());

        System.out.println("✅ Player connected: " + email + " (userId=" + userId + ")");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node = mapper.readTree(message.getPayload());
        if (!node.has("type")) return;
        String type = node.get("type").asText();
        if ("input".equals(type)) {
            String userId = sessionUserMap.get(session);
            if (userId == null) return;
            GameRoom room = GameLoop.findAvailableRoom();
            boolean left = node.path("payload").path("left").asBoolean();
            boolean right = node.path("payload").path("right").asBoolean();
            boolean fire = node.path("payload").path("fire").asBoolean();
            room.handleInput(userId, left, right, fire);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = sessionUserMap.remove(session);
        if (userId != null) {
            GameRoom room = GameLoop.findAvailableRoom();
            if (room != null) room.removeSession(session);
            userRoomMap.remove(userId);
            System.out.println("👋 Player disconnected (userId=" + userId + ")");
        }
    }
}
