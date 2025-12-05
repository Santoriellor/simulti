package ch.multispace.backend.repositories;

import ch.multispace.backend.model.PlayerEntity;
import ch.multispace.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ch.multispace.backend.dtos.LeaderboardRowDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayerRepository extends JpaRepository<PlayerEntity, UUID> {
    Optional<PlayerEntity> findByUser(User user);

    @Query("select new ch.multispace.backend.dtos.LeaderboardRowDto(u.username, p.highScore) " +
           "from PlayerEntity p join p.user u order by p.highScore desc")
    List<LeaderboardRowDto> listHighScores();
}
