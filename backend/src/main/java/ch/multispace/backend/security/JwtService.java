package ch.multispace.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Service for generating, validating, and extracting JWTs for both HTTP and WebSocket usage.
 */
@Service
public class JwtService {

    private static final String SECRET_KEY = "YOUR_SECRET_KEY_HERE_CHANGE_THIS_TO_A_256_BIT_KEY";
    private static final long EXPIRATION_TIME_MS = 1000L * 60 * 60 * 4; // 4h

    // ----------------------
    // Token Generation
    // ----------------------

    /**
     * Generates a JWT with standard claims (sub=username) and optional extra claims.
     * @param userDetails Spring Security UserDetails
     * @param extraClaims Map of additional claims (e.g., userId)
     * @return JWT string
     */
    public String generateToken(UserDetails userDetails, Map<String, Object> extraClaims) {
        Map<String, Object> claims = new HashMap<>(extraClaims);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME_MS))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Convenience method for generating a JWT with no extra claims.
     */
    public String generateToken(UserDetails userDetails) {
        return generateToken(userDetails, new HashMap<>());
    }

    /**
     * Generates a JWT specifically for WebSocket usage including userId claim.
     */
    public String generateTokenForWebSocket(UserDetails userDetails, UUID userId) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("userId", userId.toString());
        return generateToken(userDetails, extraClaims);
    }

    // ----------------------
    // Token Validation
    // ----------------------

    /**
     * Validates token against a UserDetails object for standard HTTP authentication.
     */
    public void validateToken(String token, UserDetails userDetails) throws InvalidJwtException {
        String username = extractUsername(token);
        if (!username.equals(userDetails.getUsername()) || isTokenExpired(token)) {
            throw new InvalidJwtException("JWT invalid or expired");
        }
    }

    /**
     * Simple validation for WebSocket usage.
     */
    public void validateTokenForWebSocket(String token) throws InvalidJwtException {
        if (isTokenExpired(token)) {
            throw new InvalidJwtException("JWT expired");
        }
    }

    // ----------------------
    // Token Extraction
    // ----------------------

    /**
     * Extracts the subject (username/email) from the token.
     */
    public String extractUsername(String token) throws InvalidJwtException {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Generic extraction method using a claims resolver function.
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) throws InvalidJwtException {
        return claimsResolver.apply(extractAllClaims(token));
    }

    /**
     * Extracts a specific claim for WebSocket usage (e.g., userId).
     */
    public String extractClaimForWebSocket(String token, String claimName) throws InvalidJwtException {
        Object claim = extractAllClaims(token).get(claimName);
        return claim != null ? claim.toString() : null;
    }

    /**
     * Extracts all claims from a token, throwing InvalidJwtException on failure.
     */
    private Claims extractAllClaims(String token) throws InvalidJwtException {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSignInKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            throw new InvalidJwtException("JWT expired", e);
        } catch (JwtException e) {
            throw new InvalidJwtException("Invalid JWT", e);
        }
    }

    private boolean isTokenExpired(String token) throws InvalidJwtException {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    // ----------------------
    // Key Generation
    // ----------------------

    private Key getSignInKey() {
        return Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
    }

    // ----------------------
    // Exception
    // ----------------------

    public static class InvalidJwtException extends RuntimeException {
        public InvalidJwtException(String message) { super(message); }
        public InvalidJwtException(String message, Throwable cause) { super(message, cause); }
    }
}
