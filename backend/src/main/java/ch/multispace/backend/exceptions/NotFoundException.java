package ch.multispace.backend.exceptions;

/** Thrown when a lookup finds nothing. Translated to 404. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
