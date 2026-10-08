package traffic;
import java.util.*;

/** Engine: spawning, adaptive signal control, emergency override, gap-based car following, pedestrians. */
public class Simulation {
    public enum Mode { FIXED, ADAPTIVE, ECO }
    public static final double CL = 22, CW = 10;
    public final Junction j; 
    public final List<Vehicle> vehicles = new ArrayList<>(); 
    public final List<Pedestrian> peds = new ArrayList<>();
    public final int[] queue;
    public final int[] armCount;
    public double rate = .12, weather = 1, time, co2, sumWait; 
    public int out; 
    public Mode mode = Mode.ADAPTIVE;
    public int cur, next; 
    public char phase = 'G'; 
    private double pt; 
    private final Random rnd = new Random();

    public Simulation(Junction j) { 
        this.j = j; 
        queue = new int[j.dir.length]; 
        armCount = new int[j.dir.length]; 
    }

    public char signal(int arm) { 
        if (j.kind.ring) return 'G'; 
        if (arm == cur) return phase == 'G' ? 'G' : phase == 'Y' ? 'Y' : 'R'; 
        return 'R'; 
    }

    public double avgWait() { return out > 0 ? sumWait / out : 0; }

    public boolean spawn(boolean emergency) { 
        int n = queue.length, a = rnd.nextInt(n); 
        for (int k = 0; k < n; k++) if (trySpawn((a+k)%n, emergency)) return true; 
        return false; 
    }

    private boolean trySpawn(int arm, boolean em) {
        List<Route> rs = new ArrayList<>(); 
        for (Route r : j.routes) if (r.from == arm) rs.add(r);
        Route r = null;
        if (em) {
            double bestGap = -1;
            for (Route cand : rs) {
                double gap = 1e9;
                for (Vehicle o : vehicles) {
                    if (o.route.from == arm && o.route.lane == cand.lane) {
                        if (o.s < gap) gap = o.s;
                    }
                }
                if (gap > bestGap) {
                    bestGap = gap;
                    r = cand;
                }
            }
        } else {
            r = rs.get(rnd.nextInt(rs.size())); 
        }
        double[] p = r.at(0);
        for (Vehicle o : vehicles) if (Math.hypot(o.x-p[0], o.y-p[1]) < CL*1.8) return false;
        Vehicle v = new Vehicle(r, em); 
        v.x = p[0]; v.y = p[1]; v.hx = p[2]; v.hy = p[3]; 
        vehicles.add(v); 
        return true; 
    }

    private void go(int n) { next = n; phase = 'Y'; pt = 0; }

    private int pick() { 
        int n = queue.length; 
        if (mode == Mode.FIXED) return (cur+1)%n; 
        int best = (cur+1)%n, bq = -1;
        for (int k = 1; k < n; k++) { 
            int i = (cur+k)%n; 
            if (queue[i] > bq) { bq = queue[i]; best = i; } 
        } 
        return best; 
    }

    private void signals(double dt) {
        if (j.kind.ring) return; 
        pt += dt; 
        Vehicle em = null;
        for (Vehicle v : vehicles) if (v.emergency && v.s < v.route.gate + 20) { em = v; break; }
        if (phase == 'G') { 
            int q = queue[cur];
            if (em != null) { 
                if (em.route.from == cur) pt = Math.min(pt, 1); 
                else if (pt > 1) go(em.route.from); 
            } else { 
                boolean eco = mode == Mode.ECO, other = false; 
                for (int i = 0; i < queue.length; i++) if (i != cur && queue[i] > 0) other = true;
                double dur = mode == Mode.FIXED ? 10 : Math.min(eco ? 16 : 22, Math.max(5, (eco ? 4 : 6) + 1.3*q));
                if (pt > dur || (mode != Mode.FIXED && q == 0 && pt > 3 && other)) go(pick()); 
            }
        } else if (phase == 'Y' && pt > 2) { phase = 'R'; pt = 0; }
        else if (phase == 'R' && pt > (em != null ? .5 : 1)) { cur = next; phase = 'G'; pt = 0; }
    }

    private boolean ringBusy(Vehicle v) { 
        double th = j.ang[v.route.from];
        for (Vehicle o : vehicles) { 
            if (o == v || Math.abs(Math.hypot(o.x, o.y) - Junction.R) > 10) continue;
            double d = ((th - Math.atan2(o.y, o.x)) % (2*Math.PI) + 2*Math.PI) % (2*Math.PI); 
            if (d < 1.0) return true; 
        } 
        return false; 
    }

