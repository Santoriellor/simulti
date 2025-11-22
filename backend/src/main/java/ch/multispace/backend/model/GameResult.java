package ch.multispace.backend.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "game_results")
public class GameResult {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @PrePersist
    public void pre() { if (id == null) id=UUID.randomUUID(); if (createdAt==null) createdAt = OffsetDateTime.now(); }

    @ManyToOne
    @JoinColumn(name = "session_id")
    private GameRoom session;

    @ManyToOne
    @JoinColumn(name = "player_id")
    private User player;

    @Column(name = "final_score")
    private Integer finalScore;

    @Column(name = "wave_reached")
    private Integer waveReached;

    @Column(name = "enemies_killed")
    private Integer enemiesKilled;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
    // getters/setters
}
