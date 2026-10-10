# Traffic Junction Simulator (Java 17, Swing / AWT 3D)

Open the folder in IntelliJ IDEA / Eclipse as a Maven project, run `traffic.Main`.

### Features
- **Vehicle Variety**:
  - **Sedan Car**: Passenger vehicle in diverse color finishes.
  - **Taxi Cab**: Yellow cab with illuminated roof sign and checkerboard trim.
  - **Motorbike**: Agile two-wheeler with helmeted rider.
  - **Bicycle**: Slender frame with pedaling cyclist.
  - **City Bus**: Long transit vehicle with tinted passenger windows and LED route display.
  - **Cargo Truck**: Heavy semi-truck with elevated cab and container trailer.
  - **Police Cruiser**: Black/white interceptor with high-intensity siren lightbar.
  - **Ambulance**: Medical emergency van with red cross emblem and strobes.
  - **Fire Engine**: Crimson red fire truck with roof extension ladder and sirens.
- **Accident Simulation & Emergency Clearance**:
  - Accidents can occur or be triggered on-demand (`X` key or sidebar button).
  - Wrecked vehicle blocks the lane with hazard flashers, smoke plume, and traffic warning cones.
  - Following traffic in that lane jams up until the spot is cleared; adjacent lanes can be merged into if clear.
  - The system automatically dispatches an **Ambulance** or **Police** responder.
  - **Fastest Lane Identification**: Emergency vehicles scan lane congestion and dynamically switch to the fastest/clear lane to navigate around traffic jams.
  - Responder arrives at the scene, attends to the accident, tows the wreck, clears the blockage, and resumes traffic flow!
- **Pedestrian Safety & Stop Line Positioning**:
  - The stop bar is placed **before** the zebra crossing.
  - Vehicles come to a complete halt behind the stop line and never stop on top of the zebra crossing.
  - Pedestrians cross the zebra crossing with safe clearance.
- **Roadside Footpath Community Life**:
  - Paved sidewalks flanking all road arms and connecting at junction corners.
  - **Walkers**: Pedestrians walking along the sidewalk with animated strides.
  - **Dog Walkers**: People walking their pet dogs on a leash (animated dog with wagging tail and 4 legs).
  - **Conversing Groups**: Pairs of people standing and talking with animated conversational gestures.
- **3D Isometric & Perspective Rendering**: Pure Java AWT/Swing 3D engine with depth sorting (Painter's algorithm), directional sun lighting, and ambient occlusion.
- **Interactive Camera**:
  - **Left-Click + Drag**: Orbit / rotate yaw and pitch.
  - **Right-Click + Drag**: Pan camera.
  - **Mouse Wheel**: Smooth zoom in/out.
  - **Preset Modes**: 3D Isometric, 3D Cinematic Angle, Top-Down 2D.

### Keyboard Shortcuts
- **1 – 6**: Switch junction (4-way, T-junction, Y-junction, Roundabout 4, Roundabout 5, 5-way)
- **N / M / H**: Normal / Moderate / Heavy traffic density
- **F / A / E**: Signal mode (Fixed, Adaptive, Eco)
- **L**: Toggle free left turn
- **V**: Cycle camera view (`3D Isometric` $\to$ `3D Cinematic` $\to$ `Top-Down 2D`)
- **C**: Toggle camera auto-orbit
- **R**: Reset camera
- **X**: Trigger accident (blocks lane, causes traffic jam, dispatches Police/Ambulance to clear)
- **SPACE**: Spawn emergency vehicle

Credits: PAARI DK (24PD26), S SUWETHA (24PD34)
