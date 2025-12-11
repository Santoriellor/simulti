package ch.multispace.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor
public class Ufo {
    public double x, y, w, h, vx;
    public int scoreValue = 200;

    public Ufo(double x, double y, double w, double h, double vx, int scoreValue) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.vx = vx;
        this.scoreValue = scoreValue;
    }
}
