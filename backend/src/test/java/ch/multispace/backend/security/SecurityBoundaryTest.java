package ch.multispace.backend.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins which endpoints are reachable without credentials. If a refactor makes a
 * protected endpoint public, one of these tests fails and the deploy is blocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityBoundaryTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void registerIsPublic() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"email":"public@example.com","username":"publicuser","password":"Passw0rd!"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void loginIsPublic() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
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
        mockMvc.perform(post("/api/rooms")
                        .contentType("application/json")
                        .content("""
                                {"name":"nope"}
                                """))
                .andExpect(status().is4xxClientError());
    }

    // /api/rooms/stream is permitAll (an EventSource in the browser cannot send an
    // Authorization header), so an unauthenticated request reaches
    // GameRoomController.streamRooms rather than being stopped by Spring Security.
    // That method throws a bare RuntimeException("Missing token for SSE"), and with
    // no @ControllerAdvice in this codebase (verified), there is nothing to translate
    // it into an HTTP response: under MockMvc it surfaces as an uncaught
    // ServletException out of perform() itself, not a captured 5xx status. This
    // assertion documents that as-is behavior; see
    // docs/decisions/0003-deferred-findings.md.
    @Test
    void theSseStreamRejectsAMissingToken() {
        ServletException ex = assertThrows(ServletException.class,
                () -> mockMvc.perform(get("/api/rooms/stream")));
        assertThat(ex.getCause())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Missing token for SSE");
    }
}
