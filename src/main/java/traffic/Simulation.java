package traffic;

import java.util.*;

/**
 * Engine: spawning, vehicle variety, adaptive signal control, emergency override,
 * fastest-lane detection and dynamic lane switching for emergency vehicles,
 * accident simulation and police/ambulance clearing logic,
 * robust collision physics (IDM car-following, hard non-penetration guards, cross-traffic yielding),
 * pedestrians crossing and footpath sidewalk community life.
 */
public class Simulation {
    public enum Mode { FIXED, ADAPTIVE, ECO }
    public static final double CL = 20, CW = 10;
    public static final double SAFE_BUFFER = 7.0; // Bumper-to-bumper buffer distance (pixels)

    public final Junction j;
    public final List<Vehicle> vehicles = new ArrayList<>();
    public final List<Pedestrian> peds = new ArrayList<>();
    public final List<FootpathPerson> sidewalkPeople = new ArrayList<>();
    public final List<Accident> accidents = new ArrayList<>();
    public final int[] queue;
    public final int[] armCount;
    public double rate = .14, weather = 1, time, co2, sumWait;
    public int out;
    public int accidentsCleared = 0;
    public Mode mode = Mode.ADAPTIVE;
    public int cur, next;
    public char phase = 'G';
    private double pt;
    private final Random rnd = new Random();

    public static class Accident {
        public final Vehicle victim;
        public final int arm;
        public final int lane;
        public final double s;
        public Vehicle responder = null;
        public boolean responderArrived = false;
        public double clearanceTimer = 0.0;
        public boolean cleared = false;

        public Accident(Vehicle victim, int arm, int lane, double s) {
            this.victim = victim;
            this.arm = arm;
            this.lane = lane;
            this.s = s;
        }
    }

    public enum PedestrianDensity {
        NONE("Off (0%)", 0.0, 0),
        LOW("Low (35%)", 0.35, 2),
        NORMAL("Normal (100%)", 1.0, 5),
        HIGH("High (200%)", 2.2, 9);

        public final String label;
        public final double spawnMultiplier;
        public final int peoplePerArm;
        PedestrianDensity(String l, double sm, int ppa) {
            this.label = l; this.spawnMultiplier = sm; this.peoplePerArm = ppa;
        }
    }

    public PedestrianDensity pedDensity = PedestrianDensity.NORMAL;

    public void setPedestrianDensity(PedestrianDensity pd) {
        this.pedDensity = pd;
        initSidewalkPeople();
    }

    public Simulation(Junction j) {
        this.j = j;
        queue = new int[j.dir.length];
        armCount = new int[j.dir.length];
        initSidewalkPeople();
    }

    public void initSidewalkPeople() {
        sidewalkPeople.clear();
        if (pedDensity == PedestrianDensity.NONE) return;

        java.awt.Color[] clothesColors = {
            new java.awt.Color(41, 128, 185),
            new java.awt.Color(231, 76, 60),
            new java.awt.Color(46, 204, 113),
            new java.awt.Color(241, 196, 15),
            new java.awt.Color(155, 89, 182),
            new java.awt.Color(52, 73, 94)
        };
        java.awt.Color[] dogColors = {
            new java.awt.Color(160, 110, 60),
            new java.awt.Color(40, 40, 40),
            new java.awt.Color(230, 220, 200)
        };

        for (int a = 0; a < j.dir.length; a++) {
            // Walkers on left and right sidewalks
            int walkers = Math.max(1, (int)(pedDensity.peoplePerArm * 0.5));
            for (int w = 0; w < walkers; w++) {
                int side = (w % 2 == 0) ? 1 : -1;
                sidewalkPeople.add(new FootpathPerson(FootpathPerson.Activity.WALKING,
                    a, side, j.stop + 60 + rnd.nextDouble() * (Junction.ARM - j.stop - 100), (side > 0 ? -1 : 1) * (14 + rnd.nextDouble() * 10),
                    clothesColors[rnd.nextInt(clothesColors.length)], null));
            }

            // Dog walker with pet dog
            if (pedDensity != PedestrianDensity.LOW || (a % 2 == 0)) {
                sidewalkPeople.add(new FootpathPerson(FootpathPerson.Activity.WALKING_DOG,
                    a, (rnd.nextBoolean() ? 1 : -1), j.stop + 90 + rnd.nextDouble() * (Junction.ARM - j.stop - 140),
                    11 + rnd.nextDouble() * 8,
                    clothesColors[rnd.nextInt(clothesColors.length)],
                    dogColors[rnd.nextInt(dogColors.length)]));
            }

            // Conversing pair near wide corner pavement
            if (pedDensity.peoplePerArm >= 4) {
                java.awt.Color c1 = clothesColors[rnd.nextInt(clothesColors.length)];
                java.awt.Color c2 = clothesColors[rnd.nextInt(clothesColors.length)];
                double cornerDist = j.stop + 45.0 + rnd.nextDouble() * 15.0;

                FootpathPerson p1 = new FootpathPerson(FootpathPerson.Activity.CONVERSING,
                    a, 1, cornerDist, 0, c1, null);
                p1.facingAngle = j.ang[a] + 1.2;
                sidewalkPeople.add(p1);

                FootpathPerson p2 = new FootpathPerson(FootpathPerson.Activity.CONVERSING,
                    a, 1, cornerDist + 4.0, 0, c2, null);
                p2.facingAngle = j.ang[a] - 1.8;
                sidewalkPeople.add(p2);
            }
        }
    }

