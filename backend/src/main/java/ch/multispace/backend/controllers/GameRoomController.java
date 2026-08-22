package ch.multispace.backend.controllers;

import ch.multispace.backend.dtos.CreateRoomRequestDTO;
import ch.multispace.backend.dtos.GameRoomDto;
import ch.multispace.backend.exceptions.ForbiddenException;
import ch.multispace.backend.exceptions.NotFoundException;
import ch.multispace.backend.exceptions.UnauthorizedException;
import ch.multispace.backend.game.GameRoomService;
import ch.multispace.backend.model.GameRoom;
import ch.multispace.backend.model.PlayerEntity;
import ch.multispace.backend.model.User;
import ch.multispace.backend.services.PlayerProvisioningService;
import ch.multispace.backend.events.RoomsEventBroadcaster;
import ch.multispace.backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class GameRoomController {

    private final GameRoomService gameRoomService;
    private final PlayerProvisioningService playerProvisioningService;
    private final RoomsEventBroadcaster roomsEventBroadcaster;
    private final JwtService jwtService;

    /** List open rooms */
    @GetMapping
    public List<GameRoomDto> listRooms() {
        return gameRoomService.listOpenRooms().stream().map(GameRoomDto::from).toList();
    }

    /** SSE stream for live waiting room updates */
    @GetMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter streamRooms(@RequestParam(name = "token", required = false) String token,
                                  @RequestHeader(name = "Authorization", required = false) String authHeader) {
        // Accept token via query param or Authorization header (Bearer ...)
        if (token == null && authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }
        if (token == null) {
            throw new UnauthorizedException("Missing token for SSE");
        }
        // Validate like WebSocket
        jwtService.validateTokenForWebSocket(token);
        return roomsEventBroadcaster.subscribe();
    }

    /** Create a new room */
    @PostMapping
    public ResponseEntity<GameRoomDto> createRoom(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody CreateRoomRequestDTO request
    ) {
        PlayerEntity player = playerProvisioningService.forPrincipal(userDetails);
        GameRoom room = gameRoomService.createRoom(player, request.name());
        roomsEventBroadcaster.broadcastRoomCreated(room);
        return ResponseEntity.ok(GameRoomDto.from(room));
    }

    /** Delete a room */
    @DeleteMapping("/{roomId}/delete")
    public ResponseEntity<Void> deleteRoom(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID roomId
    ) {
        if (userDetails == null) {
            throw new UnauthorizedException("Authentication required");
        }

        User user = playerProvisioningService.forPrincipal(userDetails).getUser();

        GameRoom room = gameRoomService.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found"));

        if (room.getHost() == null || !room.getHost().getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Only the host may delete this room");
        }

        gameRoomService.deleteRoom(room);
        roomsEventBroadcaster.broadcastRoomDeleted(room.getRoomId());

        return ResponseEntity.noContent().build();
    }

    /** Join a room */
    @PostMapping("/{roomId}/join")
    public ResponseEntity<GameRoomDto> joinRoom(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        PlayerEntity player = playerProvisioningService.forPrincipal(userDetails);
        Optional<GameRoom> roomOpt = gameRoomService.joinRoom(roomId, player);
        roomOpt.ifPresent(room -> {
            roomsEventBroadcaster.broadcastRoomUpdated(room);
            if ("STARTED".equalsIgnoreCase(room.getStatus())) {
                roomsEventBroadcaster.broadcastRoomStarted(room);
            }
        });
        return roomOpt.map(room -> ResponseEntity.ok(GameRoomDto.from(room)))
                .orElseGet(() -> ResponseEntity.badRequest().build());
    }

    /** Get a room state */
    @GetMapping("/{roomId}")
    public ResponseEntity<GameRoomDto> getRoom(@PathVariable UUID roomId) {
        return gameRoomService.getRoom(roomId)
                .map(room -> ResponseEntity.ok(GameRoomDto.from(room)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
