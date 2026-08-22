package ch.multispace.backend.repositories;

import ch.multispace.backend.model.GameRoom;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRoomRepository extends JpaRepository<GameRoom, UUID> {
    Optional<GameRoom> findByRoomId(UUID roomId);

    List<GameRoom> findByStatus(String status);
}