    public char signal(int arm) {
        if (j.kind.ring) return 'G';
        if (arm == cur) return phase == 'G' ? 'G' : phase == 'Y' ? 'Y' : 'R';
        return 'R';
    }

    public double avgWait() { return out > 0 ? sumWait / out : 0; }

    public boolean spawn(boolean emergency) {
        if (emergency) {
            Vehicle.Type[] emTypes = {Vehicle.Type.POLICE, Vehicle.Type.AMBULANCE, Vehicle.Type.FIRE_TRUCK};
            return spawn(emTypes[rnd.nextInt(emTypes.length)]);
        } else {
            double r = rnd.nextDouble();
            Vehicle.Type type;
            if (r < 0.50) type = Vehicle.Type.CAR;
            else if (r < 0.62) type = Vehicle.Type.TAXI;
            else if (r < 0.72) type = Vehicle.Type.BIKE;
            else if (r < 0.80) type = Vehicle.Type.CYCLE;
            else if (r < 0.90) type = Vehicle.Type.BUS;
            else type = Vehicle.Type.TRUCK;
            return spawn(type);
        }
    }

    public boolean spawn(Vehicle.Type type) {
        int n = queue.length, a = rnd.nextInt(n);
        for (int k = 0; k < n; k++) {
            if (trySpawn((a + k) % n, type)) return true;
        }
        return false;
    }

    public boolean trySpawn(int arm, Vehicle.Type type) {
        List<Route> rs = new ArrayList<>();
        for (Route r : j.routes) if (r.from == arm) rs.add(r);
        if (rs.isEmpty()) return false;

        Route r = null;
        if (type.isEmergency) {
            int bestLane = getFastestLane(arm, 0);
            for (Route cand : rs) {
                if (cand.lane == bestLane) {
                    r = cand;
                    break;
                }
            }
        }
        if (r == null) {
            r = rs.get(rnd.nextInt(rs.size()));
        }

        double[] p = r.at(0);
        for (Vehicle o : vehicles) {
            if (o.route.from == arm && o.route.lane == r.lane) {
                double minClearance = (type.length + o.length) / 2.0 + SAFE_BUFFER + 8.0;
                if (o.s < minClearance) return false;
            }
            if (Math.hypot(o.x - p[0], o.y - p[1]) < (type.length + o.length) / 2.0 + SAFE_BUFFER + 8.0) return false;
        }

        Vehicle v = new Vehicle(r, type);
        v.x = p[0]; v.y = p[1]; v.hx = p[2]; v.hy = p[3];
        vehicles.add(v);
        return true;
    }

    private void go(int n) { next = n; phase = 'Y'; pt = 0; }

    private int pick() {
        int n = queue.length;
        if (mode == Mode.FIXED) return (cur + 1) % n;
        int best = (cur + 1) % n, bq = -1;
        for (int k = 1; k < n; k++) {
            int i = (cur + k) % n;
            if (queue[i] > bq) { bq = queue[i]; best = i; }
        }
        return best;
    }

