package ch.multispace.backend.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pins which endpoints are reachable without credentials. If a refactor makes a protected endpoint
 * public, one of these tests fails and the deploy is blocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityBoundaryTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void registerIsPublic() throws Exception {
        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType("application/json")
                                .content(
                                        """
                                {"email":"public@example.com","username":"publicuser","password":"Passw0rd!"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void loginIsPublic() throws Exception {
        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType("application/json")
                                .content(
                                        """
                                {"email":"nobody@example.com","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void currentUserIsProtected() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().is4xxClientError());
    }

    @Test
    void roomListingIsProtected() throws Exception {
        mockMvc.perform(get("/api/rooms")).andExpect(status().is4xxClientError());
    }

    @Test
    void roomCreationIsProtected() throws Exception {
        mockMvc.perform(
                        post("/api/rooms")
                                .contentType("application/json")
                                .content(
                                        """
                                {"name":"nope"}
                                """))
                .andExpect(status().is4xxClientError());
    }

    // /api/rooms/stream is permitAll (an EventSource in the browser cannot send an
    // Authorization header), so an unauthenticated request reaches
    // GameRoomController.streamRooms rather than being stopped by Spring Security.
    // That method throws UnauthorizedException("Missing token for SSE"), which
    // GlobalExceptionHandler now translates into a genuine 401 response; see
    // docs/decisions/0003-deferred-findings.md.
    @Test
    void theSseStreamRejectsAMissingToken() throws Exception {
        mockMvc.perform(get("/api/rooms/stream")).andExpect(status().isUnauthorized());
    }

    // A syntactically valid JWT, correctly shaped and unexpired, but signed with a key
    // the server never issued it with. jwtService.validateTokenForWebSocket rejects it
    // with JwtService.InvalidJwtException, which GlobalExceptionHandler translates to
    // 401 rather than letting it fall through as a 500.
    @Test
    void theSseStreamRejectsAForgedToken() throws Exception {
        Key wrongKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        String forgedToken =
                Jwts.builder()
                        .setSubject("nobody@example.com")
                        .setIssuedAt(new Date())
                        .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                        .signWith(wrongKey, SignatureAlgorithm.HS256)
                        .compact();

        mockMvc.perform(get("/api/rooms/stream").param("token", forgedToken))
                .andExpect(status().isUnauthorized());
    }
}
