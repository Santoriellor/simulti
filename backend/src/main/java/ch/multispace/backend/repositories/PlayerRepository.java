package ch.multispace.backend.repositories;

import ch.multispace.backend.model.PlayerEntity;
import ch.multispace.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlayerRepository extends JpaRepository<PlayerEntity, UUID> {
    Optional<PlayerEntity> findByUser(User user);
}
