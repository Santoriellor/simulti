package ch.multispace.backend.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ShieldCell extends Rect {
    private int hp = 3;
}
