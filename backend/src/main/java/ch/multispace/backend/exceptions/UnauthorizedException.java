package ch.multispace.backend.exceptions;

/** Thrown when credentials are missing or unusable. Translated to 401. */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