    private void signals(double dt) {
        if (j.kind.ring) return;
        pt += dt;
        Vehicle em = null;
        for (Vehicle v : vehicles) {
            if (v.emergency && v.s < v.route.gate + 20) {
                em = v;
                break;
            }
        }
        if (phase == 'G') {
            int q = queue[cur];
            if (em != null) {
                if (em.route.from == cur) pt = Math.min(pt, 1);
                else if (pt > 1) go(em.route.from);
            } else {
                boolean eco = mode == Mode.ECO, other = false;
                for (int i = 0; i < queue.length; i++) if (i != cur && queue[i] > 0) other = true;
                double dur = mode == Mode.FIXED ? 12 : Math.min(eco ? 18 : 26, Math.max(6, (eco ? 5 : 7) + 1.4 * q));
                if (pt > dur || (mode != Mode.FIXED && q == 0 && pt > 3.5 && other)) go(pick());
            }
        } else if (phase == 'Y' && pt > 2.8) {
            phase = 'R';
            pt = 0;
        } else if (phase == 'R') {
            boolean junctionClearing = false;
            if (pt < 4.0) {
                for (Vehicle v : vehicles) {
                    if (!v.isAccident && v.s > v.route.gate - 2.0 && v.s < v.route.length() - v.route.gate + 2.0) {
                        junctionClearing = true;
                        break;
                    }
                }
            }
            double minRed = (em != null ? 1.0 : 2.2);
            if (pt > minRed && !junctionClearing) {
                cur = next;
                phase = 'G';
                pt = 0;
            }
        }
    }

