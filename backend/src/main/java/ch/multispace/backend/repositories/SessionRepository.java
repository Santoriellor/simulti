package ch.multispace.backend.repositories;

import ch.multispace.backend.model.SessionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<SessionEntity, UUID> {
    Optional<SessionEntity> findByToken(String token);

    void deleteByToken(String token);
}
