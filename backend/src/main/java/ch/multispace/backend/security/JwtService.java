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
import java.util.function.Function;

@Service
public class JwtService {

    private static final String SECRET_KEY = "YOUR_SECRET_KEY_HERE_CHANGE_THIS_TO_A_256_BIT_KEY";
    private static final long EXPIRATION_TIME = 1000 * 60 * 60 * 24;

    // --- Extraction helpers ---

    public String extractUsername(String token) throws InvalidJwtException {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) throws InvalidJwtException {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

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

    private Date extractExpiration(String token) throws InvalidJwtException {
        return extractClaim(token, Claims::getExpiration);
    }

    private boolean isTokenExpired(String token) throws InvalidJwtException {
        return extractExpiration(token).before(new Date());
    }

    // --- Token generation helpers ---

    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME)) // 24h
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateTokenWithExtraClaims(Map<String, Object> extraClaims, UserDetails userDetails) {
        return generateToken(extraClaims, userDetails);
    }

    // --- Token validation helpers ---

    /** Used by Spring Security filter with UserDetails */
    public void validateToken(String token, UserDetails userDetails) throws InvalidJwtException {
        final String username = extractUsername(token);
        if (!username.equals(userDetails.getUsername())) {
            throw new InvalidJwtException("JWT username does not match user");
        }
        if (isTokenExpired(token)) {
            throw new InvalidJwtException("JWT expired");
        }
    }

    /** Simpler overload for WebSocket handshakes */
    public void validateToken(String token) throws InvalidJwtException {
        if (isTokenExpired(token)) {
            throw new InvalidJwtException("JWT expired or invalid");
        }
    }

    // --- Key generation helpers ---

    private Key getSignInKey() {
        return Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
    }

    // --- Custom exception ---

    public static class InvalidJwtException extends RuntimeException {
        public InvalidJwtException(String message) { super(message); }
        public InvalidJwtException(String message, Throwable cause) { super(message, cause); }
    }
}
