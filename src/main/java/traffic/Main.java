package traffic;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.Arrays;

/**
 * Swing GUI with a sidebar control panel and a canvas for the traffic simulation.
 * Enhanced graphics with multi-lane support, zebra crossings, pedestrians, and improved vehicles.
 *
 * Keys: 1-6 junction, N/M/H traffic, F/A/E signal mode, L free-left toggle, SPACE emergency vehicle.
 */
public class Main extends JFrame {

    // ── colours ──
    private static final Color BG_DARK     = new Color(30, 110, 60); // Grass green background
    private static final Color PANEL_BG    = new Color(18, 24, 40);
    private static final Color ROAD_COLOR  = new Color(50, 55, 65);
    private static final Color ACCENT      = new Color(76, 201, 240);
    private static final Color VEHICLE_CLR = new Color(76, 201, 240);
    private static final Color EMERG_CLR   = new Color(240, 50, 50);
    private static final Color TEXT_CLR    = new Color(200, 210, 230);
    private static final Color LABEL_CLR   = new Color(130, 145, 170);
    private static final Color RING_INNER  = new Color(40, 100, 50);

    // ── state ──
    Simulation sim;
    long last = System.nanoTime();

    // ── sidebar widgets ──
    private final JComboBox<String> junctionCombo;
    private final JComboBox<String> laneCombo;
    private final JComboBox<String> trafficCombo;
    private final JComboBox<String> modeCombo;
    private final JCheckBox freeLeftCheck;
    private final JButton emergencyBtn;
    private final JLabel lblVehicles, lblOut, lblCO2, lblAvgWait, lblMode, lblFreeLeft;
    private final JLabel[] lblQueue;
    private final JLabel[] lblArmCount;
    private final SimCanvas canvas;

    private static final String[] JUNCTION_NAMES = {
        "4-Way Junction", "3-Way T-Junction", "Y-Junction",
        "Roundabout (4 exits)", "Roundabout (5 exits)", "5-Way Junction"
    };
    private static final String[] LANE_NAMES = {"1 Lane", "2 Lanes", "3 Lanes"};
    private static final String[] TRAFFIC_NAMES = {"Normal", "Moderate", "Heavy"};
    private static final String[] MODE_NAMES = {"Fixed", "Adaptive", "Eco"};

    private static final int MAX_ARMS = 5;

