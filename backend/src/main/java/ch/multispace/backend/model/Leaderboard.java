package ch.multispace.backend.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "leaderboard")
public class Leaderboard {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @PrePersist
    public void pre() { if (id == null) id = UUID.randomUUID(); if (updatedAt==null) updatedAt = OffsetDateTime.now(); }

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "total_score")
    private Long totalScore = 0L;

    @Column(name = "games_played")
    private Integer gamesPlayed = 0;

    @Column(name = "high_score")
    private Integer highScore = 0;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
    // getters/setters
}
