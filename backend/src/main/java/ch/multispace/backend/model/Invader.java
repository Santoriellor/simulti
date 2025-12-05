package ch.multispace.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor
public class Invader {
    public double x, y, w, h;
    public boolean alive = true;
    public int type = 1;

    public Invader(double x, double y, double w, double h, int type) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.type = type;
    }
}