    private boolean ringBusy(Vehicle v) {
        double entryAngle = j.ang[v.route.from];
        for (Vehicle o : vehicles) {
            if (o == v) continue;
            double r = Math.hypot(o.x, o.y);
            if (r < Junction.R - 20 || r > Junction.R + 25) continue;
            double oAngle = Math.atan2(o.y, o.x);
            double dAngle = ((entryAngle - oAngle) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
            if (dAngle > 0.05 && dAngle < 1.3) return true;
        }
        return false;
    }

    // ── Accident Trigger & Emergency Clearance Logic ──

    public boolean triggerAccident() {
        Vehicle cand = null;
        for (Vehicle v : vehicles) {
            if (!v.emergency && !v.isAccident && v.s > 100 && v.s < v.route.gate - 140) {
                cand = v;
                break;
            }
        }
        if (cand == null) return false;

        cand.isAccident = true;
        cand.accidentAngle = (rnd.nextDouble() - 0.5) * 0.45;
        cand.v = 0;

        Accident acc = new Accident(cand, cand.route.from, cand.route.lane, cand.s);
        accidents.add(acc);
        dispatchResponder(acc);
        return true;
    }

    public void dispatchResponder(Accident acc) {
        if (acc.responder != null) return;
        Vehicle.Type respType = rnd.nextBoolean() ? Vehicle.Type.POLICE : Vehicle.Type.AMBULANCE;

        List<Route> rs = new ArrayList<>();
        for (Route r : j.routes) {
            if (r.from == acc.arm) rs.add(r);
        }
        if (rs.isEmpty()) return;

        int fastest = getFastestLane(acc.arm, 0);
        Route chosen = rs.get(0);
        for (Route r : rs) {
            if (r.lane == fastest) {
                chosen = r;
                break;
            }
        }

        double[] p = chosen.at(0);
        for (Vehicle o : vehicles) {
            if (o.route.from == acc.arm) {
                double minClearance = (respType.length + o.length) / 2.0 + SAFE_BUFFER + 8.0;
                if (o.s < minClearance) return;
            }
            if (Math.hypot(o.x - p[0], o.y - p[1]) < (respType.length + o.length) / 2.0 + SAFE_BUFFER + 8.0) return;
        }

        Vehicle resp = new Vehicle(chosen, respType);
        resp.x = p[0]; resp.y = p[1]; resp.hx = p[2]; resp.hy = p[3];
        resp.targetAccident = acc;
        acc.responder = resp;
        vehicles.add(resp);
    }

    // ── Fastest Lane Identification & Safe Lane Changing ──

    public int getFastestLane(int arm, double currentS) {
        int bestLane = 0;
        double bestScore = -1e9;
        for (int lane = 0; lane < j.laneCount; lane++) {
            double score = evaluateLaneScore(arm, lane, currentS);
            if (score > bestScore) {
                bestScore = score;
                bestLane = lane;
            }
        }
        return bestLane;
    }

    private double evaluateLaneScore(int arm, int lane, double currentS) {
        double clearance = 1e9;
        boolean hasAccident = false;
        int count = 0;

        for (Accident a : accidents) {
            if (a.arm == arm && a.lane == lane && a.s > currentS) {
                hasAccident = true;
                clearance = Math.min(clearance, a.s - currentS);
            }
        }

        for (Vehicle o : vehicles) {
            if (o.route.from == arm && o.route.lane == lane && o.s > currentS) {
                count++;
                clearance = Math.min(clearance, o.s - currentS);
            }
        }

        double score = clearance - count * 40.0;
        if (hasAccident) score -= 1500.0;
        return score;
    }

    private boolean isSafeToChangeLane(Vehicle v, int targetLane) {
        for (Vehicle o : vehicles) {
            if (o == v) continue;
            if (o.route.from == v.route.from && o.route.lane == targetLane) {
                double dist = o.s - v.s;
                // Leader in target lane
                if (dist > 0) {
                    double reqGap = (v.length + o.length) / 2.0 + SAFE_BUFFER + Math.max(0, v.v - o.v) * 0.8;
                    if (dist < reqGap) return false;
                }
                // Follower in target lane
                else {
                    double reqGap = (v.length + o.length) / 2.0 + SAFE_BUFFER + Math.max(0, o.v - v.v) * 0.8;
                    if (-dist < reqGap) return false;
                }
            }
        }
        return true;
    }

    /**
     * Determines whether vehicle v has strict right-of-way over vehicle o during crossing conflicts.
     * Guaranteed strictly anti-symmetric: hasRightOfWay(v, o) == !hasRightOfWay(o, v).
     * Eliminates mutual yield deadlocks in intersections.
     */
    public boolean hasRightOfWay(Vehicle v, Vehicle o) {
        if (v.isAccident) return false;
        if (o.isAccident) return true;
        if (v.isClearing) return true;
        if (o.isClearing) return false;

        // Emergency vehicles always have highest priority
        if (v.emergency != o.emergency) {
            return v.emergency;
        }

        // Inside junction vs outside junction
        boolean vInBox = v.s >= v.route.gate - 2.0 && v.s <= v.route.length() - v.route.gate + 2.0;
        boolean oInBox = o.s >= o.route.gate - 2.0 && o.s <= o.route.length() - o.route.gate + 2.0;
        if (vInBox != oInBox) {
            return vInBox;
        }

        // If both inside junction, the one that has traveled further through the junction has right-of-way to exit first
        if (vInBox && oInBox) {
            double vDist = v.s - v.route.gate;
            double oDist = o.s - o.route.gate;
            if (Math.abs(vDist - oDist) > 6.0) {
                return vDist > oDist;
            }
        }

        // Straight movement has priority over turns
        if (v.route.turn == 'S' && o.route.turn != 'S') return true;
        if (v.route.turn != 'S' && o.route.turn == 'S') return false;

        // Active green signal arm has priority over red/yellow/free-left
        if (!j.kind.ring) {
            char sigV = signal(v.route.from);
            char sigO = signal(o.route.from);
            if (sigV == 'G' && sigO != 'G') return true;
            if (sigV != 'G' && sigO == 'G') return false;
        }

        // Deterministic unique ID tie-breaker (the earlier spawned vehicle gets right-of-way)
        return v.id < o.id;
    }

    // ── Main Simulation Step ──

    public void step(double dt) {
        time += dt;
        signals(dt);
        Arrays.fill(queue, 0);
        Arrays.fill(armCount, 0);

        // Update Footpath Sidewalk Pedestrians
        for (FootpathPerson sp : sidewalkPeople) {
            sp.update(dt, j);
        }

        // Spawn Zebra Crossing Pedestrians
        if (pedDensity.spawnMultiplier > 0 && rnd.nextDouble() < rate * dt * 0.35 * pedDensity.spawnMultiplier) {
            int arm = rnd.nextInt(j.dir.length);
            peds.add(new Pedestrian(arm, rnd.nextBoolean() ? 1 : -1));
        }

        // Update Zebra Crossing Pedestrians
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
                if (safe || p.waitTime > 8) p.waiting = false;
            } else {
                p.crossPos += p.direction * dt * 0.35;
                if (p.crossPos < 0 || p.crossPos > 1) it.remove();
            }
        }

