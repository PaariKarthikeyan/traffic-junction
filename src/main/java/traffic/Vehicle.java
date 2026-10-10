package traffic;

import java.awt.Color;

/**
 * A vehicle that follows a precomputed Route; position/heading are refreshed each step.
 * Supports multiple distinct vehicle types with custom dimensions, physics, and visual aesthetics.
 */
public class Vehicle {

    public enum Type {
        CAR("Sedan Car", 20, 10, 95, 55, 100, 1.8, false),
        BIKE("Motorbike", 13, 5, 105, 80, 110, 0.8, false),
        CYCLE("Bicycle", 10, 4, 38, 30, 75, 0.0, false),
        TAXI("Taxi Cab", 20, 10, 95, 60, 100, 1.8, false),
        BUS("City Bus", 38, 12, 75, 35, 70, 3.5, false),
        TRUCK("Cargo Truck", 36, 12, 70, 30, 65, 4.0, false),
        POLICE("Police Cruiser", 21, 10, 125, 85, 120, 2.2, true),
        AMBULANCE("Ambulance", 25, 11, 120, 75, 115, 2.5, true),
        FIRE_TRUCK("Fire Engine", 38, 12, 110, 65, 100, 4.5, true);

        public final String label;
        public final double length, width, maxSpeed, accel, decel, co2Rate;
        public final boolean isEmergency;

        Type(String label, double l, double w, double ms, double acc, double dec, double co2, boolean em) {
            this.label = label;
            this.length = l;
            this.width = w;
            this.maxSpeed = ms;
            this.accel = acc;
            this.decel = dec;
            this.co2Rate = co2;
            this.isEmergency = em;
        }
    }

    private static long nextVehicleId = 1;
    public final long id = nextVehicleId++;

    public Route route;
    public final Type type;
    public final boolean emergency;
    public double length, width;
    public double s, v = 50, x, y, hx, hy, wait, still, breakdown;
    public Color customColor;

    // Accident & clearing state
    public boolean isAccident = false;
    public double accidentAngle = 0;
    public boolean isClearing = false;
    public Simulation.Accident targetAccident = null;

    public Vehicle(Route r, boolean emergency) {
        this(r, emergency ? Type.AMBULANCE : Type.CAR);
    }

    public Vehicle(Route r, Type type) {
        this.route = r;
        this.type = type;
        this.emergency = type.isEmergency;
        this.length = type.length;
        this.width = type.width;
        this.v = Math.min(50, type.maxSpeed * 0.6);
        if (type == Type.CAR) {
            this.customColor = randomCarColor();
        }
    }

    private static final Color[] CAR_COLORS = {
        new Color(41, 128, 240),
        new Color(230, 57, 70),
        new Color(255, 183, 3),
        new Color(67, 170, 139),
        new Color(157, 78, 221),
        new Color(247, 127, 0),
        new Color(240, 244, 248),
        new Color(52, 58, 64),
        new Color(0, 180, 216),
        new Color(239, 71, 111)
    };

    private static Color randomCarColor() {
        return CAR_COLORS[(int)(Math.random() * CAR_COLORS.length)];
    }
}
