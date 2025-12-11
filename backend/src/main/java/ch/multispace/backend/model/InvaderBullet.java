package ch.multispace.backend.model;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor
public class InvaderBullet {
    public double x, y, w, h, vy;

    public InvaderBullet(double x, double y, double w, double h, double vy) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.vy = vy;
    }
}
