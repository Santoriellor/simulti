package ch.multispace.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "players")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
    }

    /** Link back to User */
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @JsonIgnore
    private User user;

    @Column(name = "total_score", columnDefinition = "BIGINT DEFAULT 0")
    private long totalScore = 0;

    @Column(name = "games_played", columnDefinition = "INT DEFAULT 0")
    private int gamesPlayed = 0;

    @Column(name = "high_score", columnDefinition = "INT DEFAULT 0")
    private int highScore = 0;
}
