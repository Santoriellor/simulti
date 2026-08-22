package ch.multispace.backend.exceptions;

/** Thrown when an authenticated caller lacks the right to act. Translated to 403. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
