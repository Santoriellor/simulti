package ch.multispace.backend.config;

import ch.multispace.backend.ws.GameWebSocketHandler;
import ch.multispace.backend.ws.JwtHandshakeInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Value("${app.frontend-url}") // read from application.yml
    private String frontendUrl;

    private final GameWebSocketHandler handler;
    private final JwtHandshakeInterceptor interceptor;

    public WebSocketConfig(GameWebSocketHandler handler,
                           JwtHandshakeInterceptor interceptor) {
        this.handler = handler;
        this.interceptor = interceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/space-invaders")
                .addInterceptors(interceptor)
                .setAllowedOrigins(frontendUrl);
    }
}
