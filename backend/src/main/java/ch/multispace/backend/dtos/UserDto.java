package ch.multispace.backend.dtos;

import ch.multispace.backend.model.User;
import java.util.UUID;

/**
 * The public shape of a user. There is no password field, so no future change to the entity can
 * reintroduce the leak this type was created to close.
 */
public record UserDto(UUID id, String username, String email) {

    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getUsername(), user.getEmail());
    }
}
