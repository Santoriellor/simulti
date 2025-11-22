package ch.multispace.backend.controllers;

import ch.multispace.backend.dtos.CreateRoomRequestDTO;
import ch.multispace.backend.game.GameRoomService;
import ch.multispace.backend.model.GameRoom;
import ch.multispace.backend.model.PlayerEntity;
import ch.multispace.backend.model.User;
import ch.multispace.backend.repositories.PlayerRepository;
import ch.multispace.backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class GameRoomController {

    private final GameRoomService gameRoomService;
    private final PlayerRepository playerRepository;
    private final UserRepository userRepository;

    /** List open rooms */
    @GetMapping
    public List<GameRoom> listRooms() {
        return gameRoomService.listOpenRooms();
    }

    /** Create a new room */
    @PostMapping
    public ResponseEntity<GameRoom> createRoom(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody CreateRoomRequestDTO request
    ) {

        if (userDetails == null) {
            throw new RuntimeException("userDetails is null! Authentication failed?");
        }

        User user = userRepository
                .findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        PlayerEntity player = playerRepository
                .findByUser(user)
                .orElseGet(() -> {
                    PlayerEntity newPlayer = new PlayerEntity();
                    newPlayer.setUser(user);
                    return playerRepository.save(newPlayer);
                });

        GameRoom session = gameRoomService.createRoom(player, request.name());
        return ResponseEntity.ok(session);
    }

    /** Delete a room */
    @DeleteMapping("/{roomId}/delete")
    public ResponseEntity<Void> deleteRoom(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID roomId
    ) {
        if (userDetails == null) {
            throw new RuntimeException("userDetails is null! Authentication failed?");
        }

        /*User user = userRepository
                .findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));*/

        GameRoom session = gameRoomService.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        // Optional: Check if the user is the owner of the room
        /*if (!session.getOwner().getUser().equals(user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }*/

        gameRoomService.deleteRoom(session);

        return ResponseEntity.noContent().build(); // 204 No Content
    }

    /** Join a room */
    @PostMapping("/{roomId}/join")
    public ResponseEntity<GameRoom> joinRoom(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userRepository
                .findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        PlayerEntity player = playerRepository
                .findByUser(user)
                .orElseGet(() -> {
                    PlayerEntity newPlayer = new PlayerEntity();
                    newPlayer.setUser(user);
                    return playerRepository.save(newPlayer);
                });

        Optional<GameRoom> sessionOpt = gameRoomService.joinRoom(roomId, player);
        return sessionOpt.map(ResponseEntity::ok).orElse(ResponseEntity.badRequest().build());
    }

    /** Get a room state */
    @GetMapping("/{roomId}")
    public ResponseEntity<GameRoom> getRoom(@PathVariable UUID roomId) {
        return gameRoomService.getRoom(roomId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
