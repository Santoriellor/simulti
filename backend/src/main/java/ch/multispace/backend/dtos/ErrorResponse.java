package ch.multispace.backend.dtos;

/** The single error shape returned by every failing endpoint. */
public record ErrorResponse(String error) {}
