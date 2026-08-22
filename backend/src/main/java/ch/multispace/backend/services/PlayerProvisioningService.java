package ch.multispace.backend.services;

import ch.multispace.backend.exceptions.NotFoundException;
import ch.multispace.backend.exceptions.UnauthorizedException;
import ch.multispace.backend.model.PlayerEntity;
import ch.multispace.backend.model.User;
import ch.multispace.backend.repositories.PlayerRepository;
import ch.multispace.backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

/**
 * Resolves the game profile behind an authenticated principal, creating it on
 * first use. AuthService.register already creates a PlayerEntity, so the
 * create branch only fires for accounts that predate that behaviour.
 */
@Service
@RequiredArgsConstructor
public class PlayerProvisioningService {

    private final UserRepository userRepository;
    private final PlayerRepository playerRepository;

    public PlayerEntity forPrincipal(UserDetails userDetails) {
        if (userDetails == null) {
            throw new UnauthorizedException("Authentication required");
        }
        // UserDetails.getUsername() carries the email; see AuthService.
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new NotFoundException("User not found"));

        return playerRepository.findByUser(user)
                .orElseGet(() -> {
                    PlayerEntity created = new PlayerEntity();
                    created.setUser(user);
                    return playerRepository.save(created);
                });
    }
}
