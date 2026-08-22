package ch.multispace.backend.exceptions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Pins the status codes malformed/unroutable requests get, independent of any application-level
 * exception. GlobalExceptionHandler must not pre-empt Spring's own translation of these into their
 * correct 4xx statuses.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ErrorContractTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private static int counter = 0;

    private String registerAndGetToken() throws Exception {
        String unique = "errcontract" + (++counter);
        MvcResult result =
                mockMvc.perform(
                                post("/api/auth/register")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                {"email":"%s@example.com","username":"%s","password":"Passw0rd!"}
                                """
                                                        .formatted(unique, unique)))
                        .andExpect(status().isOk())
                        .andReturn();
        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("token")
                .asText();
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{not-json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emptyBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongVerbReturns405() throws Exception {
        mockMvc.perform(put("/api/auth/login")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void nonUuidRoomIdReturns400() throws Exception {
        String token = registerAndGetToken();
        mockMvc.perform(get("/api/rooms/not-a-uuid").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownRouteReturns404() throws Exception {
        String token = registerAndGetToken();
        mockMvc.perform(get("/api/does-not-exist").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void unsupportedMediaTypeReturns415() throws Exception {
        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.TEXT_PLAIN)
                                .content("plain text body"))
                .andExpect(status().isUnsupportedMediaType());
    }
}