    public void step(double dt) {
        time += dt; 
        signals(dt); 
        Arrays.fill(queue, 0); 
        Arrays.fill(armCount, 0);

        if (rnd.nextDouble() < rate * dt * 0.4) {
            int arm = rnd.nextInt(j.dir.length);
            peds.add(new Pedestrian(arm, rnd.nextBoolean() ? 1 : -1));
        }

        for (Iterator<Pedestrian> it = peds.iterator(); it.hasNext();) {
            Pedestrian p = it.next();
            if (p.waiting) {
                p.waitTime += dt;
                boolean safe = true;
                if (!j.kind.ring && signal(p.arm) != 'R') safe = false;
                if (j.kind.ring) {
                    for (Vehicle v : vehicles) {
                        if (v.route.from == p.arm && v.s < v.route.gate - 10 && v.s > v.route.gate - 100) safe = false;
                    }
                }
                if (safe || p.waitTime > 10) p.waiting = false;
            } else {
                p.crossPos += p.direction * dt * 0.35;
                if (p.crossPos < 0 || p.crossPos > 1) it.remove();
            }
        }

        for (int i = 0; i < queue.length; i++) if (rnd.nextDouble() < rate*dt) trySpawn(i, false);
        
        for (Vehicle v : vehicles) {
            if (v.emergency && v.s < v.route.gate - 20) {
                double myGap = 1e9;
                for (Vehicle o : vehicles) if (o != v && o.route.from == v.route.from && o.route.lane == v.route.lane && o.s > v.s) {
                    if (o.s - v.s < myGap) myGap = o.s - v.s;
                }
                if (myGap < 80) { // Try to change if blocked
                    Route best = v.route;
                    double bestGap = myGap;
                    for (Route cand : j.routes) {
                        if (cand.from == v.route.from && cand.lane != v.route.lane) {
                            double cGap = 1e9;
                            boolean safe = true;
                            for (Vehicle o : vehicles) {
                                if (o == v) continue;
                                if (o.route.from == cand.from && o.route.lane == cand.lane) {
                                    double dist = o.s - v.s;
                                    if (dist > -CL*1.2 && dist < CL*1.2) safe = false;
                                    if (dist > 0 && dist < cGap) cGap = dist;
                                }
                            }
                            if (safe && cGap > bestGap + 30) {
                                bestGap = cGap;
                                best = cand;
                            }
                        }
                    }
                    if (best != v.route) v.route = best;
                }
            }
        }
        
        for (Vehicle v : vehicles) { 
            double[] a = v.route.at(v.s); 
            v.x = a[0]; v.y = a[1]; v.hx = a[2]; v.hy = a[3];
            armCount[v.route.from]++;
            if (v.s < v.route.gate + 10 && v.v < 8 && v.route.gate - v.s < 260) queue[v.route.from]++; 
        }

        double vmax = 95*weather, b = 100*(.55 + .45*weather);
        for (Vehicle v : vehicles) { 
            double gap = 1e9;
            for (Vehicle o : vehicles) { 
                if (o == v) continue; 
                double dx = o.x-v.x, dy = o.y-v.y, f = dx*v.hx + dy*v.hy;
                if (f <= 0 || f > 90 || Math.abs(-dx*v.hy + dy*v.hx) > CW+3 || o.hx*v.hx + o.hy*v.hy < .3) continue; 
                gap = Math.min(gap, f - CL); 
            }
            double tg = v.route.gate - v.s - CL/2;
            if (tg > -1) {
                if (!j.kind.ring && !j.freeLeft(v.route) && !v.emergency) { 
                    char s = signal(v.route.from); 
                    if (s != 'G' && !(s == 'Y' && tg < v.v*v.v/(2*b))) gap = Math.min(gap, tg); 
                }
                if (j.kind.ring && tg < 40 && !v.emergency && ringBusy(v)) gap = Math.min(gap, tg); 
            }
            
            double crossS = v.route.gate - Junction.CROSSING_OFFSET - CL/2 - 5;
            double pDist = crossS - v.s;
            if (pDist > -CL && pDist < 100) {
                for (Pedestrian p : peds) {
                    if (p.arm == v.route.from && !p.waiting) {
                        double pw = j.laneCount * Junction.BASE_L * 4;
                        double px = (p.crossPos - 0.5) * pw;
                        double laneOff = Junction.BASE_L * (2 * v.route.lane + 1);
                        if (Math.abs(px - laneOff) < 30 || Math.abs(px - (-laneOff)) < 30) {
                            gap = Math.min(gap, Math.max(0, pDist));
                        }
                    }
                }
            }

            double rc = Math.hypot(v.x, v.y);
            double jExit = v.route.length() - v.route.gate;
            if (v.still > 6 && v.s > v.route.gate - 15 && v.s < jExit + 15) gap = 1e9; 
            double vt = vmax * (v.emergency ? 1.3 : 1); 
            if (v.route.turn != 'S' && rc < j.stop + 25) vt = Math.min(vt, 55*weather);
            vt = Math.min(vt, Math.sqrt(2*b*Math.max(0, gap - 2))); 
            if (v.breakdown > 0) { vt = 0; v.breakdown -= dt; }
            v.v = Math.max(0, v.v + Math.max(-b*1.6*dt, Math.min(60*dt, vt - v.v))); 
        }

        for (Iterator<Vehicle> it = vehicles.iterator(); it.hasNext();) { 
            Vehicle v = it.next(); 
            v.s += v.v*dt;
            if (v.v < 2) { v.wait += dt; v.still += dt; co2 += .6*dt; } 
            else { v.still = 0; co2 += 1.8*dt; }
            if (v.s >= v.route.length()) { out++; sumWait += v.wait; it.remove(); } 
        }
    }
}