    Main() {
        super("Traffic Junction Simulator – Enhanced Graphics & Multi-lane");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        sim = new Simulation(new Junction(Junction.Kind.FOUR, 1));

        // ── Sidebar ──
        JPanel sidebar = new JPanel();
        sidebar.setBackground(PANEL_BG);
        sidebar.setPreferredSize(new Dimension(240, 0));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBorder(new EmptyBorder(12, 14, 12, 14));

        sidebar.add(sectionTitle("JUNCTION TYPE"));
        junctionCombo = styledCombo(JUNCTION_NAMES);
        junctionCombo.addActionListener(e -> switchJunction());
        sidebar.add(junctionCombo);
        sidebar.add(Box.createVerticalStrut(14));
        
        sidebar.add(sectionTitle("LANES PER DIRECTION"));
        laneCombo = styledCombo(LANE_NAMES);
        laneCombo.addActionListener(e -> switchJunction());
        sidebar.add(laneCombo);
        sidebar.add(Box.createVerticalStrut(14));

        sidebar.add(sectionTitle("TRAFFIC DENSITY"));
        trafficCombo = styledCombo(TRAFFIC_NAMES);
        trafficCombo.addActionListener(e -> {
            int i = trafficCombo.getSelectedIndex();
            sim.rate = i == 0 ? .12 : i == 1 ? .25 : .45;
        });
        sidebar.add(trafficCombo);
        sidebar.add(Box.createVerticalStrut(14));

        sidebar.add(sectionTitle("SIGNAL MODE"));
        modeCombo = styledCombo(MODE_NAMES);
        modeCombo.setSelectedIndex(1); 
        modeCombo.addActionListener(e -> sim.mode = Simulation.Mode.values()[modeCombo.getSelectedIndex()]);
        sidebar.add(modeCombo);
        sidebar.add(Box.createVerticalStrut(14));

        freeLeftCheck = new JCheckBox("Free left turn");
        freeLeftCheck.setSelected(true);
        freeLeftCheck.setForeground(TEXT_CLR);
        freeLeftCheck.setBackground(PANEL_BG);
        freeLeftCheck.setFocusable(false);
        freeLeftCheck.addActionListener(e -> sim.j.freeLeftEnabled = freeLeftCheck.isSelected());
        sidebar.add(freeLeftCheck);
        sidebar.add(Box.createVerticalStrut(14));

        emergencyBtn = new JButton("🚨 Spawn Emergency");
        emergencyBtn.setBackground(new Color(180, 30, 30));
        emergencyBtn.setForeground(Color.WHITE);
        emergencyBtn.setFocusable(false);
        emergencyBtn.setBorder(new EmptyBorder(8, 12, 8, 12));
        emergencyBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        emergencyBtn.addActionListener(e -> sim.spawn(true));
        sidebar.add(emergencyBtn);
        sidebar.add(Box.createVerticalStrut(20));

        sidebar.add(sectionTitle("STATISTICS"));
        sidebar.add(Box.createVerticalStrut(4));
        lblVehicles = statLabel("Vehicles: 0"); sidebar.add(lblVehicles);
        lblOut = statLabel("Exited: 0"); sidebar.add(lblOut);
        lblCO2 = statLabel("CO₂: 0 g"); sidebar.add(lblCO2);
        lblAvgWait = statLabel("Avg wait: 0.0 s"); sidebar.add(lblAvgWait);
        lblMode = statLabel("Mode: ADAPTIVE"); sidebar.add(lblMode);
        lblFreeLeft = statLabel("Free left: ON"); sidebar.add(lblFreeLeft);
        sidebar.add(Box.createVerticalStrut(12));

        sidebar.add(sectionTitle("PER-ARM QUEUES"));
        sidebar.add(Box.createVerticalStrut(4));
        lblQueue = new JLabel[MAX_ARMS];
        for (int i = 0; i < MAX_ARMS; i++) { lblQueue[i] = statLabel("Arm " + (i+1) + ": 0"); sidebar.add(lblQueue[i]); }
        sidebar.add(Box.createVerticalStrut(12));

        sidebar.add(sectionTitle("PER-ARM VEHICLES"));
        sidebar.add(Box.createVerticalStrut(4));
        lblArmCount = new JLabel[MAX_ARMS];
        for (int i = 0; i < MAX_ARMS; i++) { lblArmCount[i] = statLabel("Arm " + (i+1) + ": 0"); sidebar.add(lblArmCount[i]); }

        sidebar.add(Box.createVerticalGlue());

        canvas = new SimCanvas();

        setLayout(new BorderLayout());
        add(sidebar, BorderLayout.WEST);
        add(canvas, BorderLayout.CENTER);
        setSize(1340, 800);
        setLocationRelativeTo(null);

        canvas.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                char c = Character.toLowerCase(e.getKeyChar());
                if (c >= '1' && c <= '6') { junctionCombo.setSelectedIndex(c - '1'); }
                else if (c == 'n') { trafficCombo.setSelectedIndex(0); }
                else if (c == 'm') { trafficCombo.setSelectedIndex(1); }
                else if (c == 'h') { trafficCombo.setSelectedIndex(2); }
                else if (c == 'f') { modeCombo.setSelectedIndex(0); }
                else if (c == 'a') { modeCombo.setSelectedIndex(1); }
                else if (c == 'e') { modeCombo.setSelectedIndex(2); }
                else if (c == 'l') { freeLeftCheck.doClick(); }
                else if (c == ' ') sim.spawn(true);
            }
        });

        new Timer(16, x -> {
            long n = System.nanoTime();
            sim.step(Math.min(.05, (n - last) / 1e9));
            last = n;
            updateStats();
            canvas.repaint();
        }).start();

        setVisible(true);
        canvas.requestFocusInWindow();
    }

    private void switchJunction() {
        int index = junctionCombo.getSelectedIndex();
        int lanes = laneCombo.getSelectedIndex() + 1;
        if (index < 0 || index >= Junction.Kind.values().length) return;
        double r = sim.rate;
        Simulation.Mode m = sim.mode;
        boolean fl = sim.j.freeLeftEnabled;
        sim = new Simulation(new Junction(Junction.Kind.values()[index], lanes));
        sim.rate = r;
        sim.mode = m;
        sim.j.freeLeftEnabled = fl;
    }

    private void updateStats() {
        lblVehicles.setText("Vehicles: " + sim.vehicles.size());
        lblOut.setText("Exited: " + sim.out);
        lblCO2.setText(String.format("CO₂: %d g", (int) sim.co2));
        lblAvgWait.setText(String.format("Avg wait: %.1f s", sim.avgWait()));
        lblMode.setText("Mode: " + sim.mode);
        lblFreeLeft.setText("Free left: " + (sim.j.freeLeftEnabled ? "ON" : "OFF"));

        int arms = sim.j.dir.length;
        for (int i = 0; i < MAX_ARMS; i++) {
            if (i < arms) {
                lblQueue[i].setText("Arm " + (i+1) + ": " + sim.queue[i]);
                lblQueue[i].setVisible(true);
                lblArmCount[i].setText("Arm " + (i+1) + ": " + sim.armCount[i]);
                lblArmCount[i].setVisible(true);
            } else {
                lblQueue[i].setVisible(false);
                lblArmCount[i].setVisible(false);
            }
        }
    }

    private JLabel sectionTitle(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(ACCENT);
        l.setFont(new Font("SansSerif", Font.BOLD, 11));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(0, 0, 4, 0));
        return l;
    }

    private JLabel statLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT_CLR);
        l.setFont(new Font("Monospaced", Font.PLAIN, 12));
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(1, 0, 1, 0));
        return l;
    }

    private JComboBox<String> styledCombo(String[] items) {
        JComboBox<String> cb = new JComboBox<>(items);
        cb.setMaximumSize(new Dimension(220, 28));
        cb.setAlignmentX(Component.LEFT_ALIGNMENT);
        cb.setFocusable(false);
        return cb;
    }

    private class SimCanvas extends JPanel {
        SimCanvas() { setBackground(BG_DARK); setFocusable(true); }

        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            AffineTransform base = g.getTransform();
            double z = Math.min(getWidth(), getHeight()) / 1200.0;
            Junction j = sim.j;

            g.translate(getWidth()/2.0, getHeight()/2.0);
            g.scale(z, z);

            // Draw road arms
            int laneW = 22;
            int hw = j.laneCount * laneW;
            
            for (int i = 0; i < j.dir.length; i++) {
                AffineTransform t = g.getTransform();
                g.rotate(j.ang[i]);
                
                // Road base
                g.setColor(ROAD_COLOR);
                g.fillRect(0, -hw, (int) Junction.ARM + 30, hw * 2);
                
                // Road markings
                g.setColor(new Color(255, 255, 255, 120));
                Stroke old = g.getStroke();
                // Double yellow center line
                g.setColor(new Color(240, 200, 50));
                g.setStroke(new BasicStroke(2));
                g.drawLine((int)j.stop, -2, (int)Junction.ARM + 30, -2);
                g.drawLine((int)j.stop, 2, (int)Junction.ARM + 30, 2);
                
                // Dashed white lane dividers
                g.setColor(new Color(255, 255, 255, 180));
                float[] dash = {12.0f, 12.0f};
                g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, dash, 0.0f));
                for (int k = 1; k < j.laneCount; k++) {
                    int off = k * laneW;
                    g.drawLine((int)j.stop, off, (int)Junction.ARM + 30, off);
                    g.drawLine((int)j.stop, -off, (int)Junction.ARM + 30, -off);
                }
                g.setStroke(old);
                
                // Zebra crossing
                int crossX = (int)(j.stop + Junction.CROSSING_OFFSET);
                g.setColor(Color.WHITE);
                for (int k = -hw; k < hw; k += 8) {
                    g.fillRect(crossX - 5, k + 1, 10, 6);
                }
                
                // Stop line (incoming lanes are positive y due to left-hand traffic logic)
                g.fillRect((int)j.stop, 0, 4, hw);

                g.setTransform(t);
            }

            // Draw centre (circle or roundabout ring)
            int rad = (int) (j.kind.ring ? Junction.R + 14 : j.stop);
            g.setColor(ROAD_COLOR);
            g.fillOval(-rad, -rad, 2*rad, 2*rad);
            if (j.kind.ring) {
                g.setColor(RING_INNER);
                int ir = (int) Junction.R - 14;
                g.fillOval(-ir, -ir, 2*ir, 2*ir);
            } else {
                // Draw traffic signals
                for (int i = 0; i < j.dir.length; i++) {
                    AffineTransform t = g.getTransform();
                    g.rotate(j.ang[i]);
                    char s = sim.signal(i);
                    
                    // Pole
                    g.setColor(Color.DARK_GRAY);
                    g.fillRect((int)j.stop - 4, hw + 4, 8, 16);
                    // Light box
                    g.setColor(Color.BLACK);
                    g.fillRoundRect((int)j.stop - 8, hw + 8, 16, 16, 4, 4);
                    // Light
                    g.setColor(s == 'G' ? Color.GREEN : s == 'Y' ? Color.YELLOW : Color.RED);
                    g.fillOval((int)j.stop - 4, hw + 12, 8, 8);
                    
                    g.setTransform(t);
                }
            }
            
            // Draw pedestrians
            for (Pedestrian p : sim.peds) {
                AffineTransform t = g.getTransform();
                g.rotate(j.ang[p.arm]);
                double px = j.stop + Junction.CROSSING_OFFSET;
                double py = (p.crossPos - 0.5) * (j.laneCount * 44);
                
                g.setColor(new Color(255, 220, 180)); // skin/head
                g.fillOval((int)px - 3, (int)py - 3, 6, 6);
                g.setColor(p.waiting ? Color.RED : Color.BLUE); // body
                g.fillRect((int)px - 2, (int)py, 4, 4);
                g.setTransform(t);
            }

            // Draw vehicles
            for (Vehicle v : sim.vehicles) {
                AffineTransform t = g.getTransform();
                g.translate(v.x, v.y);
                g.rotate(Math.atan2(v.hy, v.hx));
                
                // Headlights
                g.setColor(new Color(255, 255, 200, 100));
                g.fillArc(8, -10, 40, 20, -40, 80);
                
                // Body
                g.setColor(v.emergency ? EMERG_CLR : VEHICLE_CLR);
                g.fillRoundRect(-11, -5, 22, 10, 6, 6);
                
                // Roof / windows
                g.setColor(new Color(20, 20, 20, 150));
                g.fillRoundRect(-5, -4, 12, 8, 3, 3);
                
                // Brake lights
                if (v.v < 5) {
                    g.setColor(Color.RED);
                    g.fillRect(-11, -4, 2, 2);
                    g.fillRect(-11, 2, 2, 2);
                }
                
                // Emergency flasher
                if (v.emergency && (System.currentTimeMillis() / 200) % 2 == 0) {
                    g.setColor(Color.BLUE);
                    g.fillOval(0, -2, 4, 4);
                }

                g.setTransform(t);
            }

            g.setTransform(base);

            // On-canvas HUD
            g.setColor(TEXT_CLR);
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            g.drawString(j.kind.label + " (" + j.laneCount + " Lanes)"
                + "  |  Vehicles " + sim.vehicles.size()
                + "  |  Exited " + sim.out
                + "  |  " + sim.mode, 10, 20);
            g.setColor(LABEL_CLR);
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g.drawString("Keys: 1-6 junction · N/M/H traffic · F/A/E mode · L free-left · SPACE emergency", 10, 40);
        }
    }

    public static void main(String[] a) {
        SwingUtilities.invokeLater(Main::new);
    }
}
