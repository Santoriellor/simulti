package ch.multispace.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "game_rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameRoom {

    @Id
    @Column(name = "room_id", updatable = false, nullable = false)
    private UUID roomId;

    @PrePersist
    public void pre() { if (roomId == null) roomId=UUID.randomUUID(); }

    @Column(name = "room_name")
    private String roomName;

    @ManyToOne
    @JoinColumn(name = "host_id")
    @JsonIgnore
    private PlayerEntity host;

    @ElementCollection
    @CollectionTable(
            name = "game_room_players",
            joinColumns = @JoinColumn(name = "game_room_id")
    )
    @Column(name = "player_id", nullable = false)
    private List<UUID> playerIds = new ArrayList<>();

    @Column(name = "status")
    private String status;

    @Column(name = "max_player")
    private Integer maxPlayer;

    @Column(name = "wave")
    private Integer wave = 0;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;
}
