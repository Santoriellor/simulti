package ch.multispace.backend.controllers;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class GameRoomControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private static int counter = 0;

    private String registerAndGetToken() throws Exception {
        String unique = "room" + (++counter);
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

    private String createRoom(String token, String name) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post("/api/rooms")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                {"name":"%s"}
                                """
                                                        .formatted(name)))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("roomId").asText();
    }

    @Test
    void listingRoomsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/rooms")).andExpect(status().is4xxClientError());
    }

    @Test
    void createdRoomIsReturnedWithItsIdentityAndStatus() throws Exception {
        String token = registerAndGetToken();
        mockMvc.perform(
                        post("/api/rooms")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"name":"Alpha"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").exists())
                .andExpect(jsonPath("$.roomName").value("Alpha"))
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    void aCreatedRoomAppearsInTheOpenRoomList() throws Exception {
        String token = registerAndGetToken();
        String roomId = createRoom(token, "Bravo");

        MvcResult result =
                mockMvc.perform(get("/api/rooms").header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains(roomId), "the open room list must contain the room just created");
    }

    @Test
    void fetchingAnUnknownRoomReturns404() throws Exception {
        String token = registerAndGetToken();
        mockMvc.perform(
                        get("/api/rooms/00000000-0000-0000-0000-000000000000")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingAnUnknownRoomReturns404() throws Exception {
        String token = registerAndGetToken();
        mockMvc.perform(
                        delete("/api/rooms/00000000-0000-0000-0000-000000000000/delete")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void aSecondPlayerCanJoinAnOpenRoom() throws Exception {
        String hostToken = registerAndGetToken();
        String roomId = createRoom(hostToken, "Charlie");

        String joinerToken = registerAndGetToken();
        mockMvc.perform(
                        post("/api/rooms/" + roomId + "/join")
                                .header("Authorization", "Bearer " + joinerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId").value(roomId));
    }

    @Test
    void theHostCanDeleteItsOwnRoom() throws Exception {
        String token = registerAndGetToken();
        String roomId = createRoom(token, "Delta");

        mockMvc.perform(
                        delete("/api/rooms/" + roomId + "/delete")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // and it is gone afterwards
        mockMvc.perform(get("/api/rooms/" + roomId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void aStrangerCannotDeleteSomeoneElsesRoom() throws Exception {
        String hostToken = registerAndGetToken();
        String roomId = createRoom(hostToken, "Echo");

        String strangerToken = registerAndGetToken();
        mockMvc.perform(
                        delete("/api/rooms/" + roomId + "/delete")
                                .header("Authorization", "Bearer " + strangerToken))
                .andExpect(status().isForbidden());

        // and the room must still be there
        mockMvc.perform(get("/api/rooms/" + roomId).header("Authorization", "Bearer " + hostToken))
                .andExpect(status().isOk());
    }
}
