package traffic;
import java.util.*;

/** Polyline path from an arm's spawn point, through the junction, to an exit arm. */
public class Route {
    public final int from, to;
    public final char turn; // 'L','S','R'
    public final int lane;  // 0-indexed lane number
    public final List<double[]> pts = new ArrayList<>();
    public double[] cum;
    public double gate;

    public Route(int f, int t, char turn, int lane) {
        from = f; to = t; this.turn = turn; this.lane = lane;
    }

    public void finish() {
        cum = new double[pts.size()];
        for (int i = 1; i < cum.length; i++)
            cum[i] = cum[i - 1] + Math.hypot(pts.get(i)[0] - pts.get(i - 1)[0],
                                              pts.get(i)[1] - pts.get(i - 1)[1]);
    }

    public double length() { return cum[cum.length - 1]; }

    /** @return {x, y, headingX, headingY} at distance s along the route */
    public double[] at(double s) {
        int i = 1;
        while (i < cum.length - 1 && cum[i] < s) i++;
        double[] a = pts.get(i - 1), b = pts.get(i);
        double d = Math.max(cum[i] - cum[i - 1], 1e-6);
        double t = Math.max(0, Math.min(1, (s - cum[i - 1]) / d));
        return new double[]{
            a[0] + (b[0] - a[0]) * t,
            a[1] + (b[1] - a[1]) * t,
            (b[0] - a[0]) / d,
            (b[1] - a[1]) / d
        };
    }
}
