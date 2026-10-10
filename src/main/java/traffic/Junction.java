package traffic;
import java.util.*;

/** Geometry of a junction type with configurable lane count and all its routes (left-hand traffic). */
public class Junction {
    public enum Kind {
        FOUR("4-Way Junction", false, 0, 90, 180, 270),
        T("3-Way T-Junction", false, 0, 180, 270),
        Y("Y-Junction", false, 90, 210, 330),
        ROUNDABOUT("Roundabout (4 exits)", true, 0, 90, 180, 270),
        ROUNDABOUT5("Roundabout (5 exits)", true, -90, -18, 54, 126, 198),
        FIVE("5-Way Junction", false, 0, 72, 144, 216, 288);
        public final String label; public final boolean ring; public final int[] angles;
        Kind(String l, boolean r, int... a) { label = l; ring = r; angles = a; }
    }

    public static final double BASE_L = 11, R = 60, ARM = 650;
    /** Distance from the junction mouth to the zebra-crossing centre, along the arm. */
    public static final double CROSSING_OFFSET = 16;
    /** Distance from junction mouth to stop line, ensuring vehicles stop well before the zebra crossing. */
    public static final double STOP_LINE_OFFSET = 38;

    public final Kind kind;
    public final int laneCount;
    public final double[][] dir;
    public final double[] ang;
    public final double stop;
    public final List<Route> routes = new ArrayList<>();
    /** When true, left turns on non-roundabout junctions are unsignalised (free). */
    public boolean freeLeftEnabled = true;

    public Junction(Kind k) { this(k, 1); }

    public Junction(Kind k, int lanes) {
        kind = k;
        // Roundabouts are always single-lane; others clamp to [1, 3]
        laneCount = k.ring ? 1 : Math.max(1, Math.min(3, lanes));
        int n = k.angles.length;
        dir = new double[n][2];
        ang = new double[n];
        // Widen stop distance for extra lanes so bezier curves fit inside the junction centre
        stop = k.ring ? R + 46 : R + Math.max(0, (laneCount - 1) * 18);
        for (int i = 0; i < n; i++) {
            ang[i] = Math.toRadians(k.angles[i]);
            dir[i] = new double[]{Math.cos(ang[i]), Math.sin(ang[i])};
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j) {
                    double[] A = dir[i], B = dir[j]; 
                    double dl = -B[0] * A[1] + B[1] * A[0];
                    char turn = dl > .35 ? 'L' : dl < -.35 ? 'R' : 'S';
                    
                    for (int lane = 0; lane < laneCount; lane++) {
                        // In left-hand traffic:
                        // lane 0 is innermost (rightmost for driver) -> for Right turns and Straight
                        // lane laneCount-1 is outermost (leftmost for driver) -> for Left turns and Straight
                        boolean allow = false;
                        if (kind.ring) {
                            allow = true; // roundabouts are 1-lane anyway
                        } else if (laneCount == 1) {
                            allow = true;
                        } else if (laneCount == 2) {
                            if (turn == 'R' && lane == 0) allow = true;
                            if (turn == 'L' && lane == 1) allow = true;
                            if (turn == 'S') allow = true; 
                        } else { // 3 lanes
                            if (turn == 'R' && lane == 0) allow = true;
                            if (turn == 'S' && lane == 1) allow = true;
                            if (turn == 'L' && lane == 2) allow = true;
                            if (turn == 'S' && lane == 0) allow = true; // straight can also use right lane
                            if (turn == 'S' && lane == 2) allow = true; // straight can also use left lane
                        }
                        
                        if (allow) {
                            routes.add(build(i, j, lane));
                        }
                    }
                }
            }
        }
    }

    /** Free (unsignalised) left turn: only the immediate adjacent left exit (slip lane). */
    public boolean freeLeft(Route r) {
        return freeLeftEnabled && !kind.ring && r.turn == 'L' && r.to == (r.from + 1) % dir.length;
    }

    /** Lateral offset from arm centre-line for the given lane (0 = innermost). */
    private double laneOffset(int lane) { return BASE_L * (2 * lane + 1); }

    private double[] pt(double[] d, double dist, double[] o, double offset) {
        return new double[]{d[0] * dist + o[0] * offset, d[1] * dist + o[1] * offset};
    }

    private Route build(int i, int j, int lane) {
        double[] A = dir[i], B = dir[j];
        double dl = -B[0] * A[1] + B[1] * A[0];
        Route r = new Route(i, j, dl > .35 ? 'L' : dl < -.35 ? 'R' : 'S', lane);
        double off = laneOffset(lane);
        double[] oi = {-A[1], A[0]}, oo = {B[1], -B[0]};
        double[] p0 = pt(A, ARM, oi, off), e = pt(A, stop, oi, off);
        double[] x = pt(B, stop, oo, off), end = pt(B, ARM, oo, off);
        r.gate = ARM - (stop + STOP_LINE_OFFSET);
        r.pts.add(p0);
        r.pts.add(e);
        if (!kind.ring) {
            double ex = e[0] - x[0], ey = e[1] - x[1];
            double det = -A[0] * B[1] + B[0] * A[1];
            double[] c = {(e[0] + x[0]) / 2, (e[1] + x[1]) / 2};
            if (Math.abs(det) > 1e-3) {
                double t = (-ex * B[1] + B[0] * ey) / det;
                if (t > 0 && t < 2 * stop)
                    c = new double[]{e[0] - t * A[0], e[1] - t * A[1]};
            }
            for (int k = 1; k <= 14; k++) {
                double u = k / 14.0, a = (1 - u) * (1 - u), b = 2 * u * (1 - u), cc = u * u;
                r.pts.add(new double[]{a * e[0] + b * c[0] + cc * x[0],
                                       a * e[1] + b * c[1] + cc * x[1]});
            }
        } else {
            double d = Math.asin(BASE_L / R), a0 = ang[i] + d, a1 = ang[j] - d;
            while (a1 <= a0) a1 += 2 * Math.PI;
            double[] r0 = {R * Math.cos(a0), R * Math.sin(a0)};
            double[] r1 = {R * Math.cos(a1), R * Math.sin(a1)};
            for (int k = 1; k <= 4; k++) {
                double u = k / 5.0;
                r.pts.add(new double[]{e[0] + (r0[0] - e[0]) * u, e[1] + (r0[1] - e[1]) * u});
            }
            for (double a = a0; a < a1; a += .08)
                r.pts.add(new double[]{R * Math.cos(a), R * Math.sin(a)});
            r.pts.add(r1);
            for (int k = 1; k <= 4; k++) {
                double u = k / 5.0;
                r.pts.add(new double[]{r1[0] + (x[0] - r1[0]) * u, r1[1] + (x[1] - r1[1]) * u});
            }
        }
        r.pts.add(x);
        r.pts.add(end);
        r.finish();
        return r;
    }
}
