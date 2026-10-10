package traffic;

import java.awt.Color;

/**
 * Represents a pedestrian on the roadside footpath (sidewalk).
 * Can be walking, walking a pet dog on a leash, or standing and conversing in a group.
 */
public class FootpathPerson {

    public enum Activity {
        WALKING,
        WALKING_DOG,
        CONVERSING
    }

    public final Activity activity;
    public final int arm;
    public final int side; // -1 for left sidewalk, +1 for right sidewalk
    public double distAlongArm; // distance from center along arm [stop + 30 ... ARM]
    public double walkSpeed; // movement speed (pixels/sec)
    public double x, y; // computed world coordinates
    public double facingAngle;
    public double talkGestureTimer;
    public Color clothesColor;
    public Color dogColor;

    public FootpathPerson(Activity activity, int arm, int side, double dist, double speed, Color clothes, Color dog) {
        this.activity = activity;
        this.arm = arm;
        this.side = side;
        this.distAlongArm = dist;
        this.walkSpeed = speed;
        this.clothesColor = clothes;
        this.dogColor = dog;
    }

    public void update(double dt, Junction j) {
        talkGestureTimer += dt;
        int hw = j.laneCount * 22;
        double sidewalkOffset = side * (hw + 16.0);
        double[] A = j.dir[arm];
        double[] O = {-A[1], A[0]};

        if (activity == Activity.WALKING || activity == Activity.WALKING_DOG) {
            distAlongArm += walkSpeed * dt;
            double minDist = j.stop + 40.0;
            double maxDist = Junction.ARM + 20.0;

            if (distAlongArm < minDist) {
                distAlongArm = minDist;
                walkSpeed = Math.abs(walkSpeed);
            } else if (distAlongArm > maxDist) {
                distAlongArm = maxDist;
                walkSpeed = -Math.abs(walkSpeed);
            }

            // Facing direction: walking along arm
            double baseAng = j.ang[arm];
            facingAngle = walkSpeed > 0 ? baseAng : baseAng + Math.PI;
        }

        // Compute world position
        this.x = A[0] * distAlongArm + O[0] * sidewalkOffset;
        this.y = A[1] * distAlongArm + O[1] * sidewalkOffset;
    }
}