        // Random spontaneous minor accidents (rare - realistic frequency)
        if (rnd.nextDouble() < rate * dt * 0.0004 && accidents.isEmpty()) {
            triggerAccident();
        }

        // Handle Active Accidents & Emergency Clearance
        for (Iterator<Accident> it = accidents.iterator(); it.hasNext();) {
            Accident acc = it.next();
            if (acc.responder != null && !acc.cleared) {
                if (acc.responder.route.from == acc.arm) {
                    double dist = Math.abs(acc.responder.s - acc.s);
                    if (dist < 32) {
                        acc.responder.v = 0;
                        acc.responder.isClearing = true;
                        acc.responderArrived = true;
                        acc.clearanceTimer += dt;

                        if (acc.clearanceTimer >= 4.5) {
                            acc.cleared = true;
                            vehicles.remove(acc.victim); // Towed away
                            acc.responder.isClearing = false;
                            acc.responder.targetAccident = null;
                            acc.responder.v = 28;
                            accidentsCleared++;
                            it.remove();
                        }
                    }
                }
            } else if (acc.responder == null && !acc.cleared) {
                dispatchResponder(acc);
            }
        }

        // Spawning new vehicles according to rate
        for (int i = 0; i < queue.length; i++) {
            if (rnd.nextDouble() < rate * dt) spawn(false);
        }

