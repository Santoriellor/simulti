package ch.multispace.backend.ws;

import ch.multispace.backend.repositories.SessionRepository;
import ch.multispace.backend.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final JwtService jwtService;
    private final SessionRepository sessionRepository;

    @Autowired
    public JwtHandshakeInterceptor(JwtService jwtService, SessionRepository sessionRepository) {
        this.jwtService = jwtService;
        this.sessionRepository = sessionRepository;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {

        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest req = servletRequest.getServletRequest();

            // Try to get token from query param first
            String token = req.getParameter("token");

            // If not in query, try from Authorization header
            if (token == null) {
                String auth = req.getHeader("Authorization");
                if (auth != null && auth.startsWith("Bearer ")) token = auth.substring(7);
            }

            if (token == null) {
                System.out.println("❌ No token found in handshake request");
                return false;
            }

            // optional: check session exists in DB
            var sessionOpt = sessionRepository.findByToken(token);
            if (sessionOpt.isEmpty()) {
                System.out.println("❌ No session found for token");
                return false;
            }

            // Validate token structure and expiry
            try {
                jwtService.validateToken(token); // will throw InvalidJwtException if invalid
                String email = jwtService.extractUsername(token);
                String userId = jwtService.extractClaim(token, c -> c.get("userId", String.class));

                attributes.put("email", email);
                attributes.put("token", token);
                attributes.put("userId", userId);

                System.out.println("✅ WebSocket handshake authorized for user " + email);
                return true;

            } catch (JwtService.InvalidJwtException e) {
                System.out.println("❌ Token validation failed: " + e.getMessage());
                return false;
            }
        }
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) { }
}
