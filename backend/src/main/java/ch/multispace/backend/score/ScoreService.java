package ch.multispace.backend.score;

import ch.multispace.backend.repositories.PlayerRepository;
import ch.multispace.backend.repositories.UserRepository;
import ch.multispace.backend.model.PlayerEntity;
import ch.multispace.backend.model.User;
import ch.multispace.backend.dtos.LeaderboardRowDto;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScoreService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScoreService.class);

    private final PlayerRepository playerRepository;
    private final UserRepository userRepository;

    /**
     * List high scores (descending)
     */
    public List<LeaderboardRowDto> listHighScores() {
        return playerRepository.listHighScores();
    }

    /**
     * Persist final scores for a finished/closed game room.
     * Updates each player's totalScore, gamesPlayed, and highScore.
     * The map key must be the User.id (UUID) and value is the final score (long).
     */
    public void persistRoomScores(Map<UUID, Long> userScores) {
        if (userScores == null || userScores.isEmpty()) return;

        userScores.forEach((userId, score) -> {
            try {
                User user = userRepository.findById(userId).orElse(null);
                if (user == null) {
                    LOGGER.warn("Cannot persist score for unknown user {}", userId);
                    return;
                }
                PlayerEntity player = playerRepository.findByUser(user).orElse(null);
                if (player == null) {
                    // create a new player profile if somehow missing
                    player = PlayerEntity.builder()
                            .user(user)
                            .build();
                }

                long s = score != null ? score : 0L;
                player.setTotalScore(player.getTotalScore() + s);
                player.setGamesPlayed(player.getGamesPlayed() + 1);
                int high = player.getHighScore();
                if (s > high) {
                    player.setHighScore((int) Math.min(Integer.MAX_VALUE, s));
                }
                playerRepository.save(player);
            } catch (Exception e) {
                LOGGER.warn("Failed to persist room score for user {}: {}", userId, e.getMessage());
            }
        });
    }
}
