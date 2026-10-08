package traffic;
/** A vehicle that follows a precomputed Route; position/heading are refreshed each step. */
public class Vehicle {
    public Route route; public final boolean emergency;
    public double s, v = 50, x, y, hx, hy, wait, still, breakdown;
    public Vehicle(Route r, boolean emergency) { this.route = r; this.emergency = emergency; }
}
