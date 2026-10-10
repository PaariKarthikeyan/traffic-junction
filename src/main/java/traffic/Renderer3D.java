package traffic;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

/**
 * High-performance 3D Isometric & Perspective Renderer for Java AWT/Swing.
 * Renders multiple vehicle varieties (Bike, Taxi, Truck, Bus, Cycle, Fire Truck, Police, Ambulance, Car),
 * footpath sidewalk community life with walking people, dog walkers, and conversing groups,
 * accident scenes with hazard smoke and warning cones,
 * and stop lines positioned strictly BEFORE the zebra crossing.
 */
public class Renderer3D {

    public enum CameraPreset {
        ISOMETRIC("3D Isometric", -Math.PI / 4.0, Math.toRadians(36.0), 1.05),
        ANGLED("3D Cinematic Angle", -Math.PI / 6.0, Math.toRadians(46.0), 1.15),
        TOP_DOWN("Top-Down 2D", 0.0, Math.toRadians(90.0), 0.95);

        public final String label;
        public final double yaw, pitch, zoom;
        CameraPreset(String label, double yaw, double pitch, double zoom) {
            this.label = label;
            this.yaw = yaw;
            this.pitch = pitch;
            this.zoom = zoom;
        }
    }

    // ── Camera State ──
    public double yaw = CameraPreset.ISOMETRIC.yaw;
    public double pitch = CameraPreset.ISOMETRIC.pitch;
    public double zoom = CameraPreset.ISOMETRIC.zoom;
    public double panX = 0;
    public double panY = 0;
    public boolean autoOrbit = false;

    // ── Mouse interaction state ──
    private int lastMouseX, lastMouseY;
    private int dragButton = 0;

    // ── Sun directional lighting vector (normalized) ──
    private static final double[] SUN = {-0.48, -0.56, 0.67};

    // ── Curated Color Palette ──
    private static final Color GRASS_TOP       = new Color(42, 125, 68);
    private static final Color DIORAMA_DIRT    = new Color(74, 52, 38);
    private static final Color DIORAMA_STONE   = new Color(52, 48, 45);
    private static final Color ROAD_BASE       = new Color(44, 48, 56);
    private static final Color FOOTPATH_PAVE   = new Color(175, 182, 194);
    private static final Color ROAD_CURB_TOP   = new Color(195, 202, 214);
    private static final Color ROAD_CURB_SIDE  = new Color(125, 132, 145);
    private static final Color MARKING_YELLOW  = new Color(255, 205, 50);
    private static final Color MARKING_WHITE   = new Color(245, 248, 255, 210);
    private static final Color ZEBRA_WHITE     = new Color(250, 250, 255);
    private static final Color GLASS_COLOR     = new Color(90, 180, 230, 200);
    private static final Color SHADOW_COLOR    = new Color(10, 15, 25, 120);

    // ── Scenery Cache ──
    private Junction cachedJunction = null;
    private final List<SceneryBuilding> cachedBuildings = new ArrayList<>();
    private final List<SceneryTree> cachedTrees = new ArrayList<>();
    private final List<StreetLamp> cachedStreetLamps = new ArrayList<>();

    // ── HUD Buttons ──
    private final List<HudButton> hudButtons = new ArrayList<>();

    public Renderer3D() {
        initHudButtons();
    }

    private void initHudButtons() {
        hudButtons.clear();
        int btnW = 86, btnH = 24, spacing = 6;
        int bx = 20, by = 48;

        hudButtons.add(new HudButton(bx, by, btnW, btnH, "3D Iso", () -> setPreset(CameraPreset.ISOMETRIC)));
        bx += btnW + spacing;
        hudButtons.add(new HudButton(bx, by, btnW, btnH, "3D Angle", () -> setPreset(CameraPreset.ANGLED)));
        bx += btnW + spacing;
        hudButtons.add(new HudButton(bx, by, btnW, btnH, "Top-Down", () -> setPreset(CameraPreset.TOP_DOWN)));
        bx += btnW + spacing;
        hudButtons.add(new HudButton(bx, by, btnW, btnH, "Orbit: OFF", () -> toggleOrbit()));
        bx += btnW + spacing;
        hudButtons.add(new HudButton(bx, by, btnW, btnH, "Reset Cam", () -> resetCamera()));
    }

    public void setPreset(CameraPreset preset) {
        this.yaw = preset.yaw;
        this.pitch = preset.pitch;
        this.zoom = preset.zoom;
        this.panX = 0;
        this.panY = 0;
        this.autoOrbit = false;
        updateOrbitButtonText();
    }

    public void toggleOrbit() {
        autoOrbit = !autoOrbit;
        updateOrbitButtonText();
    }

    private void updateOrbitButtonText() {
        for (HudButton b : hudButtons) {
            if (b.label.startsWith("Orbit:")) {
                b.label = "Orbit: " + (autoOrbit ? "ON" : "OFF");
            }
        }
    }

    public void resetCamera() {
        setPreset(CameraPreset.ISOMETRIC);
    }

