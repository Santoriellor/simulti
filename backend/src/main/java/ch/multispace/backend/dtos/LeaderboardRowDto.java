package ch.multispace.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LeaderboardRowDto {
    private String player;
    private int score;
}
