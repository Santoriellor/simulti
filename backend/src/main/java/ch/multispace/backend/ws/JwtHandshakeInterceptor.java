package ch.multispace.backend.ws;

import ch.multispace.backend.security.JwtService;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.server.HandshakeInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.util.Map;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtHandshakeInterceptor.class);

    private final JwtService jwtService;

    public JwtHandshakeInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean beforeHandshake(@Nonnull ServerHttpRequest request, @Nonnull ServerHttpResponse response,
                                   @Nonnull WebSocketHandler wsHandler, @Nonnull Map<String, Object> attributes) throws Exception {

        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }

        HttpServletRequest req = servletRequest.getServletRequest();

        // Try to get token from query param first
        String token = req.getParameter("token");

        // If not in query, try Authorization header
        if (token == null) {
            String authHeader = req.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }
        }

        if (token == null) {
            LOGGER.info("❌ No token found in handshake request");
            return false;
        }

        try {
            // Validate token
            jwtService.validateTokenForWebSocket(token);

            // Extract userId from 'sub' claim
            String userId = jwtService.extractClaimForWebSocket(token, "userId");
            String email = jwtService.extractUsername(token);

            if (userId == null) {
                LOGGER.info("❌ Token missing 'sub' claim for userId");
                return false;
            }

            // Store in handshake attributes
            attributes.put("userId", userId);
            attributes.put("email", email);
            attributes.put("token", token);

            // Capture optional roomId from query param to unify identity with persisted room
            String roomIdParam = req.getParameter("roomId");
            if (roomIdParam != null && !roomIdParam.isBlank()) {
                try {
                    // validate UUID format; store as string to avoid classloading issues here
                    java.util.UUID.fromString(roomIdParam);
                    attributes.put("roomId", roomIdParam);
                } catch (IllegalArgumentException ex) {
                    LOGGER.warn("Ignoring invalid roomId in WS handshake: {}", roomIdParam);
                }
            }

            LOGGER.info("✅ WebSocket handshake authorized for user {} (userId={})", email, userId);
            return true;

        } catch (JwtService.InvalidJwtException e) {
            LOGGER.error("❌ Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(@Nonnull ServerHttpRequest request, @Nonnull ServerHttpResponse response,
                               @Nonnull WebSocketHandler wsHandler, Exception exception) {
        // No-op
    }
}
