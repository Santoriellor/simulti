package ch.multispace.backend.services;

import ch.multispace.backend.model.PlayerEntity;
import ch.multispace.backend.model.User;
import ch.multispace.backend.repositories.PlayerRepository;
import ch.multispace.backend.repositories.UserRepository;
import ch.multispace.backend.security.JwtService;
import ch.multispace.backend.ws.GameWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PlayerRepository playerRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authManager;

    // --- Custom exceptions ---
    public static class DuplicateEmailException extends RuntimeException {
        public DuplicateEmailException(String message) { super(message); }
    }

    public static class DuplicateUsernameException extends RuntimeException {
        public DuplicateUsernameException(String message) { super(message); }
    }

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException(String message) { super(message); }
    }

    // --- Registration ---
    public String register(String email, String username, String password) {
        if (userRepository.findByEmail(email).isPresent())
            throw new DuplicateEmailException("Email already registered");

        if (userRepository.findByUsername(username).isPresent())
            throw new DuplicateUsernameException("Username already taken");

        User user = User.builder()
                .email(email)
                .username(username)
                .password(passwordEncoder.encode(password))
                .build();

        userRepository.save(user);

        // Create linked player profile
        PlayerEntity player = PlayerEntity.builder()
                .user(user)
                .build();

        playerRepository.save(player);

        return jwtService.generateToken(userDetailsFromUser(user));
    }

    // --- Login ---
    public String login(String email, String password) {
        try {
            authManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
        } catch (Exception e) {
            LOGGER.error("Authentication failed: {}", e.getMessage());
            throw new InvalidCredentialsException("Invalid email or password");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        return jwtService.generateTokenForWebSocket(userDetailsFromUser(user), user.getId());
    }

    // --- Helper to create Spring Security UserDetails --- NEEDED?
    private org.springframework.security.core.userdetails.User userDetailsFromUser(User user) {
        return new org.springframework.security.core.userdetails.User(
                user.getEmail(), user.getPassword(), java.util.Collections.emptyList());
    }
}
