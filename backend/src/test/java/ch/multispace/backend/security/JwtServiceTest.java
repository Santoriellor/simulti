package ch.multispace.backend.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private static final String TEST_SECRET =
            "test-secret-key-that-is-long-enough-for-hs256!!";
    private static final String OLD_HARDCODED_KEY =
            "YOUR_SECRET_KEY_HERE_CHANGE_THIS_TO_A_256_BIT_KEY";

    private JwtService jwtService;
    private UserDetails user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", TEST_SECRET);
        jwtService.validateKeyOnStartup();
        user = new User("alice@example.com", "pw", Collections.emptyList());
    }

    @Test
    void tokenSignedWithConfiguredKeyIsAccepted() {
        String token = jwtService.generateToken(user);
        assertEquals("alice@example.com", jwtService.extractUsername(token));
    }

    @Test
    void tokenForgedWithOldHardcodedKeyIsRejected() {
        String forged = Jwts.builder()
                .setSubject("attacker@example.com")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(
                                  OLD_HARDCODED_KEY.getBytes(StandardCharsets.UTF_8)),
                          SignatureAlgorithm.HS256)
                .compact();

        assertThrows(JwtService.InvalidJwtException.class,
                     () -> jwtService.extractUsername(forged));
    }

    @Test
    void startupFailsWhenSecretIsBlank() {
        JwtService svc = new JwtService();
        ReflectionTestUtils.setField(svc, "secretKey", "");
        assertThrows(IllegalStateException.class, svc::validateKeyOnStartup);
    }

    @Test
    void startupFailsWhenSecretIsTooShort() {
        JwtService svc = new JwtService();
        ReflectionTestUtils.setField(svc, "secretKey", "tooshort");
        assertThrows(IllegalStateException.class, svc::validateKeyOnStartup);
    }

    @Test
    void startupFailsWhenSecretIsStillThePlaceholder() {
        JwtService svc = new JwtService();
        ReflectionTestUtils.setField(svc, "secretKey", OLD_HARDCODED_KEY);
        assertThrows(IllegalStateException.class, svc::validateKeyOnStartup);
    }
}
