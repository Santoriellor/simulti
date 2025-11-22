package ch.multispace.backend.repositories;

import ch.multispace.backend.model.SessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<SessionEntity, UUID> {
    Optional<SessionEntity> findByToken(String token);
    void deleteByToken(String token);
}
