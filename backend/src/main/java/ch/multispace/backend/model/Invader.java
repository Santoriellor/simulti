package ch.multispace.backend.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Invader extends Rect {
    private int row;
    private int col;
    private int type;
    private boolean alive = true;

    public Invader(double x, double y, double w, double h, int row, int col, int type) {
        super(x, y, w, h);
        this.row = row;
        this.col = col;
        this.type = type;
    }
}
