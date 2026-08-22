package ch.multispace.backend.dtos;

import ch.multispace.backend.model.GameRoom;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The public shape of a room. Field names match what the entity serialized before, so the frontend
 * contract is unchanged. hostUsername is added because host is @JsonIgnore'd on the entity and the
 * waiting room had no way to name the host.
 */
public record GameRoomDto(
        UUID roomId,
        String roomName,
        String status,
        Integer maxPlayer,
        Integer wave,
        List<UUID> playerIds,
        String hostUsername,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt) {

    public static GameRoomDto from(GameRoom room) {
        String hostUsername =
                room.getHost() != null && room.getHost().getUser() != null
                        ? room.getHost().getUser().getUsername()
                        : null;
        return new GameRoomDto(
                room.getRoomId(),
                room.getRoomName(),
                room.getStatus(),
                room.getMaxPlayer(),
                room.getWave(),
                room.getPlayerIds(),
                hostUsername,
                room.getStartedAt(),
                room.getEndedAt());
    }
}
