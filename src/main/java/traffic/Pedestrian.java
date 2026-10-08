package traffic;

/** A pedestrian that crosses at a zebra crossing on a junction arm. */
public class Pedestrian {
    public final int arm;        // which arm's crossing
    public final int direction;  // +1 (cross left→right) or -1 (right→left)
    public double crossPos;      // 0.0 → 1.0 progress across the road
    public double x, y;          // rendered world position
    public boolean waiting = true;
    public double waitTime;

    public Pedestrian(int arm, int direction) {
        this.arm = arm;
        this.direction = direction;
        this.crossPos = direction > 0 ? 0.0 : 1.0;
    }
}