    public void attachControls(JComponent canvas) {
        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastMouseX = e.getX();
                lastMouseY = e.getY();
                dragButton = e.getButton();

                for (HudButton btn : hudButtons) {
                    if (btn.contains(e.getX(), e.getY())) {
                        btn.action.run();
                        canvas.repaint();
                        return;
                    }
                }
                canvas.requestFocusInWindow();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragButton = 0;
            }
        });

        canvas.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                int dx = e.getX() - lastMouseX;
                int dy = e.getY() - lastMouseY;
                lastMouseX = e.getX();
                lastMouseY = e.getY();

                if (SwingUtilities.isRightMouseButton(e) || (dragButton == MouseEvent.BUTTON3)) {
                    panX += dx;
                    panY += dy;
                } else {
                    yaw += dx * 0.007;
                    pitch += dy * 0.007;
                    pitch = Math.max(Math.toRadians(12.0), Math.min(Math.toRadians(90.0), pitch));
                }
                canvas.repaint();
            }
        });

        canvas.addMouseWheelListener(e -> {
            double notches = e.getPreciseWheelRotation();
            double factor = 1.0 - notches * 0.1;
            zoom = Math.max(0.4, Math.min(3.2, zoom * factor));
            canvas.repaint();
        });
    }

    // ── 3D Projection Math ──

    public static class ScreenPt {
        public double sx, sy, depth;
        public ScreenPt(double sx, double sy, double depth) {
            this.sx = sx; this.sy = sy; this.depth = depth;
        }
    }

    public ScreenPt project(double x, double y, double z, double cx, double cy, double scale) {
        double cosY = Math.cos(yaw);
        double sinY = Math.sin(yaw);
        double rx = x * cosY - y * sinY;
        double ry = x * sinY + y * cosY;

        double cosP = Math.cos(pitch);
        double sinP = Math.sin(pitch);

        double s = scale * zoom;
        double sx = cx + panX + rx * s;
        double sy = cy + panY + (-ry * sinP - z * cosP) * s;
        double depth = ry * cosP - z * sinP;

        return new ScreenPt(sx, sy, depth);
    }

    public Color shadeColor(Color c, double factor) {
        int r = Math.min(255, Math.max(0, (int) (c.getRed() * factor)));
        int g = Math.min(255, Math.max(0, (int) (c.getGreen() * factor)));
        int b = Math.min(255, Math.max(0, (int) (c.getBlue() * factor)));
        return new Color(r, g, b, c.getAlpha());
    }

    public double computeShading(double nx, double ny, double nz) {
        double dot = nx * SUN[0] + ny * SUN[1] + nz * SUN[2];
        return Math.max(0.42, Math.min(1.28, 0.76 + 0.46 * dot));
    }

    // ── Main Render Entry Point ──

    public void render(Graphics2D g, int width, int height, Simulation sim) {
        if (autoOrbit) {
            yaw += 0.004;
        }

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        double cx = width / 2.0;
        double cy = height / 2.0 + 20.0;
        double baseScale = Math.min(width, height) / 1520.0;
        Junction j = sim.j;

        if (cachedJunction != j) {
            rebuildScenery(j);
            cachedJunction = j;
        }

        // 1. Draw Sky / Ground Background
        drawBackground(g, width, height);

        // 2. Draw 3D Floating Diorama Slab
        drawDioramaBase(g, cx, cy, baseScale);

        // 3. Draw Roads, Footpaths, Curbs, Markings (Ground level)
        drawRoadsAndFootpaths(g, sim, cx, cy, baseScale);

        // 4. Gather all 3D elevated entities for Depth Sorting (Painter's Algorithm)
        List<Renderable3D> depthList = new ArrayList<>();

        // Add Scenery (Buildings, Trees, Street Lamps)
        for (SceneryBuilding b : cachedBuildings) depthList.add(b.createRenderable(this, cx, cy, baseScale));
        for (SceneryTree t : cachedTrees) depthList.add(t.createRenderable(this, cx, cy, baseScale));
        for (StreetLamp l : cachedStreetLamps) depthList.add(l.createRenderable(this, cx, cy, baseScale));

        // Add Traffic Signals
        if (!j.kind.ring) {
            int hw = j.laneCount * 22;
            double stopDist = j.stop + Junction.STOP_LINE_OFFSET;
            for (int i = 0; i < j.dir.length; i++) {
                final int armIdx = i;
                double[] A = j.dir[i];
                double[] O = {-A[1], A[0]};
                double px = A[0] * stopDist + O[0] * (hw + 8);
                double py = A[1] * stopDist + O[1] * (hw + 8);
                ScreenPt pt = project(px, py, 0, cx, cy, baseScale);

                depthList.add(new Renderable3D(pt.depth, (g2) -> {
                    drawTrafficSignal3D(g2, sim, armIdx, px, py, j.ang[armIdx], hw, cx, cy, baseScale);
                }));
            }
        }

        // Add Zebra Crossing Pedestrians
        for (Pedestrian p : sim.peds) {
            double[] A = j.dir[p.arm];
            double[] O = {-A[1], A[0]};
            double crossDist = j.stop + Junction.CROSSING_OFFSET;
            double crossY = (p.crossPos - 0.5) * (j.laneCount * 44);
            double wx = A[0] * crossDist + O[0] * crossY;
            double wy = A[1] * crossDist + O[1] * crossY;
            ScreenPt pt = project(wx, wy, 0, cx, cy, baseScale);

            depthList.add(new Renderable3D(pt.depth, (g2) -> {
                drawPedestrian3D(g2, p, wx, wy, cx, cy, baseScale);
            }));
        }

        // Add Footpath Sidewalk Community Life (Walkers, Dog Walkers, Conversing Groups)
        for (FootpathPerson sp : sim.sidewalkPeople) {
            ScreenPt pt = project(sp.x, sp.y, 0, cx, cy, baseScale);
            depthList.add(new Renderable3D(pt.depth, (g2) -> {
                drawFootpathPerson3D(g2, sp, cx, cy, baseScale);
            }));
        }

        // Add Vehicles
        for (Vehicle v : sim.vehicles) {
            ScreenPt pt = project(v.x, v.y, 0, cx, cy, baseScale);
            depthList.add(new Renderable3D(pt.depth, (g2) -> {
                drawVehicle3D(g2, v, cx, cy, baseScale);
            }));
        }

        // Sort descending by depth: objects with largest depth are furthest away, rendered first!
        depthList.sort((o1, o2) -> Double.compare(o2.depth, o1.depth));

        // Render elevated 3D objects back-to-front
        for (Renderable3D r : depthList) {
            r.render(g);
        }

        // 5. Draw Modern Glassmorphic HUD & Compass
        drawHUD(g, width, height, sim);
    }

    // ── Background & Diorama Base ──

    private void drawBackground(Graphics2D g, int w, int h) {
        GradientPaint gp = new GradientPaint(
            0, 0, new Color(18, 26, 44),
            0, h, new Color(10, 14, 26)
        );
        g.setPaint(gp);
        g.fillRect(0, 0, w, h);
    }

    private void drawDioramaBase(Graphics2D g, double cx, double cy, double scale) {
        int sides = 24;
        double radius = Junction.ARM + 75;
        double zTop = 0.0;
        double zBottom = -45.0;

        Point2D.Double[] perimeter = new Point2D.Double[sides];
        for (int i = 0; i < sides; i++) {
            double angle = i * 2.0 * Math.PI / sides;
            perimeter[i] = new Point2D.Double(radius * Math.cos(angle), radius * Math.sin(angle));
        }

        for (int i = 0; i < sides; i++) {
            int next = (i + 1) % sides;
            Point2D.Double p1 = perimeter[i];
            Point2D.Double p2 = perimeter[next];

            ScreenPt t1 = project(p1.x, p1.y, zTop, cx, cy, scale);
            ScreenPt t2 = project(p2.x, p2.y, zTop, cx, cy, scale);
            ScreenPt b1 = project(p1.x, p1.y, zBottom, cx, cy, scale);
            ScreenPt b2 = project(p2.x, p2.y, zBottom, cx, cy, scale);

            double dx = p2.x - p1.x;
            double dy = p2.y - p1.y;
            double len = Math.hypot(dx, dy);
            double nx = dy / len;
            double ny = -dx / len;

            double signedArea = (t1.sx * (t2.sy - b1.sy) + t2.sx * (b2.sy - t1.sy)
                               + b2.sx * (b1.sy - t2.sy) + b1.sx * (t1.sy - b2.sy)) * 0.5;
            if (signedArea < 0 || pitch >= Math.toRadians(88)) {
                double shade = computeShading(nx, ny, -0.1);
                Color cliffColor = shadeColor(DIORAMA_DIRT, shade);

                Path2D path = new Path2D.Double();
                path.moveTo(t1.sx, t1.sy);
                path.lineTo(t2.sx, t2.sy);
                path.lineTo(b2.sx, b2.sy);
                path.lineTo(b1.sx, b1.sy);
                path.closePath();

                g.setColor(cliffColor);
                g.fill(path);
                g.setColor(shadeColor(DIORAMA_STONE, shade * 0.85));
                g.draw(path);
            }
        }

        Path2D topPoly = new Path2D.Double();
        ScreenPt p0 = project(perimeter[0].x, perimeter[0].y, zTop, cx, cy, scale);
        topPoly.moveTo(p0.sx, p0.sy);
        for (int i = 1; i < sides; i++) {
            ScreenPt pt = project(perimeter[i].x, perimeter[i].y, zTop, cx, cy, scale);
            topPoly.lineTo(pt.sx, pt.sy);
        }
        topPoly.closePath();

        double topShade = computeShading(0, 0, 1.0);
        g.setColor(shadeColor(GRASS_TOP, topShade));
        g.fill(topPoly);

        g.setColor(new Color(255, 255, 255, 12));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(topPoly);
    }

    // ── Roads, Footpaths, Curbs, Markings ──

    private static class RoadCorner {
        final double x, y;
        RoadCorner(double x, double y) { this.x = x; this.y = y; }
    }

    private RoadCorner computeRoadCorner(Junction j, int i, int next, double hw) {
        double[] A = j.dir[i];
        double[] B = j.dir[next];
        double[] OA = {-A[1], A[0]};
        double[] OB = {-B[1], B[0]};

        // Left edge of arm i: point PA = OA * hw, direction A
        double pAx = OA[0] * hw, pAy = OA[1] * hw;
        // Right edge of arm next: point PB = -OB * hw, direction B
        double pBx = -OB[0] * hw, pBy = -OB[1] * hw;

        double det = -(A[0] * B[1] - A[1] * B[0]);
        if (Math.abs(det) < 1e-4) {
            // Collinear opposite arms (e.g. 180 deg in 3-way T-junction)
            return new RoadCorner(pAx, pAy);
        }

        double tA = ((pBx - pAx) * (-B[1]) - (pBy - pAy) * (-B[0])) / det;
        return new RoadCorner(pAx + tA * A[0], pAy + tA * A[1]);
    }

    private void drawRoadsAndFootpaths(Graphics2D g, Simulation sim, double cx, double cy, double scale) {
        Junction j = sim.j;
        int laneW = 22;
        int hw = j.laneCount * laneW;
        double armLen = Junction.ARM + 50;
        double curbW = 4.0;
        double curbH = 3.0;
        double footW = 20.0;

        if (j.kind.ring) {
            // ── ROUNDABOUT ROAD & ISLAND ──
            double rIn = Junction.R - 18.0;   // 42.0 inner island radius
            double rOut = Junction.R + 22.0;  // 82.0 outer road ring radius

            // 1. Central Raised Island with stone curb and grass top
            drawRaisedIsland(g, 0, 0, rIn, 4.5, cx, cy, scale);

            // 2. Continuous Circular Ring Road (Asphalt between rIn and rOut)
            drawAsphaltRing(g, 0, 0, rIn, rOut, 0.4, ROAD_BASE, cx, cy, scale);

            // 3. Radial Arms & Outer Perimeter Curbs
            int n = j.dir.length;
            for (int i = 0; i < n; i++) {
                double[] A = j.dir[i];
                double[] O = {-A[1], A[0]};

                // Road Slab from rOut to armLen
                double p1x = A[0] * (rOut - 2.0) - O[0] * hw;
                double p1y = A[1] * (rOut - 2.0) - O[1] * hw;
                double p2x = A[0] * armLen - O[0] * hw;
                double p2y = A[1] * armLen - O[1] * hw;
                double p3x = A[0] * armLen + O[0] * hw;
                double p3y = A[1] * armLen + O[1] * hw;
                double p4x = A[0] * (rOut - 2.0) + O[0] * hw;
                double p4y = A[1] * (rOut - 2.0) + O[1] * hw;

                fillQuad3D(g, p1x, p1y, 0.4, p2x, p2y, 0.4, p3x, p3y, 0.4, p4x, p4y, 0.4,
                    ROAD_BASE, 0, 0, 1.0, cx, cy, scale);

                // Arm Curbs & Footpaths from rOut outward
                drawRoadCurb3D(g, A[0] * rOut - O[0] * hw, A[1] * rOut - O[1] * hw,
                                  p2x, p2y, -O[0], -O[1], curbW, curbH, cx, cy, scale);
                drawPavedFootpath(g, A[0] * rOut - O[0] * (hw + curbW), A[1] * rOut - O[1] * (hw + curbW),
                                     p2x - O[0] * curbW, p2y - O[1] * curbW,
                                     -O[0], -O[1], footW, curbH * 0.9, cx, cy, scale);

                drawRoadCurb3D(g, A[0] * rOut + O[0] * hw, A[1] * rOut + O[1] * hw,
                                  p3x, p3y, O[0], O[1], curbW, curbH, cx, cy, scale);
                drawPavedFootpath(g, A[0] * rOut + O[0] * (hw + curbW), A[1] * rOut + O[1] * (hw + curbW),
                                     p3x + O[0] * curbW, p3y + O[1] * curbW,
                                     O[0], O[1], footW, curbH * 0.9, cx, cy, scale);

                // Circular outer curb & footpath between arm i and arm (i+1)
                int next = (i + 1) % n;
                double a1 = j.ang[i] + Math.asin(Math.min(0.9, (hw + curbW) / rOut));
                double a2 = j.ang[next] - Math.asin(Math.min(0.9, (hw + curbW) / rOut));
                while (a2 <= a1) a2 += 2 * Math.PI;
                drawCurvedCurbAndFootpath(g, 0, 0, rOut, a1, a2, curbW, curbH, footW, cx, cy, scale);
            }
        } else {
            // ── STANDARD INTERSECTION (4-WAY, 3-WAY T, Y, 5-WAY) ──
            int n = j.dir.length;
            RoadCorner[] corners = new RoadCorner[n];
            for (int i = 0; i < n; i++) {
                int next = (i + 1) % n;
                corners[i] = computeRoadCorner(j, i, next, hw);
            }

            // 1. Center Intersection Asphalt Polygon
            Path2D centerPoly = new Path2D.Double();
            for (int i = 0; i < n; i++) {
                ScreenPt pt = project(corners[i].x, corners[i].y, 0.4, cx, cy, scale);
                if (i == 0) centerPoly.moveTo(pt.sx, pt.sy);
                else centerPoly.lineTo(pt.sx, pt.sy);
            }
            centerPoly.closePath();
            double asphaltShade = computeShading(0, 0, 1.0);
            g.setColor(shadeColor(ROAD_BASE, asphaltShade));
            g.fill(centerPoly);

            // 2. Each Arm Road Slab (from origin outward to armLen)
            for (int i = 0; i < n; i++) {
                double[] A = j.dir[i];
                double[] O = {-A[1], A[0]};

                double p1x = -O[0] * hw, p1y = -O[1] * hw;
                double p2x = A[0] * armLen - O[0] * hw, p2y = A[1] * armLen - O[1] * hw;
                double p3x = A[0] * armLen + O[0] * hw, p3y = A[1] * armLen + O[1] * hw;
                double p4x = O[0] * hw, p4y = O[1] * hw;

                fillQuad3D(g, p1x, p1y, 0.4, p2x, p2y, 0.4, p3x, p3y, 0.4, p4x, p4y, 0.4,
                    ROAD_BASE, 0, 0, 1.0, cx, cy, scale);
            }

            // 3. Curbs & Footpaths along every arm and meeting seamlessly at corners
            for (int i = 0; i < n; i++) {
                double[] A = j.dir[i];
                double[] O = {-A[1], A[0]};
                int next = (i + 1) % n;
                int prev = (i - 1 + n) % n;

                RoadCorner cLeft = corners[i];      // corner between arm i and arm next
                RoadCorner cRight = corners[prev];   // corner between arm prev and arm i

                double endLx = A[0] * armLen + O[0] * hw, endLy = A[1] * armLen + O[1] * hw;
                double endRx = A[0] * armLen - O[0] * hw, endRy = A[1] * armLen - O[1] * hw;

                // Left curb & footpath (from cLeft outward along arm i)
                drawRoadCurb3D(g, cLeft.x, cLeft.y, endLx, endLy, O[0], O[1], curbW, curbH, cx, cy, scale);
                drawPavedFootpath(g, cLeft.x + O[0] * curbW, cLeft.y + O[1] * curbW,
                                     endLx + O[0] * curbW, endLy + O[1] * curbW,
                                     O[0], O[1], footW, curbH * 0.9, cx, cy, scale);

                // Right curb & footpath (from cRight outward along arm i)
                drawRoadCurb3D(g, cRight.x, cRight.y, endRx, endRy, -O[0], -O[1], curbW, curbH, cx, cy, scale);
                drawPavedFootpath(g, cRight.x - O[0] * curbW, cRight.y - O[1] * curbW,
                                     endRx - O[0] * curbW, endRy - O[1] * curbW,
                                     -O[0], -O[1], footW, curbH * 0.9, cx, cy, scale);
            }
        }

        // ── Road Markings: Center Divider, Lane Dividers, Zebra Crossing & Stop Line ──
        for (int i = 0; i < j.dir.length; i++) {
            double[] A = j.dir[i];
            double[] O = {-A[1], A[0]};

            double markStart = j.stop + Junction.STOP_LINE_OFFSET;
            double markEnd = armLen;
            drawCenterDoubleLine(g, A, O, markStart, markEnd, cx, cy, scale);

            for (int k = 1; k < j.laneCount; k++) {
                double off = k * laneW;
                drawDashedLaneLine(g, A, O, off, markStart, markEnd, cx, cy, scale);
                drawDashedLaneLine(g, A, O, -off, markStart, markEnd, cx, cy, scale);
            }

            // 1. Zebra Crossing at stop + CROSSING_OFFSET
            double crossDist = j.stop + Junction.CROSSING_OFFSET;
            drawZebraCrossing3D(g, A, O, crossDist, hw, cx, cy, scale);

            // 2. Stop Line at stop + STOP_LINE_OFFSET (Strictly before zebra crossing!)
            double stopDist = j.stop + Junction.STOP_LINE_OFFSET;
            double slx1 = A[0] * stopDist;
            double sly1 = A[1] * stopDist;
            double slx2 = A[0] * stopDist + O[0] * hw;
            double sly2 = A[1] * stopDist + O[1] * hw;
            drawLineQuad3D(g, slx1, sly1, slx2, sly2, 5.0, 0.8, MARKING_WHITE, cx, cy, scale);
        }
    }

    private void drawPavedFootpath(Graphics2D g, double x1, double y1, double x2, double y2,
                                   double ox, double oy, double width, double height,
                                   double cx, double cy, double scale) {
        double ox1 = x1 + ox * width, oy1 = y1 + oy * width;
        double ox2 = x2 + ox * width, oy2 = y2 + oy * width;

        // Paved Walkway Top Surface
        fillQuad3D(g, x1, y1, height, x2, y2, height, ox2, oy2, height, ox1, oy1, height,
            FOOTPATH_PAVE, 0, 0, 1.0, cx, cy, scale);

        // Outer Curb Face with guaranteed outward winding
        double dx = ox2 - ox1, dy = oy2 - oy1;
        double cross = dx * oy - dy * ox;
        double sx1 = (cross < 0) ? ox1 : ox2;
        double sy1 = (cross < 0) ? oy1 : oy2;
        double sx2 = (cross < 0) ? ox2 : ox1;
        double sy2 = (cross < 0) ? oy2 : oy1;

        fillQuad3D(g, sx1, sy1, 0, sx2, sy2, 0, sx2, sy2, height, sx1, sy1, height,
            ROAD_CURB_SIDE, ox, oy, 0, cx, cy, scale);
    }

    private void drawRoadCurb3D(Graphics2D g, double x1, double y1, double x2, double y2,
                                double ox, double oy, double width, double height,
                                double cx, double cy, double scale) {
        double ox1 = x1 + ox * width, oy1 = y1 + oy * width;
        double ox2 = x2 + ox * width, oy2 = y2 + oy * width;

        // Ensure vertical face winding is counter-clockwise relative to outward normal
        double dx = x2 - x1, dy = y2 - y1;
        double cross = dx * oy - dy * ox;
        double sx1 = (cross < 0) ? x1 : x2;
        double sy1 = (cross < 0) ? y1 : y2;
        double sx2 = (cross < 0) ? x2 : x1;
        double sy2 = (cross < 0) ? oy2 : oy1;

        fillQuad3D(g, sx1, sy1, 0, sx2, sy2, 0, sx2, sy2, height, sx1, sy1, height,
            ROAD_CURB_SIDE, ox, oy, 0, cx, cy, scale);

        fillQuad3D(g, x1, y1, height, x2, y2, height, ox2, oy2, height, ox1, oy1, height,
            ROAD_CURB_TOP, 0, 0, 1.0, cx, cy, scale);
    }

    private void drawAsphaltRing(Graphics2D g, double cx3d, double cy3d, double rIn, double rOut, double z, Color c,
                                 double cx, double cy, double scale) {
        int steps = 36;
        for (int i = 0; i < steps; i++) {
            double a1 = i * 2.0 * Math.PI / steps;
            double a2 = (i + 1) * 2.0 * Math.PI / steps;

            double p1x = cx3d + rIn * Math.cos(a1),  p1y = cy3d + rIn * Math.sin(a1);
            double p2x = cx3d + rOut * Math.cos(a1), p2y = cy3d + rOut * Math.sin(a1);
            double p3x = cx3d + rOut * Math.cos(a2), p3y = cy3d + rOut * Math.sin(a2);
            double p4x = cx3d + rIn * Math.cos(a2),  p4y = cy3d + rIn * Math.sin(a2);

            fillQuad3D(g, p1x, p1y, z, p2x, p2y, z, p3x, p3y, z, p4x, p4y, z,
                c, 0, 0, 1.0, cx, cy, scale);
        }
    }

    private void drawCurvedCurbAndFootpath(Graphics2D g, double cx3d, double cy3d, double r,
                                           double aStart, double aEnd, double curbW, double curbH,
                                           double footW, double cx, double cy, double scale) {
        int steps = Math.max(3, (int) Math.ceil(Math.abs(aEnd - aStart) / (Math.PI / 14.0)));
        double dAng = (aEnd - aStart) / steps;
        for (int s = 0; s < steps; s++) {
            double a1 = aStart + s * dAng;
            double a2 = aStart + (s + 1) * dAng;

            double midA = (a1 + a2) / 2.0;
            double ox = Math.cos(midA), oy = Math.sin(midA);

            double x1 = cx3d + r * Math.cos(a1), y1 = cy3d + r * Math.sin(a1);
            double x2 = cx3d + r * Math.cos(a2), y2 = cy3d + r * Math.sin(a2);

            drawRoadCurb3D(g, x1, y1, x2, y2, ox, oy, curbW, curbH, cx, cy, scale);
            drawPavedFootpath(g, x1 + ox * curbW, y1 + oy * curbW,
                                 x2 + ox * curbW, y2 + oy * curbW,
                                 ox, oy, footW, curbH * 0.9, cx, cy, scale);
        }
    }

    private void drawRaisedIsland(Graphics2D g, double x, double y, double r, double height,
                                  double cx, double cy, double scale) {
        int steps = 28;
        Point2D.Double[] pts = new Point2D.Double[steps];
        for (int i = 0; i < steps; i++) {
            double ang = i * 2.0 * Math.PI / steps;
            pts[i] = new Point2D.Double(x + r * Math.cos(ang), y + r * Math.sin(ang));
        }

        for (int i = 0; i < steps; i++) {
            int next = (i + 1) % steps;
            Point2D.Double p1 = pts[i];
            Point2D.Double p2 = pts[next];

            double nx = (p1.x + p2.x) / (2 * r);
            double ny = (p1.y + p2.y) / (2 * r);
            fillQuad3D(g, p1.x, p1.y, 0, p2.x, p2.y, 0, p2.x, p2.y, height, p1.x, p1.y, height,
                ROAD_CURB_SIDE, nx, ny, 0, cx, cy, scale);
        }

        Path2D top = new Path2D.Double();
        for (int i = 0; i < steps; i++) {
            ScreenPt pt = project(pts[i].x, pts[i].y, height, cx, cy, scale);
            if (i == 0) top.moveTo(pt.sx, pt.sy);
            else top.lineTo(pt.sx, pt.sy);
        }
        top.closePath();

        double shade = computeShading(0, 0, 1.0);
        g.setColor(shadeColor(GRASS_TOP, shade));
        g.fill(top);

        // Center garden ornamental ring
        int gSteps = 20;
        Path2D garden = new Path2D.Double();
        double gr = r * 0.5;
        for (int i = 0; i < gSteps; i++) {
            double ang = i * 2.0 * Math.PI / gSteps;
            ScreenPt pt = project(x + gr * Math.cos(ang), y + gr * Math.sin(ang), height + 0.3, cx, cy, scale);
            if (i == 0) garden.moveTo(pt.sx, pt.sy);
            else garden.lineTo(pt.sx, pt.sy);
        }
        garden.closePath();
        g.setColor(shadeColor(new Color(36, 110, 60), shade));
        g.fill(garden);
    }

    private void drawCenterDoubleLine(Graphics2D g, double[] A, double[] O, double s1, double s2,
                                      double cx, double cy, double scale) {
        double off = 1.6;
        for (double o : new double[]{-off, off}) {
            double x1 = A[0] * s1 + O[0] * o;
            double y1 = A[1] * s1 + O[1] * o;
            double x2 = A[0] * s2 + O[0] * o;
            double y2 = A[1] * s2 + O[1] * o;
            drawLineQuad3D(g, x1, y1, x2, y2, 1.8, 0.7, MARKING_YELLOW, cx, cy, scale);
        }
    }

    private void drawDashedLaneLine(Graphics2D g, double[] A, double[] O, double offset,
                                    double s1, double s2, double cx, double cy, double scale) {
        double dashLen = 14.0, gapLen = 12.0;
        double s = s1;
        while (s < s2) {
            double end = Math.min(s2, s + dashLen);
            double x1 = A[0] * s + O[0] * offset;
            double y1 = A[1] * s + O[1] * offset;
            double x2 = A[0] * end + O[0] * offset;
            double y2 = A[1] * end + O[1] * offset;
            drawLineQuad3D(g, x1, y1, x2, y2, 1.4, 0.6, MARKING_WHITE, cx, cy, scale);
            s += dashLen + gapLen;
        }
    }

    private void drawZebraCrossing3D(Graphics2D g, double[] A, double[] O, double crossDist,
                                     double hw, double cx, double cy, double scale) {
        double stripeWidth = 6.0;
        double stripeLength = 12.0;
        double step = 8.5;

        for (double off = -hw + 4; off < hw - 4; off += step) {
            double xCenter = A[0] * crossDist + O[0] * off;
            double yCenter = A[1] * crossDist + O[1] * off;

            double dx = A[0] * (stripeLength / 2.0);
            double dy = A[1] * (stripeLength / 2.0);
            double wx = O[0] * (stripeWidth / 2.0);
            double wy = O[1] * (stripeWidth / 2.0);

            fillQuad3D(g,
                xCenter - dx - wx, yCenter - dy - wy, 0.7,
                xCenter + dx - wx, yCenter + dy - wy, 0.7,
                xCenter + dx + wx, yCenter + dy + wy, 0.7,
                xCenter - dx + wx, yCenter - dy + wy, 0.7,
                ZEBRA_WHITE, 0, 0, 1.0, cx, cy, scale);
        }
    }

    // ── 3D Vehicle Varieties (Car, Bike, Cycle, Taxi, Bus, Truck, Police, Ambulance, Fire Truck) ──

    private void drawVehicle3D(Graphics2D g, Vehicle v, double cx, double cy, double scale) {
        double vx = v.x, vy = v.y;
        double hx = v.hx, hy = v.hy;
        double heading = Math.atan2(hy, hx) + (v.isAccident ? v.accidentAngle : 0);
        double len = v.length;
        double wid = v.width;
        double px = -Math.sin(heading), py = Math.cos(heading);
        double fwdX = Math.cos(heading), fwdY = Math.sin(heading);

        // Soft Oval Shadow
        drawVehicleShadow(g, vx, vy, heading, len + 4, wid + 4, cx, cy, scale);

        // Headlight Beams
        if (!v.isAccident) {
            drawHeadlightBeams(g, vx, vy, fwdX, fwdY, px, py, len, wid, cx, cy, scale);
        }

        // Draw model according to Vehicle.Type
        switch (v.type) {
            case CYCLE:
                drawCycle3D(g, v, vx, vy, fwdX, fwdY, px, py, heading, cx, cy, scale);
                break;
            case BIKE:
                drawBike3D(g, v, vx, vy, fwdX, fwdY, px, py, heading, cx, cy, scale);
                break;
            case TAXI:
                drawTaxi3D(g, v, vx, vy, fwdX, fwdY, px, py, heading, len, wid, cx, cy, scale);
                break;
            case BUS:
                drawBus3D(g, v, vx, vy, fwdX, fwdY, px, py, heading, len, wid, cx, cy, scale);
                break;
            case TRUCK:
                drawTruck3D(g, v, vx, vy, fwdX, fwdY, px, py, heading, len, wid, cx, cy, scale);
                break;
            case POLICE:
                drawPolice3D(g, v, vx, vy, fwdX, fwdY, px, py, heading, len, wid, cx, cy, scale);
                break;
            case AMBULANCE:
                drawAmbulance3D(g, v, vx, vy, fwdX, fwdY, px, py, heading, len, wid, cx, cy, scale);
                break;
            case FIRE_TRUCK:
                drawFireTruck3D(g, v, vx, vy, fwdX, fwdY, px, py, heading, len, wid, cx, cy, scale);
                break;
            case CAR:
            default:
                drawCar3D(g, v, vx, vy, fwdX, fwdY, px, py, heading, len, wid, cx, cy, scale);
                break;
        }

        // Accident visuals (Hazard smoke, warning cones, clearing responders)
        if (v.isAccident) {
            drawAccidentScene3D(g, v, vx, vy, fwdX, fwdY, px, py, len, wid, cx, cy, scale);
        }
    }

    private void drawCar3D(Graphics2D g, Vehicle v, double vx, double vy,
                           double hx, double hy, double px, double py,
                           double heading, double len, double wid,
                           double cx, double cy, double scale) {
        Color bodyColor = v.customColor != null ? v.customColor : new Color(41, 128, 240);

        // Wheels
        draw4Wheels(g, vx, vy, hx, hy, px, py, heading, len, wid, 2.4, 1.3, 4.0, cx, cy, scale);

        // Lower Chassis Body
        double chassisH = 5.2;
        drawBox3D(g, vx, vy, 1.8, len, wid, chassisH, heading,
            bodyColor, shadeColor(bodyColor, 0.8), cx, cy, scale);

        // Cabin Roof
        double cabinLen = len * 0.58, cabinWid = wid * 0.82, cabinH = 5.0;
        double cabX = vx - hx * (len * 0.06), cabY = vy - hy * (len * 0.06);
        drawBox3D(g, cabX, cabY, 1.8 + chassisH, cabinLen, cabinWid, cabinH, heading,
            bodyColor, shadeColor(bodyColor, 0.75), cx, cy, scale);

        // Windows
        drawCabinWindows(g, cabX, cabY, 1.8 + chassisH, cabinLen, cabinWid, cabinH, hx, hy, px, py, cx, cy, scale);

        // Lamps
        drawVehicleLamps(g, v, vx, vy, hx, hy, px, py, len, wid, cx, cy, scale);
    }

    private void drawTaxi3D(Graphics2D g, Vehicle v, double vx, double vy,
                            double hx, double hy, double px, double py,
                            double heading, double len, double wid,
                            double cx, double cy, double scale) {
        Color taxiYellow = new Color(251, 192, 45);

        draw4Wheels(g, vx, vy, hx, hy, px, py, heading, len, wid, 2.4, 1.3, 4.0, cx, cy, scale);

        // Body
        double chassisH = 5.2;
        drawBox3D(g, vx, vy, 1.8, len, wid, chassisH, heading,
            taxiYellow, shadeColor(taxiYellow, 0.8), cx, cy, scale);

        // Cabin
        double cabinLen = len * 0.58, cabinWid = wid * 0.82, cabinH = 5.0;
        double cabX = vx - hx * (len * 0.06), cabY = vy - hy * (len * 0.06);
        drawBox3D(g, cabX, cabY, 1.8 + chassisH, cabinLen, cabinWid, cabinH, heading,
            taxiYellow, shadeColor(taxiYellow, 0.75), cx, cy, scale);

        drawCabinWindows(g, cabX, cabY, 1.8 + chassisH, cabinLen, cabinWid, cabinH, hx, hy, px, py, cx, cy, scale);

        // Illuminated "TAXI" Roof Pod
        drawBox3D(g, cabX, cabY, 1.8 + chassisH + cabinH, 4.0, 7.0, 2.2, heading,
            new Color(255, 255, 230), new Color(40, 40, 40), cx, cy, scale);

        drawVehicleLamps(g, v, vx, vy, hx, hy, px, py, len, wid, cx, cy, scale);
    }

    private void drawBike3D(Graphics2D g, Vehicle v, double vx, double vy,
                            double hx, double hy, double px, double py,
                            double heading, double cx, double cy, double scale) {
        // 2 Motorcycle Wheels (Front & Rear)
        double wheelDist = 4.5;
        double wx1 = vx + hx * wheelDist, wy1 = vy + hy * wheelDist;
        double wx2 = vx - hx * wheelDist, wy2 = vy - hy * wheelDist;
        drawBox3D(g, wx1, wy1, 0.6, 2.2, 1.2, 4.0, heading, new Color(30, 30, 30), new Color(15, 15, 15), cx, cy, scale);
        drawBox3D(g, wx2, wy2, 0.6, 2.2, 1.2, 4.0, heading, new Color(30, 30, 30), new Color(15, 15, 15), cx, cy, scale);

        // Motorcycle Frame & Gas Tank
        Color bikeColor = new Color(220, 40, 40);
        drawBox3D(g, vx, vy, 2.2, 8.0, 3.2, 3.5, heading, bikeColor, shadeColor(bikeColor, 0.75), cx, cy, scale);

        // Rider (Torso + Helmet)
        ScreenPt hip = project(vx - hx * 0.8, vy - hy * 0.8, 5.0, cx, cy, scale);
        ScreenPt shoulder = project(vx, vy, 9.5, cx, cy, scale);
        ScreenPt head = project(vx + hx * 0.5, vy + hy * 0.5, 12.0, cx, cy, scale);

        g.setColor(new Color(40, 45, 60)); // Rider leather jacket
        g.setStroke(new BasicStroke(3.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine((int) hip.sx, (int) hip.sy, (int) shoulder.sx, (int) shoulder.sy);

        // Helmet
        g.setColor(new Color(245, 245, 250));
        g.fillOval((int) (head.sx - 3), (int) (head.sy - 3), 6, 6);

        // Headlight
        ScreenPt lampPt = project(vx + hx * 5.0, vy + hy * 5.0, 3.8, cx, cy, scale);
        g.setColor(new Color(255, 255, 210));
        g.fillOval((int) (lampPt.sx - 2), (int) (lampPt.sy - 2), 4, 4);
    }

    private void drawCycle3D(Graphics2D g, Vehicle v, double vx, double vy,
                             double hx, double hy, double px, double py,
                             double heading, double cx, double cy, double scale) {
        double dist = 3.6;
        double fx = vx + hx * dist, fy = vy + hy * dist;
        double rx = vx - hx * dist, ry = vy - hy * dist;

        // Slender 2 Bicycle Wheels
        drawBox3D(g, fx, fy, 0.5, 1.8, 0.6, 3.8, heading, new Color(40, 40, 40), new Color(20, 20, 20), cx, cy, scale);
        drawBox3D(g, rx, ry, 0.5, 1.8, 0.6, 3.8, heading, new Color(40, 40, 40), new Color(20, 20, 20), cx, cy, scale);

        // Thin Frame
        ScreenPt fHub = project(fx, fy, 2.0, cx, cy, scale);
        ScreenPt rHub = project(rx, ry, 2.0, cx, cy, scale);
        ScreenPt seat = project(vx - hx * 0.5, vy - hy * 0.5, 4.5, cx, cy, scale);
        ScreenPt bars = project(vx + hx * 2.2, vy + hy * 2.2, 5.0, cx, cy, scale);

        g.setColor(new Color(0, 180, 216)); // Cyan bike frame
        g.setStroke(new BasicStroke(1.8f));
        g.drawLine((int) fHub.sx, (int) fHub.sy, (int) bars.sx, (int) bars.sy);
        g.drawLine((int) rHub.sx, (int) rHub.sy, (int) seat.sx, (int) seat.sy);
        g.drawLine((int) seat.sx, (int) seat.sy, (int) bars.sx, (int) bars.sy);

        // Cyclist Pedaling
        ScreenPt head = project(vx, vy, 10.5, cx, cy, scale);
        g.setColor(new Color(255, 112, 67)); // Orange jersey
        g.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine((int) seat.sx, (int) seat.sy, (int) head.sx, (int) head.sy);

        // Cyclist Helmet
        g.setColor(new Color(255, 238, 88));
        g.fillOval((int) (head.sx - 2), (int) (head.sy - 2), 5, 5);
    }

    private void drawBus3D(Graphics2D g, Vehicle v, double vx, double vy,
                           double hx, double hy, double px, double py,
                           double heading, double len, double wid,
                           double cx, double cy, double scale) {
        Color busBlue = new Color(30, 90, 175);

        // 6 Heavy Wheels
        double[] fxs = {len * 0.38, -len * 0.25, -len * 0.38};
        for (double fx : fxs) {
            for (double side : new double[]{-0.52 * wid, 0.52 * wid}) {
                double wx = vx + hx * fx + px * side;
                double wy = vy + hy * fx + py * side;
                drawBox3D(g, wx, wy, 0.8, 3.2, 1.6, 5.0, heading, new Color(25, 25, 25), new Color(15, 15, 15), cx, cy, scale);
            }
        }

        // Large Bus Body
        double busHeight = 11.5;
        drawBox3D(g, vx, vy, 1.8, len, wid, busHeight, heading,
            busBlue, shadeColor(busBlue, 0.8), cx, cy, scale);

        // Continuous Glass Window Band along sides
        double winZ = 1.8 + busHeight * 0.45;
        double winH = busHeight * 0.4;
        fillQuad3D(g,
            vx + hx * (len * 0.45) + px * (wid * 0.51), vy + hy * (len * 0.45) + py * (wid * 0.51), winZ,
            vx - hx * (len * 0.45) + px * (wid * 0.51), vy - hy * (len * 0.45) + py * (wid * 0.51), winZ,
            vx - hx * (len * 0.45) + px * (wid * 0.51), vy - hy * (len * 0.45) + py * (wid * 0.51), winZ + winH,
            vx + hx * (len * 0.45) + px * (wid * 0.51), vy + hy * (len * 0.45) + py * (wid * 0.51), winZ + winH,
            GLASS_COLOR, px, py, 0, cx, cy, scale);

        // Front Windshield & Destination LED Display
        fillQuad3D(g,
            vx + hx * (len * 0.51) - px * (wid * 0.45), vy + hy * (len * 0.51) - py * (wid * 0.45), winZ,
            vx + hx * (len * 0.51) + px * (wid * 0.45), vy + hy * (len * 0.51) + py * (wid * 0.45), winZ,
            vx + hx * (len * 0.51) + px * (wid * 0.45), vy + hy * (len * 0.51) + py * (wid * 0.45), winZ + winH,
            vx + hx * (len * 0.51) - px * (wid * 0.45), vy + hy * (len * 0.51) - py * (wid * 0.45), winZ + winH,
            GLASS_COLOR, hx, hy, 0, cx, cy, scale);

        // LED Route Banner
        ScreenPt destPt = project(vx + hx * (len * 0.51), vy + hy * (len * 0.51), winZ + winH + 1.2, cx, cy, scale);
        g.setColor(new Color(255, 193, 7));
        g.fillRoundRect((int) (destPt.sx - 6), (int) (destPt.sy - 2), 12, 4, 2, 2);

        drawVehicleLamps(g, v, vx, vy, hx, hy, px, py, len, wid, cx, cy, scale);
    }

    private void drawTruck3D(Graphics2D g, Vehicle v, double vx, double vy,
                             double hx, double hy, double px, double py,
                             double heading, double len, double wid,
                             double cx, double cy, double scale) {
        // Front Cab (Driver cabin)
        double cabLen = len * 0.32;
        double cabX = vx + hx * (len * 0.34);
        double cabY = vy + hy * (len * 0.34);
        Color cabColor = new Color(225, 60, 40);
        drawBox3D(g, cabX, cabY, 1.8, cabLen, wid, 9.5, heading,
            cabColor, shadeColor(cabColor, 0.8), cx, cy, scale);

        // Rear Cargo Container
        double cargoLen = len * 0.62;
        double cargoX = vx - hx * (len * 0.16);
        double cargoY = vy - hy * (len * 0.16);
        Color cargoColor = new Color(235, 238, 245);
        drawBox3D(g, cargoX, cargoY, 2.5, cargoLen, wid * 1.05, 11.0, heading,
            cargoColor, shadeColor(cargoColor, 0.85), cx, cy, scale);

        // Wheels
        double[] fxs = {len * 0.35, -len * 0.22, -len * 0.40};
        for (double fx : fxs) {
            for (double side : new double[]{-0.52 * wid, 0.52 * wid}) {
                double wx = vx + hx * fx + px * side;
                double wy = vy + hy * fx + py * side;
                drawBox3D(g, wx, wy, 0.8, 3.2, 1.6, 5.0, heading, new Color(25, 25, 25), new Color(15, 15, 15), cx, cy, scale);
            }
        }

        drawVehicleLamps(g, v, vx, vy, hx, hy, px, py, len, wid, cx, cy, scale);
    }

    private void drawPolice3D(Graphics2D g, Vehicle v, double vx, double vy,
                              double hx, double hy, double px, double py,
                              double heading, double len, double wid,
                              double cx, double cy, double scale) {
        Color policeBlack = new Color(30, 34, 42);
        Color policeWhite = new Color(245, 248, 252);

        draw4Wheels(g, vx, vy, hx, hy, px, py, heading, len, wid, 2.4, 1.3, 4.0, cx, cy, scale);

        // Body
        double chassisH = 5.2;
        drawBox3D(g, vx, vy, 1.8, len, wid, chassisH, heading,
            policeBlack, shadeColor(policeBlack, 0.8), cx, cy, scale);

        // White Door Inserts & White Roof
        double cabinLen = len * 0.58, cabinWid = wid * 0.82, cabinH = 5.0;
        double cabX = vx - hx * (len * 0.06), cabY = vy - hy * (len * 0.06);
        drawBox3D(g, cabX, cabY, 1.8 + chassisH, cabinLen, cabinWid, cabinH, heading,
            policeWhite, shadeColor(policeWhite, 0.8), cx, cy, scale);

        drawCabinWindows(g, cabX, cabY, 1.8 + chassisH, cabinLen, cabinWid, cabinH, hx, hy, px, py, cx, cy, scale);

        // Police Siren Lightbar
        drawEmergencyLightbar(g, cabX, cabY, 1.8 + chassisH + cabinH, hx, hy, px, py, cx, cy, scale);

        drawVehicleLamps(g, v, vx, vy, hx, hy, px, py, len, wid, cx, cy, scale);
    }

    private void drawAmbulance3D(Graphics2D g, Vehicle v, double vx, double vy,
                                 double hx, double hy, double px, double py,
                                 double heading, double len, double wid,
                                 double cx, double cy, double scale) {
        Color ambWhite = new Color(250, 252, 255);

        draw4Wheels(g, vx, vy, hx, hy, px, py, heading, len, wid, 2.6, 1.4, 4.5, cx, cy, scale);

        // High-roof Ambulance Van Box
        double ambHeight = 9.8;
        drawBox3D(g, vx, vy, 1.8, len, wid, ambHeight, heading,
            ambWhite, shadeColor(ambWhite, 0.85), cx, cy, scale);

        // Bold Red Cross Emblem on Roof & Sides
        ScreenPt roofPt = project(vx - hx * 2.0, vy - hy * 2.0, 1.8 + ambHeight + 0.2, cx, cy, scale);
        g.setColor(new Color(230, 30, 30));
        g.fillRect((int) (roofPt.sx - 6), (int) (roofPt.sy - 2), 12, 4);
        g.fillRect((int) (roofPt.sx - 2), (int) (roofPt.sy - 6), 4, 12);

        // Flashing Emergency Siren
        drawEmergencyLightbar(g, vx + hx * (len * 0.2), vy + hy * (len * 0.2), 1.8 + ambHeight, hx, hy, px, py, cx, cy, scale);

        drawVehicleLamps(g, v, vx, vy, hx, hy, px, py, len, wid, cx, cy, scale);
    }

    private void drawFireTruck3D(Graphics2D g, Vehicle v, double vx, double vy,
                                 double hx, double hy, double px, double py,
                                 double heading, double len, double wid,
                                 double cx, double cy, double scale) {
        Color fireRed = new Color(210, 30, 30);

        // 6 Heavy Wheels
        double[] fxs = {len * 0.36, -len * 0.24, -len * 0.38};
        for (double fx : fxs) {
            for (double side : new double[]{-0.52 * wid, 0.52 * wid}) {
                double wx = vx + hx * fx + px * side;
                double wy = vy + hy * fx + py * side;
                drawBox3D(g, wx, wy, 0.8, 3.2, 1.6, 5.0, heading, new Color(25, 25, 25), new Color(15, 15, 15), cx, cy, scale);
            }
        }

        // Heavy Fire Engine Body
        double ftHeight = 11.0;
        drawBox3D(g, vx, vy, 1.8, len, wid, ftHeight, heading,
            fireRed, shadeColor(fireRed, 0.8), cx, cy, scale);

        // Silver Extension Ladder on Roof
        double ladLen = len * 0.65;
        drawBox3D(g, vx - hx * (len * 0.1), vy - hy * (len * 0.1), 1.8 + ftHeight, ladLen, wid * 0.45, 1.8, heading,
            new Color(220, 225, 235), new Color(150, 155, 165), cx, cy, scale);

        // Dual Intense Flashing Sirens
        drawEmergencyLightbar(g, vx + hx * (len * 0.32), vy + hy * (len * 0.32), 1.8 + ftHeight, hx, hy, px, py, cx, cy, scale);

        drawVehicleLamps(g, v, vx, vy, hx, hy, px, py, len, wid, cx, cy, scale);
    }

    private void drawAccidentScene3D(Graphics2D g, Vehicle v, double vx, double vy,
                                     double hx, double hy, double px, double py,
                                     double len, double wid, double cx, double cy, double scale) {
        // 1. Hazard Warning Flashers
        boolean flash = ((System.currentTimeMillis() / 250) % 2 == 0);
        if (flash) {
            Color amberGlow = new Color(255, 170, 0, 180);
            ScreenPt pFront = project(vx + hx * (len * 0.5), vy + hy * (len * 0.5), 4.5, cx, cy, scale);
            ScreenPt pRear  = project(vx - hx * (len * 0.5), vy - hy * (len * 0.5), 4.5, cx, cy, scale);
            g.setColor(amberGlow);
            g.fillOval((int) (pFront.sx - 8), (int) (pFront.sy - 8), 16, 16);
            g.fillOval((int) (pRear.sx - 8), (int) (pRear.sy - 8), 16, 16);
        }

        // 2. Animated Smoke Plume from Hood
        long t = System.currentTimeMillis();
        for (int k = 0; k < 4; k++) {
            double phase = ((t + k * 350) % 1400) / 1400.0;
            double smokeZ = 5.0 + phase * 22.0;
            double driftX = Math.sin(phase * 4.0 + k) * 3.0;
            double driftY = Math.cos(phase * 4.0 + k) * 3.0;
            ScreenPt sPt = project(vx + hx * (len * 0.4) + driftX, vy + hy * (len * 0.4) + driftY, smokeZ, cx, cy, scale);

            int alpha = (int) (Math.max(0, 140 * (1.0 - phase)));
            g.setColor(new Color(60, 65, 75, alpha));
            int r = (int) (6 + phase * 16);
            g.fillOval((int) (sPt.sx - r / 2), (int) (sPt.sy - r / 2), r, r);
        }

        // 3. Orange Highway Warning Cones behind accident
        for (int c = 1; c <= 2; c++) {
            double coneX = vx - hx * (len * 0.6 + c * 10.0) + px * (c == 1 ? -2.5 : 2.5);
            double coneY = vy - hy * (len * 0.6 + c * 10.0) + py * (c == 1 ? -2.5 : 2.5);
            drawBox3D(g, coneX, coneY, 0.4, 2.0, 2.0, 3.8, 0,
                new Color(255, 109, 0), new Color(200, 70, 0), cx, cy, scale);
        }
    }

    private void draw4Wheels(Graphics2D g, double vx, double vy, double hx, double hy,
                             double px, double py, double heading,
                             double len, double wid, double r, double w, double h,
                             double cx, double cy, double scale) {
        double[] fx = {0.33 * len, -0.33 * len};
        double[] fy = {0.52 * wid, -0.52 * wid};
        for (double fxPos : fx) {
            for (double fyPos : fy) {
                double wx = vx + hx * fxPos + px * fyPos;
                double wy = vy + hy * fxPos + py * fyPos;
                drawBox3D(g, wx, wy, 0.8, r * 1.8, w, h,
                    heading, new Color(30, 32, 36), new Color(20, 22, 25), cx, cy, scale);
            }
        }
    }

    private void drawVehicleShadow(Graphics2D g, double vx, double vy, double heading,
                                   double len, double wid, double cx, double cy, double scale) {
        int steps = 14;
        Path2D p = new Path2D.Double();
        double cosH = Math.cos(heading), sinH = Math.sin(heading);
        double a = len / 2.0, b = wid / 2.0;

        for (int i = 0; i < steps; i++) {
            double theta = i * 2.0 * Math.PI / steps;
            double ex = a * Math.cos(theta);
            double ey = b * Math.sin(theta);
            double wx = vx + ex * cosH - ey * sinH + 1.5;
            double wy = vy + ex * sinH + ey * cosH + 1.5;
            ScreenPt pt = project(wx, wy, 0.3, cx, cy, scale);
            if (i == 0) p.moveTo(pt.sx, pt.sy);
            else p.lineTo(pt.sx, pt.sy);
        }
        p.closePath();
        g.setColor(SHADOW_COLOR);
        g.fill(p);
    }

    private void drawHeadlightBeams(Graphics2D g, double vx, double vy, double hx, double hy,
                                    double px, double py, double len, double wid,
                                    double cx, double cy, double scale) {
        double beamLen = 42.0;
        double beamSpread = 16.0;
        double frontX = vx + hx * (len / 2.0);
        double frontY = vy + hy * (len / 2.0);

        for (double side : new double[]{-0.32, 0.32}) {
            double fx = frontX + px * (wid * side);
            double fy = frontY + py * (wid * side);
            double end1X = fx + hx * beamLen - px * (beamSpread * 0.4);
            double end1Y = fy + hy * beamLen - py * (beamSpread * 0.4);
            double end2X = fx + hx * beamLen + px * (beamSpread * 0.4);
            double end2Y = fy + hy * beamLen + py * (beamSpread * 0.4);

            ScreenPt sp0 = project(fx, fy, 0.5, cx, cy, scale);
            ScreenPt sp1 = project(end1X, end1Y, 0.5, cx, cy, scale);
            ScreenPt sp2 = project(end2X, end2Y, 0.5, cx, cy, scale);

            Path2D cone = new Path2D.Double();
            cone.moveTo(sp0.sx, sp0.sy);
            cone.lineTo(sp1.sx, sp1.sy);
            cone.lineTo(sp2.sx, sp2.sy);
            cone.closePath();

            g.setColor(new Color(255, 255, 210, 32));
            g.fill(cone);
        }
    }

    private void drawCabinWindows(Graphics2D g, double cx3d, double cy3d, double cz,
                                  double len, double wid, double height,
                                  double hx, double hy, double px, double py,
                                  double cx, double cy, double scale) {
        double fwX = cx3d + hx * (len * 0.48);
        double fwY = cy3d + hy * (len * 0.48);
        double hw = wid * 0.42;

        fillQuad3D(g,
            fwX - px * hw, fwY - py * hw, cz + 0.6,
            fwX + px * hw, fwY + py * hw, cz + 0.6,
            fwX + px * hw, fwY + py * hw, cz + height - 0.4,
            fwX - px * hw, fwY - py * hw, cz + height - 0.4,
            GLASS_COLOR, hx, hy, 0.4, cx, cy, scale);

        double rwX = cx3d - hx * (len * 0.48);
        double rwY = cy3d - hy * (len * 0.48);
        fillQuad3D(g,
            rwX + px * hw, rwY + py * hw, cz + 0.6,
            rwX - px * hw, rwY - py * hw, cz + 0.6,
            rwX - px * hw, rwY - py * hw, cz + height - 0.4,
            rwX + px * hw, rwY + py * hw, cz + height - 0.4,
            GLASS_COLOR, -hx, -hy, 0.4, cx, cy, scale);
    }

    private void drawVehicleLamps(Graphics2D g, Vehicle v, double vx, double vy,
                                  double hx, double hy, double px, double py,
                                  double len, double wid, double cx, double cy, double scale) {
        double lampZ = 4.2;
        double fx = vx + hx * (len / 2.0);
        double fy = vy + hy * (len / 2.0);

        for (double side : new double[]{-0.34, 0.34}) {
            double lx = fx + px * (wid * side);
            double ly = fy + py * (wid * side);
            ScreenPt spt = project(lx, ly, lampZ, cx, cy, scale);
            g.setColor(new Color(255, 255, 230));
            g.fillOval((int) (spt.sx - 2), (int) (spt.sy - 2), 4, 4);
        }

        double rx = vx - hx * (len / 2.0);
        double ry = vy - hy * (len / 2.0);
        boolean braking = (v.v < 5.0);
        Color tailColor = braking ? new Color(255, 30, 30) : new Color(175, 20, 20);

        for (double side : new double[]{-0.34, 0.34}) {
            double lx = rx + px * (wid * side);
            double ly = ry + py * (wid * side);
            ScreenPt spt = project(lx, ly, lampZ, cx, cy, scale);
            g.setColor(tailColor);
            g.fillOval((int) (spt.sx - 2), (int) (spt.sy - 2), 4, 4);

            if (braking) {
                g.setColor(new Color(255, 40, 40, 90));
                g.fillOval((int) (spt.sx - 5), (int) (spt.sy - 5), 10, 10);
            }
        }
    }

    private void drawEmergencyLightbar(Graphics2D g, double cx3d, double cy3d, double cz,
                                       double hx, double hy, double px, double py,
                                       double cx, double cy, double scale) {
        boolean flashRed = ((System.currentTimeMillis() / 150) % 2 == 0);

        drawBox3D(g, cx3d, cy3d, cz, 3.5, 7.5, 2.0, Math.atan2(hy, hx),
            new Color(20, 22, 25), new Color(15, 17, 20), cx, cy, scale);

        double lx = cx3d + px * 2.4, ly = cy3d + py * 2.4;
        double rx = cx3d - px * 2.4, ry = cy3d - py * 2.4;

        ScreenPt lPt = project(lx, ly, cz + 2.0, cx, cy, scale);
        ScreenPt rPt = project(rx, ry, cz + 2.0, cx, cy, scale);

        Color leftColor = flashRed ? new Color(255, 40, 40) : new Color(120, 0, 0);
        Color rightColor = !flashRed ? new Color(40, 140, 255) : new Color(0, 30, 120);

        g.setColor(leftColor);
        g.fillOval((int) (lPt.sx - 3), (int) (lPt.sy - 3), 6, 6);
        g.setColor(rightColor);
        g.fillOval((int) (rPt.sx - 3), (int) (rPt.sy - 3), 6, 6);

        if (flashRed) {
            g.setColor(new Color(255, 30, 30, 110));
            g.fillOval((int) (lPt.sx - 9), (int) (lPt.sy - 9), 18, 18);
        } else {
            g.setColor(new Color(40, 150, 255, 110));
            g.fillOval((int) (rPt.sx - 9), (int) (rPt.sy - 9), 18, 18);
        }
    }

    // ── 3D Traffic Signals ──

    private void drawTrafficSignal3D(Graphics2D g, Simulation sim, int armIdx,
                                     double x, double y, double armAng, int hw,
                                     double cx, double cy, double scale) {
        char sig = sim.signal(armIdx);
        double poleHeight = 28.0;

        drawBox3D(g, x, y, 0, 3.2, 3.2, poleHeight, armAng,
            new Color(60, 65, 75), new Color(45, 48, 55), cx, cy, scale);

        double armLen = 14.0;
        double ax = x - Math.sin(armAng) * (armLen / 2.0);
        double ay = y + Math.cos(armAng) * (armLen / 2.0);
        drawBox3D(g, ax, ay, poleHeight - 2.5, 2.4, armLen, 2.4, armAng,
            new Color(55, 60, 70), new Color(40, 44, 52), cx, cy, scale);

        double boxX = x - Math.sin(armAng) * 6.0;
        double boxY = y + Math.cos(armAng) * 6.0;
        double boxZ = poleHeight - 11.0;
        drawBox3D(g, boxX, boxY, boxZ, 4.5, 4.0, 11.0, armAng,
            new Color(20, 22, 26), new Color(15, 17, 20), cx, cy, scale);

        double[] lampZ = {boxZ + 8.5, boxZ + 5.5, boxZ + 2.5};
        Color[] unlitColors = {new Color(70, 0, 0), new Color(70, 60, 0), new Color(0, 55, 15)};
        Color[] litColors   = {new Color(255, 35, 35), new Color(255, 215, 0), new Color(0, 245, 100)};

        int activeIdx = (sig == 'R' ? 0 : sig == 'Y' ? 1 : 2);

        for (int l = 0; l < 3; l++) {
            ScreenPt lpt = project(boxX, boxY, lampZ[l], cx, cy, scale);
            boolean lit = (l == activeIdx);
            Color lampColor = lit ? litColors[l] : unlitColors[l];

            g.setColor(lampColor);
            g.fillOval((int) (lpt.sx - 3), (int) (lpt.sy - 3), 6, 6);

            if (lit) {
                Color glow = new Color(lampColor.getRed(), lampColor.getGreen(), lampColor.getBlue(), 90);
                g.setColor(glow);
                g.fillOval((int) (lpt.sx - 8), (int) (lpt.sy - 8), 16, 16);

                ScreenPt roadPt = project(boxX, boxY, 0.8, cx, cy, scale);
                Color roadGlow = new Color(lampColor.getRed(), lampColor.getGreen(), lampColor.getBlue(), 35);
                g.setColor(roadGlow);
                g.fillOval((int) (roadPt.sx - 12), (int) (roadPt.sy - 8), 24, 16);
            }
        }
    }

    // ── 3D Pedestrians (Zebra Crossing) ──

    private void drawPedestrian3D(Graphics2D g, Pedestrian p, double x, double y,
                                  double cx, double cy, double scale) {
        double time = System.currentTimeMillis() / 100.0;
        double bobbing = p.waiting ? 0 : Math.sin(time * 1.5) * 0.8;

        ScreenPt footPt = project(x, y, 0.4, cx, cy, scale);
        g.setColor(SHADOW_COLOR);
        g.fillOval((int) (footPt.sx - 3), (int) (footPt.sy - 2), 6, 4);

        double legOffset = p.waiting ? 0 : Math.sin(time) * 1.8;
        ScreenPt lFoot = project(x - legOffset, y, 0, cx, cy, scale);
        ScreenPt rFoot = project(x + legOffset, y, 0, cx, cy, scale);
        ScreenPt hip = project(x, y, 4.5 + bobbing, cx, cy, scale);

        g.setColor(new Color(40, 45, 60));
        g.setStroke(new BasicStroke(1.8f));
        g.drawLine((int) hip.sx, (int) hip.sy, (int) lFoot.sx, (int) lFoot.sy);
        g.drawLine((int) hip.sx, (int) hip.sy, (int) rFoot.sx, (int) rFoot.sy);

        Color shirtColor = p.waiting ? new Color(230, 50, 50) : new Color(40, 140, 240);
        ScreenPt shoulder = project(x, y, 8.5 + bobbing, cx, cy, scale);
        g.setColor(shirtColor);
        g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine((int) hip.sx, (int) hip.sy, (int) shoulder.sx, (int) shoulder.sy);

        ScreenPt head = project(x, y, 10.5 + bobbing, cx, cy, scale);
        g.setColor(new Color(255, 218, 185));
        g.fillOval((int) (head.sx - 3), (int) (head.sy - 3), 6, 6);
    }

    // ── 3D Footpath People (Walkers, Dog Walkers, Conversing Groups) ──

    private void drawFootpathPerson3D(Graphics2D g, FootpathPerson sp,
                                      double cx, double cy, double scale) {
        double time = System.currentTimeMillis() / 100.0;
        boolean walking = (sp.activity == FootpathPerson.Activity.WALKING || sp.activity == FootpathPerson.Activity.WALKING_DOG);
        double bobbing = walking ? Math.sin(time * 1.2) * 0.7 : Math.sin(time * 0.4) * 0.2;

        // Shadow at feet
        ScreenPt footPt = project(sp.x, sp.y, 3.4, cx, cy, scale);
        g.setColor(SHADOW_COLOR);
        g.fillOval((int) (footPt.sx - 3), (int) (footPt.sy - 2), 6, 4);

        // Legs
        double legOffset = walking ? Math.sin(time * 1.1) * 1.6 : 0;
        ScreenPt lFoot = project(sp.x - legOffset, sp.y, 3.2, cx, cy, scale);
        ScreenPt rFoot = project(sp.x + legOffset, sp.y, 3.2, cx, cy, scale);
        ScreenPt hip = project(sp.x, sp.y, 6.8 + bobbing, cx, cy, scale);

        g.setColor(new Color(45, 52, 70));
        g.setStroke(new BasicStroke(1.8f));
        g.drawLine((int) hip.sx, (int) hip.sy, (int) lFoot.sx, (int) lFoot.sy);
        g.drawLine((int) hip.sx, (int) hip.sy, (int) rFoot.sx, (int) rFoot.sy);

        // Torso
        ScreenPt shoulder = project(sp.x, sp.y, 10.5 + bobbing, cx, cy, scale);
        g.setColor(sp.clothesColor != null ? sp.clothesColor : new Color(41, 128, 185));
        g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine((int) hip.sx, (int) hip.sy, (int) shoulder.sx, (int) shoulder.sy);

        // Conversing Gesture animation
        if (sp.activity == FootpathPerson.Activity.CONVERSING) {
            double gArm = Math.sin(time * 0.6 + sp.talkGestureTimer) * 1.8;
            ScreenPt hand = project(sp.x + gArm, sp.y + gArm, 9.0 + bobbing, cx, cy, scale);
            g.setColor(sp.clothesColor);
            g.setStroke(new BasicStroke(1.6f));
            g.drawLine((int) shoulder.sx, (int) shoulder.sy, (int) hand.sx, (int) hand.sy);
        }

        // Head
        ScreenPt head = project(sp.x, sp.y, 12.2 + bobbing, cx, cy, scale);
        g.setColor(new Color(255, 218, 185));
        g.fillOval((int) (head.sx - 3), (int) (head.sy - 3), 6, 6);

        // Pet Dog on Leash
        if (sp.activity == FootpathPerson.Activity.WALKING_DOG && sp.dogColor != null) {
            double dogOffsetX = Math.cos(sp.facingAngle + 0.5) * 5.5;
            double dogOffsetY = Math.sin(sp.facingAngle + 0.5) * 5.5;
            double dogX = sp.x + dogOffsetX;
            double dogY = sp.y + dogOffsetY;

            // Leash Line
            ScreenPt dogCollar = project(dogX, dogY, 4.8, cx, cy, scale);
            g.setColor(new Color(210, 50, 50));
            g.setStroke(new BasicStroke(1.0f));
            g.drawLine((int) shoulder.sx, (int) shoulder.sy, (int) dogCollar.sx, (int) dogCollar.sy);

            // 3D Dog Model (Torso, 4 legs, head, wagging tail)
            drawDog3D(g, dogX, dogY, sp.facingAngle, sp.dogColor, cx, cy, scale);
        }
    }

    private void drawDog3D(Graphics2D g, double x, double y, double angle, Color dogColor,
                           double cx, double cy, double scale) {
        double time = System.currentTimeMillis() / 100.0;
        double tailWag = Math.sin(time * 3.0) * 1.8;

        // Shadow
        ScreenPt dFoot = project(x, y, 3.3, cx, cy, scale);
        g.setColor(SHADOW_COLOR);
        g.fillOval((int) (dFoot.sx - 4), (int) (dFoot.sy - 2), 8, 4);

        // Body
        drawBox3D(g, x, y, 3.4, 4.5, 2.4, 2.6, angle,
            dogColor, shadeColor(dogColor, 0.8), cx, cy, scale);

        // Head
        double hX = x + Math.cos(angle) * 2.8;
        double hY = y + Math.sin(angle) * 2.8;
        ScreenPt dHead = project(hX, hY, 5.8, cx, cy, scale);
        g.setColor(dogColor);
        g.fillOval((int) (dHead.sx - 2), (int) (dHead.sy - 2), 5, 5);

        // Wagging Tail
        double tX = x - Math.cos(angle) * 2.5;
        double tY = y - Math.sin(angle) * 2.5;
        ScreenPt tailRoot = project(tX, tY, 5.0, cx, cy, scale);
        ScreenPt tailTip  = project(tX + tailWag, tY + tailWag, 6.8, cx, cy, scale);
        g.setColor(dogColor);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine((int) tailRoot.sx, (int) tailRoot.sy, (int) tailTip.sx, (int) tailTip.sy);
    }

    // ── 3D Geometric Primitives ──

    public void fillQuad3D(Graphics2D g, double x1, double y1, double z1,
                           double x2, double y2, double z2,
                           double x3, double y3, double z3,
                           double x4, double y4, double z4,
                           Color c, double nx, double ny, double nz,
                           double cx, double cy, double scale) {
        ScreenPt p1 = project(x1, y1, z1, cx, cy, scale);
        ScreenPt p2 = project(x2, y2, z2, cx, cy, scale);
        ScreenPt p3 = project(x3, y3, z3, cx, cy, scale);
        ScreenPt p4 = project(x4, y4, z4, cx, cy, scale);

        double signedArea = (p1.sx * (p2.sy - p4.sy) + p2.sx * (p3.sy - p1.sy)
                           + p3.sx * (p4.sy - p2.sy) + p4.sx * (p1.sy - p3.sy)) * 0.5;

        if (nz <= 0.4 && signedArea >= 0 && pitch < Math.toRadians(88)) {
            return;
        }

        double shade = computeShading(nx, ny, nz);
        Path2D poly = new Path2D.Double();
        poly.moveTo(p1.sx, p1.sy);
        poly.lineTo(p2.sx, p2.sy);
        poly.lineTo(p3.sx, p3.sy);
        poly.lineTo(p4.sx, p4.sy);
        poly.closePath();

        g.setColor(shadeColor(c, shade));
        g.fill(poly);
    }

    public void drawBox3D(Graphics2D g, double bx, double by, double bz,
                          double len, double wid, double height, double heading,
                          Color topColor, Color sideColor,
                          double cx, double cy, double scale) {
        double cosH = Math.cos(heading);
        double sinH = Math.sin(heading);
        double hl = len / 2.0;
        double hw = wid / 2.0;

        double[][] corners = {
            { hl * cosH - hw * sinH,  hl * sinH + hw * cosH},
            {-hl * cosH - hw * sinH, -hl * sinH + hw * cosH},
            {-hl * cosH + hw * sinH, -hl * sinH - hw * cosH},
            { hl * cosH + hw * sinH,  hl * sinH - hw * cosH}
        };

        for (int i = 0; i < 4; i++) {
            int next = (i + 1) % 4;
            double x1 = bx + corners[i][0],    y1 = by + corners[i][1];
            double x2 = bx + corners[next][0], y2 = by + corners[next][1];

            double dx = x2 - x1, dy = y2 - y1;
            double nlen = Math.hypot(dx, dy);
            double nx = dy / nlen, ny = -dx / nlen;

            fillQuad3D(g,
                x1, y1, bz,
                x2, y2, bz,
                x2, y2, bz + height,
                x1, y1, bz + height,
                sideColor, nx, ny, 0, cx, cy, scale);
        }

        fillQuad3D(g,
            bx + corners[0][0], by + corners[0][1], bz + height,
            bx + corners[1][0], by + corners[1][1], bz + height,
            bx + corners[2][0], by + corners[2][1], bz + height,
            bx + corners[3][0], by + corners[3][1], bz + height,
            topColor, 0, 0, 1.0, cx, cy, scale);
    }

    private void drawLineQuad3D(Graphics2D g, double x1, double y1, double x2, double y2,
                                double thickness, double z, Color c, double cx, double cy, double scale) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.hypot(dx, dy);
        if (len < 1e-4) return;
        double ox = -dy / len * (thickness / 2.0);
        double oy =  dx / len * (thickness / 2.0);

        fillQuad3D(g,
            x1 - ox, y1 - oy, z,
            x2 - ox, y2 - oy, z,
            x2 + ox, y2 + oy, z,
            x1 + ox, y1 + oy, z,
            c, 0, 0, 1.0, cx, cy, scale);
    }

    // ── Scenery Cache (Buildings, Trees, Street Lamps) ──

    public static boolean isRoadCollision(double x, double y, double clearance, Junction j) {
        int hw = j.laneCount * 22;
        double roadCorridorHalfWidth = hw + 4.0 /*curbW*/ + 20.0 /*footW*/ + clearance;

        if (j.kind.ring) {
            // Roundabout outer ring road clearance
            double rOut = Junction.R + 22.0 + 4.0 + 20.0 + clearance;
            double distFromOrigin = Math.hypot(x, y);

            // Outside the center island, must be clear of outer ring and sidewalk
            if (distFromOrigin > 38.0 && distFromOrigin < rOut) {
                return true;
            }
        } else {
            // Center intersection plaza clearance
            double centerClearance = j.stop + 20.0 + clearance;
            if (Math.hypot(x, y) < centerClearance) {
                return true;
            }
        }

        // Check against every arm corridor from origin out to ARM + 80
        for (int i = 0; i < j.dir.length; i++) {
            double[] A = j.dir[i];
            double[] O = {-A[1], A[0]};

            double distAlongArm = x * A[0] + y * A[1];
            double distAcrossArm = Math.abs(x * O[0] + y * O[1]);

            if (distAlongArm > -15.0 && distAlongArm < Junction.ARM + 80.0) {
                if (distAcrossArm < roadCorridorHalfWidth) {
                    return true;
                }
            }
        }

        return false;
    }

    private void rebuildScenery(Junction j) {
        cachedBuildings.clear();
        cachedTrees.clear();
        cachedStreetLamps.clear();

        int n = j.dir.length;
        double[] angles = Arrays.copyOf(j.ang, n);
        Arrays.sort(angles);

        Random rnd = new Random(42 + j.kind.ordinal() * 100);

        for (int i = 0; i < n; i++) {
            double a1 = angles[i];
            double a2 = angles[(i + 1) % n];
            if (a2 <= a1) a2 += 2 * Math.PI;
            double midAng = (a1 + a2) / 2.0;
            double sectorSpan = a2 - a1;

            if (sectorSpan > 0.45) {
                // Buildings (checked with clearance so building footprints never clip roads or sidewalks)
                double[] dists = {270, 370, 480, 590};
                for (double dist : dists) {
                    for (int attempt = 0; attempt < 10; attempt++) {
                        double offsetAng = midAng + (rnd.nextDouble() - 0.5) * (sectorSpan * 0.55);
                        double bx = dist * Math.cos(offsetAng);
                        double by = dist * Math.sin(offsetAng);

                        double bW = 26 + rnd.nextInt(18);
                        double bL = 26 + rnd.nextInt(18);
                        double bH = 35 + rnd.nextInt(55);

                        double maxRad = Math.hypot(bW, bL) * 0.5 + 4.0;
                        if (!isRoadCollision(bx, by, maxRad + 14.0, j) && Math.hypot(bx, by) < Junction.ARM + 40) {
                            Color bTop = new Color(175 + rnd.nextInt(50), 185 + rnd.nextInt(50), 205 + rnd.nextInt(40));
                            Color bSide = new Color(65 + rnd.nextInt(35), 75 + rnd.nextInt(35), 95 + rnd.nextInt(35));
                            cachedBuildings.add(new SceneryBuilding(bx, by, bW, bL, bH, offsetAng, bTop, bSide));
                            break;
                        }
                    }
                }

                // Trees (checked with clearance so tree canopies never overhang the road or footpath)
                for (int t = 0; t < 12; t++) {
                    for (int attempt = 0; attempt < 12; attempt++) {
                        double tDist = 120 + rnd.nextDouble() * 490;
                        double tAng = midAng + (rnd.nextDouble() - 0.5) * (sectorSpan * 0.75);
                        double tx = tDist * Math.cos(tAng);
                        double ty = tDist * Math.sin(tAng);

                        if (!isRoadCollision(tx, ty, 16.0, j) && Math.hypot(tx, ty) < Junction.ARM + 50) {
                            double tH = 14 + rnd.nextInt(12);
                            cachedTrees.add(new SceneryTree(tx, ty, tH));
                            break;
                        }
                    }
                }
            }

            // Street lamps positioned safely along the outer edge of the sidewalk
            for (double s = j.stop + 40; s < Junction.ARM; s += 85) {
                int hw = j.laneCount * 22;
                double[] A = j.dir[i];
                double[] O = {-A[1], A[0]};
                double lx = A[0] * s + O[0] * (hw + 26);
                double ly = A[1] * s + O[1] * (hw + 26);
                cachedStreetLamps.add(new StreetLamp(lx, ly, 22.0));
            }
        }

        if (j.kind.ring) {
            // Center island trees inside the roundabout (safe radius < 38px)
            cachedTrees.add(new SceneryTree(0, 0, 24));
            for (int k = 0; k < 4; k++) {
                double ringAng = k * Math.PI / 2.0;
                double rDist = Junction.R * 0.42;
                cachedTrees.add(new SceneryTree(rDist * Math.cos(ringAng), rDist * Math.sin(ringAng), 16));
            }
        }
    }

    private static class SceneryBuilding {
        double x, y, w, l, h, rot;
        Color topColor, sideColor;

        SceneryBuilding(double x, double y, double w, double l, double h, double rot, Color top, Color side) {
            this.x = x; this.y = y; this.w = w; this.l = l; this.h = h; this.rot = rot;
            this.topColor = top; this.sideColor = side;
        }

        Renderable3D createRenderable(Renderer3D r, double cx, double cy, double scale) {
            ScreenPt pt = r.project(x, y, 0, cx, cy, scale);
            return new Renderable3D(pt.depth, (g) -> {
                r.drawBox3D(g, x, y, 0, l, w, h, rot,
                    topColor, sideColor, cx, cy, scale);
                r.drawBox3D(g, x, y, h, l * 0.5, w * 0.5, 4.0, rot,
                    new Color(220, 225, 235), new Color(120, 125, 135), cx, cy, scale);
            });
        }
    }

    private static class SceneryTree {
        double x, y, h;
        SceneryTree(double x, double y, double h) { this.x = x; this.y = y; this.h = h; }

        Renderable3D createRenderable(Renderer3D r, double cx, double cy, double scale) {
            ScreenPt pt = r.project(x, y, 0, cx, cy, scale);
            return new Renderable3D(pt.depth, (g) -> {
                r.drawBox3D(g, x, y, 0, 3.0, 3.0, h * 0.4, 0,
                    new Color(90, 60, 40), new Color(70, 45, 30), cx, cy, scale);
                r.drawBox3D(g, x, y, h * 0.35, h * 0.9, h * 0.9, h * 0.45, Math.PI / 4,
                    new Color(46, 125, 50), new Color(36, 100, 40), cx, cy, scale);
                r.drawBox3D(g, x, y, h * 0.70, h * 0.6, h * 0.6, h * 0.35, 0,
                    new Color(67, 160, 71), new Color(48, 120, 52), cx, cy, scale);
            });
        }
    }

    private static class StreetLamp {
        double x, y, h;
        StreetLamp(double x, double y, double h) { this.x = x; this.y = y; this.h = h; }

        Renderable3D createRenderable(Renderer3D r, double cx, double cy, double scale) {
            ScreenPt pt = r.project(x, y, 0, cx, cy, scale);
            return new Renderable3D(pt.depth, (g) -> {
                r.drawBox3D(g, x, y, 0, 1.8, 1.8, h, 0,
                    new Color(70, 75, 85), new Color(50, 55, 65), cx, cy, scale);

                ScreenPt top = r.project(x, y, h, cx, cy, scale);
                g.setColor(new Color(255, 235, 150));
                g.fillOval((int) (top.sx - 2), (int) (top.sy - 2), 4, 4);

                ScreenPt ground = r.project(x, y, 0.4, cx, cy, scale);
                g.setColor(new Color(255, 230, 140, 28));
                g.fillOval((int) (ground.sx - 12), (int) (ground.sy - 8), 24, 16);
            });
        }
    }

    // ── HUD Overlay & Navigation Compass ──

    private void drawHUD(Graphics2D g, int width, int height, Simulation sim) {
        int hudW = 490;
        int hudH = 82;
        g.setColor(new Color(15, 20, 32, 215));
        g.fillRoundRect(12, 10, hudW, hudH, 12, 12);
        g.setColor(new Color(60, 80, 120, 160));
        g.setStroke(new BasicStroke(1.2f));
        g.drawRoundRect(12, 10, hudW, hudH, 12, 12);

        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.setColor(new Color(230, 240, 255));
        g.drawString("3D TRAFFIC SIMULATOR", 24, 30);

        String modeBadge = (pitch >= Math.toRadians(88)) ? "TOP-DOWN 2D" :
                           (autoOrbit ? "3D AUTO-ORBIT" : "3D ISOMETRIC");
        g.setFont(new Font("SansSerif", Font.BOLD, 10));
        g.setColor(new Color(76, 201, 240));
        g.drawString("[" + modeBadge + "]", 200, 30);

        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.setColor(new Color(150, 165, 195));
        g.drawString("Accidents: " + sim.accidents.size() + " active (" + sim.accidentsCleared + " cleared)", 300, 30);

        for (HudButton btn : hudButtons) {
            btn.draw(g);
        }

        drawCompass(g, width - 48, 48);
    }

    private void drawCompass(Graphics2D g, int cx, int cy) {
        int r = 26;
        g.setColor(new Color(18, 24, 38, 200));
        g.fillOval(cx - r, cy - r, 2 * r, 2 * r);
        g.setColor(new Color(70, 90, 130, 180));
        g.setStroke(new BasicStroke(1.2f));
        g.drawOval(cx - r, cy - r, 2 * r, 2 * r);

        double rot = yaw + Math.PI / 4.0;
        int nx = (int) (cx + Math.sin(rot) * (r - 6));
        int ny = (int) (cy - Math.cos(rot) * (r - 6));
        int sx = (int) (cx - Math.sin(rot) * (r - 6));
        int sy = (int) (cy + Math.cos(rot) * (r - 6));

        g.setColor(new Color(240, 50, 50));
        g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(cx, cy, nx, ny);

        g.setColor(new Color(180, 190, 210));
        g.drawLine(cx, cy, sx, sy);

        g.setFont(new Font("SansSerif", Font.BOLD, 9));
        g.setColor(Color.WHITE);
        g.drawString("N", nx - 3, ny - 2);
    }

    // ── Helper Classes ──

    public interface RenderAction {
        void render(Graphics2D g);
    }

    public static class Renderable3D {
        public final double depth;
        public final RenderAction action;
        public Renderable3D(double depth, RenderAction action) {
            this.depth = depth;
            this.action = action;
        }
        public void render(Graphics2D g) { action.render(g); }
    }

    public static class HudButton {
        int x, y, w, h;
        String label;
        Runnable action;

        HudButton(int x, int y, int w, int h, String label, Runnable action) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.label = label; this.action = action;
        }

        boolean contains(int px, int py) {
            return (px >= x && px <= x + w && py >= y && py <= y + h);
        }

        void draw(Graphics2D g) {
            g.setColor(new Color(30, 42, 65, 230));
            g.fillRoundRect(x, y, w, h, 6, 6);
            g.setColor(new Color(76, 201, 240, 180));
            g.setStroke(new BasicStroke(1.0f));
            g.drawRoundRect(x, y, w, h, 6, 6);

            g.setFont(new Font("SansSerif", Font.BOLD, 10));
            FontMetrics fm = g.getFontMetrics();
            int strW = fm.stringWidth(label);
            g.setColor(new Color(220, 235, 255));
            g.drawString(label, x + (w - strW) / 2, y + (h + fm.getAscent() - fm.getDescent()) / 2);
        }
    }
}
