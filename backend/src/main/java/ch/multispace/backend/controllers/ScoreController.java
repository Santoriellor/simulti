package ch.multispace.backend.controllers;

import ch.multispace.backend.score.ScoreService;
import ch.multispace.backend.dtos.LeaderboardRowDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/leaderboard")
@RequiredArgsConstructor
public class ScoreController {

    private final ScoreService scoreService;

    /** List leaderboard entries (username + high score) */
    @GetMapping
    public List<LeaderboardRowDto> listLeaderboard() {
        return scoreService.listHighScores();
    }

}