        // Dynamic Lane Changing: Emergency Vehicles & Accident Bypass
        for (Vehicle v : vehicles) {
            if (v.emergency && v.s < v.route.gate - 15) {
                int curLane = v.route.lane;
                int bestLane = getFastestLane(v.route.from, v.s);

                if (bestLane != curLane && isSafeToChangeLane(v, bestLane)) {
                    for (Route cand : j.routes) {
                        if (cand.from == v.route.from && cand.lane == bestLane) {
                            v.route = cand;
                            break;
                        }
                    }
                }
            } else if (!v.emergency && !v.isAccident && v.s < v.route.gate - 30) {
                for (Accident acc : accidents) {
                    if (acc.arm == v.route.from && acc.lane == v.route.lane) {
                        double distToAcc = acc.s - v.s;
                        if (distToAcc > 0 && distToAcc < 110) {
                            for (int altLane = 0; altLane < j.laneCount; altLane++) {
                                if (altLane != v.route.lane && isSafeToChangeLane(v, altLane)) {
                                    for (Route cand : j.routes) {
                                        if (cand.from == v.route.from && cand.lane == altLane) {
                                            v.route = cand;
                                            break;
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

        // Update positions along routes
        for (Vehicle v : vehicles) {
            double[] a = v.route.at(v.s);
            v.x = a[0]; v.y = a[1]; v.hx = a[2]; v.hy = a[3];
            armCount[v.route.from]++;
            if (v.s < v.route.gate + 10 && v.v < 8 && v.route.gate - v.s < 450) {
                queue[v.route.from]++;
            }
        }

        // ── ROBUST COLLISION PHYSICS & CAR-FOLLOWING MODEL ──
        double b = 110 * (.55 + .45 * weather);

        for (Vehicle v : vehicles) {
            if (v.isAccident || v.isClearing) {
                v.v = 0;
                continue;
            }

            double gap = 1e9;

            // 1. Longitudinal Same-Lane Following (Guaranteed exact gap along road path)
            for (Vehicle o : vehicles) {
                if (o == v) continue;

                // Same Arm & Same Lane (prior to junction)
                if (o.route.from == v.route.from && o.route.lane == v.route.lane && o.s > v.s) {
                    double sDist = (o.s - v.s) - (v.length / 2.0 + o.length / 2.0);
                    if (sDist >= -SAFE_BUFFER && sDist < 200) {
                        gap = Math.min(gap, sDist);
                    }
                }
                // Same Route through junction
                else if (o.route == v.route && o.s > v.s) {
                    double sDist = (o.s - v.s) - (v.length / 2.0 + o.length / 2.0);
                    if (sDist >= -SAFE_BUFFER && sDist < 200) {
                        gap = Math.min(gap, sDist);
                    }
                }
                // Same Exit Arm & Same Lane (after junction)
                else if (o.route.to == v.route.to && o.route.lane == v.route.lane
                        && v.s > v.route.length() - v.route.gate && o.s > o.route.length() - o.route.gate
                        && o.s > v.s) {
                    double sDist = (o.s - v.s) - (v.length / 2.0 + o.length / 2.0);
                    if (sDist >= -SAFE_BUFFER && sDist < 200) {
                        gap = Math.min(gap, sDist);
                    }
                }
                // Roundabout Ring Arc Following
                else if (j.kind.ring && v.s >= v.route.gate && o.s >= o.route.gate) {
                    double distV = Math.hypot(v.x, v.y);
                    double distO = Math.hypot(o.x, o.y);
                    if (Math.abs(distV - Junction.R) < 18 && Math.abs(distO - Junction.R) < 18) {
                        double thV = Math.atan2(v.y, v.x);
                        double thO = Math.atan2(o.y, o.x);
                        double dTh = ((thO - thV) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
                        if (dTh > 0 && dTh < 1.2) {
                            double arcGap = dTh * Junction.R - (v.length / 2.0 + o.length / 2.0);
                            gap = Math.min(gap, arcGap);
                        }
                    }
                }
                // 2. Spatial 2D Conflict Detection (Intersections, merges, crossings)
                else {
                    // Exclude parallel lanes on same approach road (before junction)
                    if (o.route.from == v.route.from && o.route.lane != v.route.lane
                            && v.s < v.route.gate + 5 && o.s < o.route.gate + 5) {
                        continue;
                    }

                    // Exclude parallel lanes on same departure road (after junction)
                    double vExitGate = v.route.length() - v.route.gate;
                    double oExitGate = o.route.length() - o.route.gate;
                    if (o.route.to == v.route.to && o.route.lane != v.route.lane
                            && v.s > vExitGate - 5 && o.s > oExitGate - 5) {
                        continue;
                    }

                    // Exclude opposing traffic separated by center median on approach/departure arms
                    if ((v.route.from == o.route.to && v.s < v.route.gate && o.s > oExitGate)
                        || (v.route.to == o.route.from && v.s > vExitGate && o.s < o.route.gate)) {
                        continue;
                    }

                    double dx = o.x - v.x, dy = o.y - v.y;
                    double f = dx * v.hx + dy * v.hy;
                    double lat = Math.abs(-dx * v.hy + dy * v.hx);
                    double combWidth = (v.width + o.width) / 2.0 + 3.0;

                    if (f > 0 && f < 85 && lat < combWidth) {
                        double dotH = v.hx * o.hx + v.hy * o.hy;
                        if (dotH < -0.6) {
                            double oRel = -dx * o.hy + dy * o.hx;
                            if (Math.abs(oRel) > combWidth * 0.7) continue;
                        }

                        // Asymmetric Right-of-Way: exactly ONE vehicle yields, preventing mutual freezing!
                        boolean iHavePriority = hasRightOfWay(v, o);
                        if (iHavePriority) {
                            // v has right-of-way! Only yield if o is an actual stationary obstacle (accident/breakdown)
                            if (o.isAccident || o.breakdown > 0) {
                                double physicalDist = f - (v.length / 2.0 + o.length / 2.0);
                                gap = Math.min(gap, physicalDist);
                            }
                        } else {
                            // v yields to o until o clears the conflict
                            gap = Math.min(gap, f - (v.length / 2.0 + o.length / 2.0));
                        }
                    }
                }
            }

            // ── YELLOW BOX "DON'T BLOCK THE BOX" RULE ──
            // If approaching stop line, do not enter intersection if box is jammed or exit blocked
            double tg = v.route.gate - v.s - v.length / 2.0;
            if (!j.kind.ring && !v.emergency && tg > -2.0 && tg < 32.0) {
                char curSig = signal(v.route.from);
                if (curSig == 'G' || j.freeLeft(v.route)) {
                    boolean boxBlocked = false;
                    double vExitDist = v.route.length() - v.route.gate;
                    for (Vehicle o : vehicles) {
                        if (o == v) continue;
                        boolean oInBox = o.s > o.route.gate + 2.0 && o.s < o.route.length() - o.route.gate - 2.0;
                        if (oInBox && o.v < 6.0) {
                            double distCenter = Math.hypot(o.x, o.y);
                            if (distCenter < j.stop + 8.0) {
                                boxBlocked = true;
                                break;
                            }
                        }
                        if (o.route.to == v.route.to && o.s > vExitDist - 5.0 && o.s < vExitDist + 35.0 && o.v < 4.0) {
                            boxBlocked = true;
                            break;
                        }
                    }
                    if (boxBlocked) {
                        gap = Math.min(gap, Math.max(0.0, tg));
                    }
                }
            }

            // Target gate distance: stop strictly BEFORE the zebra crossing!
            if (tg > -2) {
                if (!j.kind.ring && !j.freeLeft(v.route) && !v.emergency) {
                    char s = signal(v.route.from);
                    if (s != 'G' && !(s == 'Y' && tg < v.v * v.v / (2 * b))) {
                        gap = Math.min(gap, tg);
                    }
                }
                if (j.kind.ring && tg < 45 && !v.emergency && ringBusy(v)) {
                    gap = Math.min(gap, tg);
                }
            }

            // Yield to Pedestrians on Zebra Crossing
            for (Pedestrian p : peds) {
                if (p.arm == v.route.from && !p.waiting) {
                    double distToStop = v.route.gate - v.s - v.length / 2.0;
                    if (distToStop > -8 && distToStop < 90) {
                        gap = Math.min(gap, Math.max(0, distToStop));
                    }
                }
            }

            // Speed calculation using Intelligent Driver Model with safe buffer
            double vmax = v.type.maxSpeed * weather * (v.emergency ? 1.25 : 1.0);
            double rc = Math.hypot(v.x, v.y);
            double jExit = v.route.length() - v.route.gate;
            boolean insideJunction = (v.s > v.route.gate - 8.0 && v.s < jExit + 8.0);

            // Anti-Deadlock Creep: If stuck inside intersection for > 1.5s, force creep forward to clear box
            if (insideJunction && v.still > 1.5 && !v.isAccident && !v.isClearing) {
                gap = Math.max(gap, 40.0);
            }

            if (v.route.turn != 'S' && rc < j.stop + 25) vmax = Math.min(vmax, 55 * weather);

            double targetSpeed;
            if (gap <= SAFE_BUFFER) {
                targetSpeed = 0;
                if (insideJunction && v.still > 1.5 && !v.isAccident && !v.isClearing) {
                    targetSpeed = 22.0 * weather; // creep to evacuate junction box!
                    v.v = Math.max(v.v, 14.0 * weather);
                }
            } else {
                double effectiveGap = gap - SAFE_BUFFER;
                targetSpeed = Math.min(vmax, Math.sqrt(2 * b * effectiveGap));
            }

            if (v.breakdown > 0) { targetSpeed = 0; v.breakdown -= dt; }

            // Apply acceleration / deceleration limits
            double maxAcc = v.type.accel * dt;
            double maxDec = -v.type.decel * dt;
            v.v = Math.max(0, v.v + Math.max(maxDec, Math.min(maxAcc, targetSpeed - v.v)));
        }

        // Advance vehicle distance with HARD NON-PENETRATION RESOLUTION
        for (Iterator<Vehicle> it = vehicles.iterator(); it.hasNext();) {
            Vehicle v = it.next();
            if (v.isAccident || v.isClearing) {
                continue;
            }

            double plannedS = v.s + v.v * dt;

            // Enforce hard non-penetration against immediate leader in same lane
            for (Vehicle o : vehicles) {
                if (o == v) continue;
                if ((o.route.from == v.route.from && o.route.lane == v.route.lane && o.s > v.s)
                    || (o.route == v.route && o.s > v.s)) {
                    double maxAllowedS = o.s - (o.length + v.length) / 2.0 - SAFE_BUFFER;
                    if (plannedS > maxAllowedS) {
                        plannedS = Math.max(v.s, maxAllowedS);
                        v.v = Math.min(v.v, o.v);
                    }
                }
            }

            v.s = plannedS;

            if (v.v < 2) {
                v.wait += dt;
                v.still += dt;
                co2 += 0.4 * v.type.co2Rate * dt;
            } else {
                v.still = 0;
                co2 += v.type.co2Rate * dt;
            }

            if (v.s >= v.route.length()) {
                out++;
                sumWait += v.wait;
                it.remove();
            }
        }
    }
}
