package traffic;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.*;

/**
 * Modern Swing GUI with a sleek card-based sidebar and 3D isometric simulation canvas.
 * Featuring custom UI components, responsive layout, vehicle variety controls,
 * incident/accident simulation, and robust collision-free physics.
 */
public class Main extends JFrame {

    // ── Design System Color Tokens ──
    private static final Color BG_SIDEBAR      = new Color(15, 23, 42);   // Slate 900
    private static final Color CARD_BG         = new Color(30, 41, 59);   // Slate 800
    private static final Color CARD_BORDER     = new Color(51, 65, 85);   // Slate 700
    private static final Color ACCENT_CYAN     = new Color(56, 189, 248); // Sky 400
    private static final Color ACCENT_BLUE     = new Color(37, 99, 235);  // Blue 600
    private static final Color ACCENT_EMERALD  = new Color(16, 185, 129); // Emerald 500
    private static final Color ACCENT_AMBER    = new Color(245, 158, 11); // Amber 500
    private static final Color ACCENT_ROSE     = new Color(244, 63, 94);  // Rose 500
    private static final Color TEXT_WHITE      = new Color(248, 250, 252);
    private static final Color TEXT_SLATE      = new Color(148, 163, 184);
    private static final Color INPUT_BG        = new Color(15, 23, 42);

