package ch.multispace.backend.exceptions;

import ch.multispace.backend.dtos.ErrorResponse;
import ch.multispace.backend.security.JwtService;
import ch.multispace.backend.services.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * The only place an HTTP status is chosen for a failure. Controllers throw meaning; this class
 * turns meaning into a status code and one error shape.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} rather than relying solely on a catch-all
 * {@code @ExceptionHandler(Exception.class)}. That superclass declares its own
 * {@code @ExceptionHandler} methods for framework-level failures (malformed JSON, wrong HTTP verb,
 * unsupported media type, a path variable that does not parse, an unmapped route, ...). Spring
 * picks the most specific matching handler method for a thrown exception's type, so those
 * framework-specific handlers win over our generic {@code Exception.class} fallback below and keep
 * translating those failures into their correct 4xx statuses. Without the inheritance, our
 * catch-all was the only method Spring's advice resolver had ever seen for these types and it
 * pre-empted the framework's own (non-advice) resolver, turning every one of those cases into a
 * 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler({
        UnauthorizedException.class,
        AuthService.InvalidCredentialsException.class,
        JwtService.InvalidJwtException.class
    })
    public ResponseEntity<ErrorResponse> handleUnauthorized(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler({
        AuthService.DuplicateEmailException.class,
        AuthService.DuplicateUsernameException.class
    })
    public ResponseEntity<ErrorResponse> handleDuplicate(RuntimeException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    /**
     * Anything unmapped by us or by {@link ResponseEntityExceptionHandler}'s framework-specific
     * handlers is a bug, not a client error. It is logged with its stack trace and reported without
     * internal detail.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        LOGGER.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Internal error"));
    }
}
