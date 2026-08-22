package ch.multispace.backend.dtos;

/**
 * The error shape returned for the application's own failures, as thrown from controllers and
 * services and translated by {@link ch.multispace.backend.exceptions.GlobalExceptionHandler}.
 * Framework-level 4xx failures (malformed JSON, a wrong HTTP verb, an unmapped route, an
 * unsupported content type) are not application failures — they are handled by the inherited {@link
 * org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler} behaviour
 * and returned as RFC 7807 {@code application/problem+json} bodies instead, a different shape
 * entirely. See {@code docs/decisions/0003-deferred-findings.md} for why the two shapes were not
 * unified.
 */
public record ErrorResponse(String error) {}