    private static final Font FONT_HEADER = new Font("Segoe UI", Font.BOLD, 11);
    private static final Font FONT_REGULAR = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 12);
    private static final Font FONT_MONO = new Font("Consolas", Font.BOLD, 12);

    // ── Simulation State ──
    Simulation sim;
    long last = System.nanoTime();
    final Renderer3D renderer3D = new Renderer3D();

    // ── Widgets ──
    private final JComboBox<String> junctionCombo;
    private final JComboBox<String> laneCombo;
    private final JComboBox<String> trafficCombo;
    private final JComboBox<String> speedCombo;
    private final JComboBox<String> pedCombo;
    private final JComboBox<String> modeCombo;
    private final JComboBox<String> cameraCombo;
    private final JComboBox<String> vehicleTypeCombo;
    private final JCheckBox autoOrbitCheck;
    private final JCheckBox freeLeftCheck;

    private final JLabel lblVehiclesVal, lblOutVal, lblCO2Val, lblWaitVal;
    private final JLabel lblAccidentBadge;
    private final JLabel[] lblQueueBars;
    private final JLabel[] lblArmCounts;
    private final SimCanvas canvas;

    private static final String[] JUNCTION_NAMES = {
        "4-Way Junction", "3-Way T-Junction", "Y-Junction",
        "Roundabout (4 Exits)", "Roundabout (5 Exits)", "5-Way Junction"
    };
    private static final String[] LANE_NAMES = {"1 Lane per Arm", "2 Lanes per Arm", "3 Lanes per Arm"};
    private static final String[] TRAFFIC_NAMES = {"Normal Traffic", "Moderate Traffic", "Heavy Traffic"};
    private static final String[] SPEED_NAMES = {"0.5x Slow", "1.0x Normal", "1.5x Fast", "2.0x Double", "3.0x Max"};
    private static final double[] SPEED_VALUES = {0.5, 1.0, 1.5, 2.0, 3.0};
    public double simSpeed = 1.0;

    private static final String[] PED_NAMES = {"Off (0%)", "Low (35%)", "Normal (100%)", "High (200%)"};
    private static final String[] MODE_NAMES = {"Fixed Timer", "Adaptive Signal", "Eco Mode"};
    private static final String[] CAMERA_NAMES = {"3D Isometric View", "3D Cinematic View", "Top-Down 2D View"};
    private static final String[] VEHICLE_OPTIONS = {
        "Random Traffic", "Sedan Car", "Taxi Cab", "Motorbike", "Bicycle",
        "City Bus", "Cargo Truck", "Police Cruiser", "Ambulance", "Fire Engine"
    };

    private static final int MAX_ARMS = 5;

    public Main() {
        super("Traffic Junction Simulator – 3D Isometric View & Vehicle Varieties");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        sim = new Simulation(new Junction(Junction.Kind.FOUR, 1));

        // ── Main Sidebar Container with Viewport Width Tracking ──
        ScrollableSidebar sidebar = new ScrollableSidebar();
        sidebar.setBackground(BG_SIDEBAR);
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBorder(new EmptyBorder(12, 12, 16, 12));

        // ── CARD 1: Junction & Traffic Settings ──
        JPanel card1 = createCard("JUNCTION & TRAFFIC", ACCENT_CYAN);
        junctionCombo = createStyledCombo(JUNCTION_NAMES);
        junctionCombo.addActionListener(e -> switchJunction());
        card1.add(createInputRow("Layout:", junctionCombo));
        card1.add(Box.createVerticalStrut(6));

        laneCombo = createStyledCombo(LANE_NAMES);
        laneCombo.addActionListener(e -> switchJunction());
        card1.add(createInputRow("Lanes:", laneCombo));
        card1.add(Box.createVerticalStrut(6));

        trafficCombo = createStyledCombo(TRAFFIC_NAMES);
        trafficCombo.addActionListener(e -> {
            int i = trafficCombo.getSelectedIndex();
            sim.rate = i == 0 ? .12 : i == 1 ? .25 : .45;
        });
        card1.add(createInputRow("Density:", trafficCombo));
        card1.add(Box.createVerticalStrut(6));

        speedCombo = createStyledCombo(SPEED_NAMES);
        speedCombo.setSelectedIndex(1);
        speedCombo.addActionListener(e -> {
            simSpeed = SPEED_VALUES[speedCombo.getSelectedIndex()];
        });
        card1.add(createInputRow("Speed:", speedCombo));
        card1.add(Box.createVerticalStrut(6));

        pedCombo = createStyledCombo(PED_NAMES);
        pedCombo.setSelectedIndex(2);
        pedCombo.addActionListener(e -> {
            int idx = pedCombo.getSelectedIndex();
            Simulation.PedestrianDensity pd = Simulation.PedestrianDensity.values()[idx];
            sim.setPedestrianDensity(pd);
        });
        card1.add(createInputRow("Crowd:", pedCombo));
        card1.add(Box.createVerticalStrut(6));

        modeCombo = createStyledCombo(MODE_NAMES);
        modeCombo.setSelectedIndex(1);
        modeCombo.addActionListener(e -> sim.mode = Simulation.Mode.values()[modeCombo.getSelectedIndex()]);
        card1.add(createInputRow("Signals:", modeCombo));
        card1.add(Box.createVerticalStrut(6));

        freeLeftCheck = createStyledCheckBox("Free Left Turn Rule (L)", true);
        freeLeftCheck.addActionListener(e -> sim.j.freeLeftEnabled = freeLeftCheck.isSelected());
        card1.add(freeLeftCheck);
        sidebar.add(card1);
        sidebar.add(Box.createVerticalStrut(10));

        // ── CARD 2: 3D Camera Controls ──
        JPanel card2 = createCard("3D CAMERA & VIEW", ACCENT_CYAN);
        autoOrbitCheck = createStyledCheckBox("Auto-Orbit Drone Camera (C)", false);
        autoOrbitCheck.addActionListener(e -> renderer3D.autoOrbit = autoOrbitCheck.isSelected());

        cameraCombo = createStyledCombo(CAMERA_NAMES);
        cameraCombo.addActionListener(e -> {
            int idx = cameraCombo.getSelectedIndex();
            if (idx == 0) renderer3D.setPreset(Renderer3D.CameraPreset.ISOMETRIC);
            else if (idx == 1) renderer3D.setPreset(Renderer3D.CameraPreset.ANGLED);
            else if (idx == 2) renderer3D.setPreset(Renderer3D.CameraPreset.TOP_DOWN);
            autoOrbitCheck.setSelected(renderer3D.autoOrbit);
        });
        card2.add(createInputRow("Preset:", cameraCombo));
        card2.add(Box.createVerticalStrut(6));

        card2.add(autoOrbitCheck);
        card2.add(Box.createVerticalStrut(6));

        JButton resetCamBtn = createStyledButton("↺ Reset Camera (R)", CARD_BORDER, TEXT_WHITE);
        resetCamBtn.addActionListener(e -> {
            renderer3D.resetCamera();
            cameraCombo.setSelectedIndex(0);
            autoOrbitCheck.setSelected(false);
        });
        card2.add(resetCamBtn);
        sidebar.add(card2);
        sidebar.add(Box.createVerticalStrut(10));

        // ── CARD 3: Vehicle Spawner ──
        JPanel card3 = createCard("VEHICLE SPAWNER", ACCENT_CYAN);
        vehicleTypeCombo = createStyledCombo(VEHICLE_OPTIONS);
        card3.add(createInputRow("Type:", vehicleTypeCombo));
        card3.add(Box.createVerticalStrut(6));

        JButton spawnBtn = createStyledButton("+ Spawn Selected Vehicle", ACCENT_BLUE, TEXT_WHITE);
        spawnBtn.addActionListener(e -> spawnSelectedVehicle());
        card3.add(spawnBtn);
        card3.add(Box.createVerticalStrut(6));

        // Quick Emergency Buttons Grid
        JPanel emgRow = new JPanel(new GridLayout(1, 3, 6, 0));
        emgRow.setBackground(CARD_BG);
        emgRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        emgRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton btnPolice = createCompactButton("🚓 Police", new Color(30, 58, 138), TEXT_WHITE);
        btnPolice.addActionListener(e -> sim.spawn(Vehicle.Type.POLICE));
        emgRow.add(btnPolice);

        JButton btnMedic = createCompactButton("🚑 Medic", new Color(153, 27, 27), TEXT_WHITE);
        btnMedic.addActionListener(e -> sim.spawn(Vehicle.Type.AMBULANCE));
        emgRow.add(btnMedic);

        JButton btnFire = createCompactButton("🚒 Fire", new Color(185, 28, 28), TEXT_WHITE);
        btnFire.addActionListener(e -> sim.spawn(Vehicle.Type.FIRE_TRUCK));
        emgRow.add(btnFire);
        card3.add(emgRow);
        sidebar.add(card3);
        sidebar.add(Box.createVerticalStrut(10));

        // ── CARD 4: Incident & Accident Simulation ──
        JPanel card4 = createCard("INCIDENTS & ACCIDENTS", ACCENT_ROSE);
        JButton triggerAccBtn = createStyledButton("💥 Trigger Accident (X)", new Color(225, 29, 72), TEXT_WHITE);
        triggerAccBtn.addActionListener(e -> sim.triggerAccident());
        card4.add(triggerAccBtn);
        card4.add(Box.createVerticalStrut(6));

        lblAccidentBadge = new JLabel("Status: 0 active | 0 cleared");
        lblAccidentBadge.setFont(FONT_REGULAR);
        lblAccidentBadge.setForeground(TEXT_SLATE);
        lblAccidentBadge.setAlignmentX(Component.LEFT_ALIGNMENT);
        card4.add(lblAccidentBadge);
        sidebar.add(card4);
        sidebar.add(Box.createVerticalStrut(10));

        // ── CARD 5: Live Metrics & Queues ──
        JPanel card5 = createCard("LIVE SIMULATION METRICS", ACCENT_EMERALD);
        JPanel kpiGrid = new JPanel(new GridLayout(2, 2, 8, 8));
        kpiGrid.setBackground(CARD_BG);
        kpiGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 118));
        kpiGrid.setPreferredSize(new Dimension(320, 112));
        kpiGrid.setAlignmentX(Component.LEFT_ALIGNMENT);

        lblVehiclesVal = createKpiBox(kpiGrid, "ACTIVE CARS", "0", ACCENT_CYAN);
        lblOutVal = createKpiBox(kpiGrid, "EXITED", "0", ACCENT_EMERALD);
        lblWaitVal = createKpiBox(kpiGrid, "AVG WAIT", "0.0s", ACCENT_AMBER);
        lblCO2Val = createKpiBox(kpiGrid, "CO₂ FLOW", "0g", ACCENT_ROSE);
        card5.add(kpiGrid);
        card5.add(Box.createVerticalStrut(12));

        JLabel qHeader = new JLabel("INCOMING QUEUES BY ARM");
        qHeader.setFont(new Font("Segoe UI", Font.BOLD, 11));
        qHeader.setForeground(new Color(203, 213, 225));
        qHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        card5.add(qHeader);
        card5.add(Box.createVerticalStrut(8));

        lblQueueBars = new JLabel[MAX_ARMS];
        lblArmCounts = new JLabel[MAX_ARMS];
        for (int i = 0; i < MAX_ARMS; i++) {
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setBackground(new Color(24, 33, 47));
            row.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
            row.setPreferredSize(new Dimension(300, 24));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel name = new JLabel("Arm " + (i + 1) + ":");
            name.setFont(new Font("Segoe UI", Font.BOLD, 11));
            name.setForeground(new Color(203, 213, 225));
            name.setPreferredSize(new Dimension(52, 18));
            row.add(name, BorderLayout.WEST);

            lblQueueBars[i] = new JLabel("□□□□□");
            lblQueueBars[i].setFont(FONT_MONO);
            lblQueueBars[i].setForeground(new Color(251, 191, 36));
            row.add(lblQueueBars[i], BorderLayout.CENTER);

            lblArmCounts[i] = new JLabel("0 cars");
            lblArmCounts[i].setFont(new Font("Segoe UI", Font.BOLD, 11));
            lblArmCounts[i].setForeground(Color.WHITE);
            lblArmCounts[i].setHorizontalAlignment(SwingConstants.RIGHT);
            lblArmCounts[i].setPreferredSize(new Dimension(58, 18));
            row.add(lblArmCounts[i], BorderLayout.EAST);

            card5.add(row);
            card5.add(Box.createVerticalStrut(4));
        }

        sidebar.add(card5);
        sidebar.add(Box.createVerticalStrut(24));

        // ── Custom Modern ScrollPane ──
        JScrollPane scrollPane = new JScrollPane(sidebar);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(24);
        scrollPane.getVerticalScrollBar().setBlockIncrement(96);
        scrollPane.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        scrollPane.setPreferredSize(new Dimension(360, 0));
        scrollPane.setMinimumSize(new Dimension(280, 0));

        canvas = new SimCanvas();
        canvas.setMinimumSize(new Dimension(450, 0));

        // ── Resizable / Adjustable SplitPane ──
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollPane, canvas);
        splitPane.setDividerLocation(365);
        splitPane.setDividerSize(6);
        splitPane.setContinuousLayout(true);
        splitPane.setBorder(null);
        splitPane.setBackground(BG_SIDEBAR);

        setLayout(new BorderLayout());
        add(splitPane, BorderLayout.CENTER);
        setSize(1480, 900);
        setMinimumSize(new Dimension(1100, 720));
        setLocationRelativeTo(null);

        // ── Keyboard Shortcuts Listener ──
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
                else if (c == 'v') {
                    int nextIdx = (cameraCombo.getSelectedIndex() + 1) % 3;
                    cameraCombo.setSelectedIndex(nextIdx);
                }
                else if (c == 'c') { autoOrbitCheck.doClick(); }
                else if (c == 'r') { resetCamBtn.doClick(); }
                else if (c == 'x') { triggerAccBtn.doClick(); }
                else if (c == ' ') sim.spawn(true);
            }
        });

        // ── 60 FPS Animation Timer ──
        new Timer(16, x -> {
            long n = System.nanoTime();
            sim.step(Math.min(.05, (n - last) / 1e9) * simSpeed);
            last = n;
            updateStats();
            canvas.repaint();
        }).start();

        setVisible(true);
        canvas.requestFocusInWindow();
    }

    // ── Modern Scrollable Sidebar (Constrains children to viewport width) ──
    private static class ScrollableSidebar extends JPanel implements Scrollable {
        public ScrollableSidebar() {
            super();
        }
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) { return 24; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) { return 96; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    // ── Helper UI Factory Methods ──

    private JPanel createCard(String title, Color accent) {
        JPanel card = new JPanel();
        card.setBackground(CARD_BG);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CARD_BORDER, 1, true),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 400));

        // Card Title with colored indicator bar
        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        titleRow.setBackground(CARD_BG);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

        JLabel bar = new JLabel("▎ ");
        bar.setForeground(accent);
        bar.setFont(FONT_HEADER);
        titleRow.add(bar);

        JLabel lbl = new JLabel(title);
        lbl.setFont(FONT_HEADER);
        lbl.setForeground(TEXT_WHITE);
        titleRow.add(lbl);

        card.add(titleRow);
        card.add(Box.createVerticalStrut(6));
        return card;
    }

    private JPanel createInputRow(String labelText, JComponent input) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(CARD_BG);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        JLabel lbl = new JLabel(labelText);
        lbl.setFont(FONT_REGULAR);
        lbl.setForeground(TEXT_SLATE);
        lbl.setPreferredSize(new Dimension(60, 26));
        row.add(lbl, BorderLayout.WEST);
        row.add(input, BorderLayout.CENTER);
        return row;
    }

    private JComboBox<String> createStyledCombo(String[] items) {
        JComboBox<String> cb = new JComboBox<>(items);
        cb.setBackground(INPUT_BG);
        cb.setForeground(TEXT_WHITE);
        cb.setFont(FONT_REGULAR);
        cb.setFocusable(false);
        cb.setBorder(BorderFactory.createLineBorder(CARD_BORDER, 1));
        cb.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object val, int idx, boolean isSel, boolean hasFoc) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, val, idx, isSel, hasFoc);
                l.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
                l.setBackground(isSel ? ACCENT_BLUE : INPUT_BG);
                l.setForeground(TEXT_WHITE);
                l.setFont(FONT_REGULAR);
                return l;
            }
        });
        return cb;
    }

    private JCheckBox createStyledCheckBox(String text, boolean sel) {
        JCheckBox cb = new JCheckBox(text, sel);
        cb.setBackground(CARD_BG);
        cb.setForeground(TEXT_WHITE);
        cb.setFont(FONT_REGULAR);
        cb.setFocusable(false);
        cb.setAlignmentX(Component.LEFT_ALIGNMENT);
        return cb;
    }

    private JButton createStyledButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFont(FONT_BOLD);
        btn.setFocusable(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(bg.brighter(), 1, true),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btn.setBackground(bg.brighter()); }
            @Override public void mouseExited(MouseEvent e) { btn.setBackground(bg); }
        });
        return btn;
    }

    private JButton createCompactButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setFocusable(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(bg.brighter(), 1, true),
            BorderFactory.createEmptyBorder(5, 4, 5, 4)
        ));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btn.setBackground(bg.brighter()); }
            @Override public void mouseExited(MouseEvent e) { btn.setBackground(bg); }
        });
        return btn;
    }

    private JLabel createKpiBox(JPanel parent, String title, String initialVal, Color accent) {
        JPanel box = new JPanel(new BorderLayout(0, 4));
        box.setBackground(new Color(24, 33, 47));
        box.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(51, 65, 85), 1, true),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));

        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        topRow.setOpaque(false);
        JLabel dot = new JLabel("● ");
        dot.setFont(new Font("Segoe UI", Font.BOLD, 8));
        dot.setForeground(accent);
        topRow.add(dot);

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 10));
        titleLbl.setForeground(new Color(203, 213, 225));
        topRow.add(titleLbl);
        box.add(topRow, BorderLayout.NORTH);

        JLabel valLbl = new JLabel(initialVal);
        valLbl.setFont(new Font("Segoe UI", Font.BOLD, 18));
        valLbl.setForeground(accent);
        box.add(valLbl, BorderLayout.CENTER);

        parent.add(box);
        return valLbl;
    }

    // ── Modern Custom ScrollBar UI ──
    private static class ModernScrollBarUI extends BasicScrollBarUI {
        @Override protected void configureScrollBarColors() {
            this.thumbColor = new Color(90, 105, 130);
            this.trackColor = BG_SIDEBAR;
        }
        @Override public Dimension getPreferredSize(JComponent c) {
            return new Dimension(10, super.getPreferredSize(c).height);
        }
        @Override protected JButton createDecreaseButton(int orientation) { return createZeroButton(); }
        @Override protected JButton createIncreaseButton(int orientation) { return createZeroButton(); }
        private JButton createZeroButton() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            g.setColor(trackColor);
            g.fillRect(r.x, r.y, r.width, r.height);
        }
        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isThumbRollover() ? new Color(148, 163, 184) : thumbColor);
            g2.fillRoundRect(r.x + 1, r.y + 2, r.width - 2, r.height - 4, 6, 6);
            g2.dispose();
        }
    }

    private void spawnSelectedVehicle() {
        int idx = vehicleTypeCombo.getSelectedIndex();
        switch (idx) {
            case 1: sim.spawn(Vehicle.Type.CAR); break;
            case 2: sim.spawn(Vehicle.Type.TAXI); break;
            case 3: sim.spawn(Vehicle.Type.BIKE); break;
            case 4: sim.spawn(Vehicle.Type.CYCLE); break;
            case 5: sim.spawn(Vehicle.Type.BUS); break;
            case 6: sim.spawn(Vehicle.Type.TRUCK); break;
            case 7: sim.spawn(Vehicle.Type.POLICE); break;
            case 8: sim.spawn(Vehicle.Type.AMBULANCE); break;
            case 9: sim.spawn(Vehicle.Type.FIRE_TRUCK); break;
            case 0:
            default:
                sim.spawn(false);
                break;
        }
    }

    private void switchJunction() {
        int index = junctionCombo.getSelectedIndex();
        int lanes = laneCombo.getSelectedIndex() + 1;
        if (index < 0 || index >= Junction.Kind.values().length) return;
        double r = sim.rate;
        Simulation.Mode m = sim.mode;
        boolean fl = sim.j.freeLeftEnabled;
        Simulation.PedestrianDensity pd = sim.pedDensity;
        sim = new Simulation(new Junction(Junction.Kind.values()[index], lanes));
        sim.rate = r;
        sim.mode = m;
        sim.j.freeLeftEnabled = fl;
        sim.setPedestrianDensity(pd);
    }

    private void updateStats() {
        lblVehiclesVal.setText(String.valueOf(sim.vehicles.size()));
        lblOutVal.setText(String.valueOf(sim.out));
        lblWaitVal.setText(String.format("%.1fs", sim.avgWait()));
        lblCO2Val.setText(String.format("%dg", (int) sim.co2));

        lblAccidentBadge.setText(String.format("Status: %d active | %d cleared",
            sim.accidents.size(), sim.accidentsCleared));

        int arms = sim.j.dir.length;
        for (int i = 0; i < MAX_ARMS; i++) {
            if (i < arms) {
                int q = sim.queue[i];
                int total = sim.armCount[i];
                StringBuilder sb = new StringBuilder();
                int filled = Math.min(8, q);
                for (int k = 0; k < filled; k++) sb.append("■");
                for (int k = filled; k < 8; k++) sb.append("□");
                lblQueueBars[i].setText(sb.toString());
                lblArmCounts[i].setText(total + " cars");
                lblQueueBars[i].setVisible(true);
                lblArmCounts[i].setVisible(true);
            } else {
                lblQueueBars[i].setVisible(false);
                lblArmCounts[i].setVisible(false);
            }
        }
    }

    private class SimCanvas extends JPanel {
        SimCanvas() {
            setBackground(new Color(15, 23, 42));
            setFocusable(true);
            renderer3D.attachControls(this);
        }

        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            renderer3D.render(g, getWidth(), getHeight(), sim);
        }
    }

    public static void main(String[] a) {
        SwingUtilities.invokeLater(Main::new);
    }
}
