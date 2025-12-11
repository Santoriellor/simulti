package ch.multispace.backend.controllers;

import ch.multispace.backend.model.User;
import ch.multispace.backend.repositories.UserRepository;
import ch.multispace.backend.services.AuthService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            String token = authService.register(request.getEmail(), request.getUsername(), request.getPassword());
            return ResponseEntity.ok(new TokenResponse(token));
        } catch (AuthService.DuplicateEmailException | AuthService.DuplicateUsernameException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            String token = authService.login(request.getEmail(), request.getPassword());
            return ResponseEntity.ok(new TokenResponse(token));
        } catch (AuthService.InvalidCredentialsException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<User> getCurrentUser(@AuthenticationPrincipal org.springframework.security.core.userdetails.User userDetails) {
        // userDetails.getUsername() contains the email
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(user);
    }

    @Data
    public static class RegisterRequest {
        private String email;
        private String username;
        private String password;
    }

    @Data
    public static class LoginRequest {
        private String email;
        private String password;
    }

    @Data
    @AllArgsConstructor
    static class TokenResponse {
        private String token;
    }
}
