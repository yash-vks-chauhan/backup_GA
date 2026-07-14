# Gridee Low-Cost ANPR Gate Prototype

Hardware list, build plan, product direction, and presentation notes

Prepared for: Gridee student team

Date: June 2, 2026

---

## 1. Executive Decision

Yes, we can build a cheap and useful ANPR-style system for parking gates.

The correct target is not highway-grade ANPR. The first target should be parking-gate ANPR, where vehicles move slowly, the lane is controlled, the camera angle is fixed, and lighting can be controlled. This is realistic for a student team and can become a separate sellable product later.

The first product should be:

**A low-cost camera and edge-computer box that reads vehicle plates at a parking gate, sends plate events to the Gridee backend, and lets the operator handle only uncertain cases.**

The product should not promise full automation on day one. The safer roadmap is:

1. Shadow mode: camera reads plates and logs events, but the operator still does manual check-in.
2. Assisted mode: detected plate appears on the operator screen for one-tap confirmation.
3. Automatic mode: backend auto check-in/check-out only when confidence is high and the booking match is exact.

---

## 2. What We Are Going To Build

We are going to build a low-cost ANPR gate system for Gridee.

### Main Goal

When a vehicle reaches the parking entry or exit gate:

1. A fixed IP camera captures the vehicle plate.
2. An edge computer reads the camera stream.
3. Our software detects the plate area.
4. OCR reads the plate text.
5. Indian vehicle-number normalization fixes common OCR mistakes.
6. The backend matches the plate to a pending or active booking.
7. The system checks the vehicle in or out, or asks the operator to confirm.

### Simple Architecture

```text
Vehicle at gate
    |
    v
PoE IP camera
    |
    v
Edge computer running our ANPR software
    |
    |  plate, confidence, cameraId, gateId, image crop
    v
Gridee backend ANPR event API
    |
    v
Booking match and check-in/check-out
    |
    v
Operator app shows success or asks for manual correction
```

---

## 3. What We Are Not Building Yet

To keep this cheap and achievable, we are not building these in the first version:

- Highway-speed number plate recognition.
- Full traffic-police-grade enforcement.
- Automatic barrier opening without operator fallback.
- A fully custom AI model trained from zero.
- Recognition for every rare/special Indian plate type on day one.

The first version should work well for normal parking entry/exit lanes.

---

## 4. Existing Gridee Codebase Advantage

The project is already close to this idea.

### Existing Android Flow

The Android app already has a vehicle plate scanner:

- `QrScannerActivity.kt` uses CameraX and ML Kit Text Recognition.
- It extracts plate candidates from camera frames.
- It normalizes Indian vehicle numbers.
- It calls operator check-in/check-out by vehicle number.

Relevant files:

- `Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/qr/QrScannerActivity.kt`
- `Gridee_Android/android-app/app/src/main/java/com/gridee/parking/ui/operator/OperatorViewModel.kt`
- `Gridee_Android/android-app/app/src/main/java/com/gridee/parking/utils/VehicleNumberValidator.kt`

### Existing Backend Flow

The backend already has operator endpoints:

- `POST /api/operator/bookings/checkin`
- `POST /api/operator/bookings/checkout`

Relevant files:

- `gridee-backend/src/main/java/com/parking/app/controller/operator/OperatorBookingController.java`
- `gridee-backend/src/main/java/com/parking/app/service/booking/CheckInService.java`
- `gridee-backend/src/main/java/com/parking/app/service/booking/CheckOutService.java`

### What This Means

We do not need to reinvent the whole parking system. The new edge ANPR box can become another scan source that sends plate events to the backend.

---

## 5. Recommended Hardware To Buy

Prices are approximate India street prices checked around June 2, 2026. Confirm stock and GST before ordering.

### Recommended Student MVP Kit

| Part | What It Is Used For | Recommended Item | Approx Price | Buy Link | Notes |
|---|---|---|---:|---|---|
| Camera | Captures the vehicle number plate at entry/exit | TP-Link VIGI C340I 4MP 6mm PoE bullet camera | Rs 3,000 approx | [Moglix product page](https://www.moglix.com/tp-link-vigi-c340i-4mp-6mm-outdoor-ir-bullet-network-camera/mp/msnr50n8m6d251) | Best if available. The 6mm lens is better for one gate lane than a very wide lens. |
| Camera alternative | Easier local CCTV availability | CP PLUS 4MP IP Bullet CP-UNC-TA41L3C-D-Q | Rs 3,549 | [FGTECH Store](https://fgtechstore.com/product/cp-plus-cp-unc-ta41l3c-d-q/) | Good India-friendly fallback. 4MP, PoE, ONVIF, IP67, IR 30m. |
| Cheapest camera | Lowest-cost prototype camera | CP PLUS 2MP IP Bullet CP-UNC-TA21L3C-Q | Rs 2,549 | [FGTECH Store](https://fgtechstore.com/product/cp-plus-cp-unc-ta21l3c-q/) | Use only if budget is very tight. 4MP is preferred for OCR. |
| Edge computer | Runs OpenCV, plate detector, OCR, and backend event sender | Refurb HP ProDesk 400 G4 Mini, i5 8th gen, 8GB RAM, 256GB SSD | Rs 14,500 | [Mtronics](https://mtronics.in/product/hp-prodesk-400-g4-tiny-refurbished/) | Best student choice. Easier than Raspberry Pi/Jetson because Python, OpenCV, Docker, and OCR run cleanly on x86 Linux. |
| PoE switch | Powers the PoE camera and connects it to the edge computer | Mercusys MS105GP 5-port gigabit PoE switch | Rs 1,796 to Rs 2,263 | [Moglix product page](https://www.moglix.com/mercusys-ms105gp-5-ports-10-100-1000-mbps-desktop-network-switch/mp/msn85804jlwl92) | One switch can power the camera and leave spare ports for future expansion. |
| Lighting | Gives stable plate lighting at night and indoors | Forus 20W IP67 cool-white LED flood light | Rs 869 | [Moglix product page](https://www.moglix.com/forus-20w-6kv-aluminium-cool-white-led-flood-light/mp/msnrkreg2j039n) | Mount off-axis so it does not reflect directly from the plate. |
| Better night lighting | Invisible or semi-invisible night illumination | 850nm IR illuminator, 96 LED | Rs 5,999 approx | [Bshopy product page](https://bshopy.in/products/96-led-infrared-illuminator-ip65-waterproof-ir-light-for-outdoor-cctv-security-cameras) | Optional upgrade. Needs testing with the chosen camera. |
| Weatherproof box | Protects switch, power adapters, relay, connectors | 300x200x125mm IP65 ABS enclosure | Rs 1,602 | [ComponentsTree](https://componentstree.com/product/300x200x125mm-ip65-waterproof-plastic-enclosure/) | Needed for outdoor installation. |
| Extra storage | Stores plate crops, logs, and training data | Crucial BX500 500GB SATA SSD | Rs 2,700 approx | [Moglix product page](https://www.moglix.com/crucial-bx500-500gb-25-inch-3d-nand-internal-ssd-for-desktop-laptop-ct500bx500ssd1/mp/msn39yrz7x325q) | Optional for MVP if the mini PC already has 256GB SSD. Useful for dataset collection. |
| Optional relay | Sends dry-contact signal to gate controller | 5V 1-channel opto relay module | Rs 30 to Rs 50 | [Robu relay category](https://robu.in/product-tag/1-channel-relay-module/) | Do not switch gate motor power directly. Use only dry contact through proper isolation. |
| Easier relay alternative | Relay controlled directly from mini PC USB | 1-channel USB relay module | Rs 3,000 approx | [Ajitek Solutions](https://store.ajiteksolutions.com/product/1-channel-usb-powered-relay-module/) | Easier but expensive. Keep relay for later product version. |

### Small Items To Buy Locally

| Item | Use | Approx Price |
|---|---|---:|
| CAT6 outdoor Ethernet cable | Connect camera, PoE switch, router, and edge computer | Rs 500 to Rs 1,500 |
| Camera wall/pole bracket | Mount camera at fixed angle | Rs 300 to Rs 1,000 |
| Pole clamp or metal strip | Mount camera/enclosure to gate pole | Rs 200 to Rs 700 |
| 6A extension board/surge protector | Power edge PC and PoE switch | Rs 400 to Rs 1,000 |
| Basic UPS, optional | Keeps system alive during short power cuts | Rs 2,500 to Rs 4,000 |
| Printed demo plates | Testing and presentation | Rs 100 to Rs 500 |
| "ANPR camera in use" sign | Privacy and transparency at gate | Rs 200 to Rs 500 |

---

## 6. Budget Options

### Option A - Zero/Very Low-Cost Demo

Use the existing Android phone scanner.

Approx cost: Rs 0 to Rs 500

Use this for:

- College presentation.
- Early demo.
- Validating backend booking flow.
- Showing that Gridee already supports plate-based check-in/check-out.

Limitations:

- Not a fixed camera system.
- Still needs operator to hold the phone.
- Not a sellable hardware product yet.

### Option B - Recommended Student MVP

Use:

- CP PLUS or TP-Link 4MP PoE camera.
- Refurb i5 mini PC.
- PoE switch.
- LED flood light.
- IP65 enclosure.

Approx cost: Rs 22,000 to Rs 28,000 before installation.

This is the best first hardware build.

### Option C - Better Product Prototype

Use:

- Better 4MP/5MP PoE camera with WDR or starlight.
- Refurb i5/i7 mini PC or Intel N100 mini PC.
- Better IR/LED lighting.
- Better enclosure and UPS.
- Optional USB relay.

Approx cost: Rs 30,000 to Rs 45,000 per lane.

This is better for pilots with real parking lots.

### Option D - High-Performance AI Version

Use:

- NVIDIA Jetson Orin Nano Super.
- Better camera and IR lighting.
- GPU-optimized model.

Approx cost: Rs 55,000+ per lane.

This is not the student-budget first choice.

---

## 7. Alternative AI Hardware

| Hardware | Approx India Price | Use | Recommendation |
|---|---:|---|---|
| Raspberry Pi 5 8GB | Rs 19,549 | Small Linux computer | Not first choice in India right now because the price is high after adding power, case, storage, and cooling. [Buy link](https://robocraze.com/products/raspberry-pi-5-8gb) |
| Raspberry Pi Camera Module 3 | Rs 3,099 | Pi camera module | Good for lab prototype, harder for outdoor product. [Buy link](https://robocraze.com/products/raspberry-pi-camera-module-3) |
| Raspberry Pi Global Shutter Camera | Rs 5,846 | Reduces motion blur | Good camera, but needs lens/enclosure work. [Buy link](https://robocraze.com/products/raspberry-pi-global-shutter-camera) |
| Raspberry Pi AI Camera | Rs 8,431 | Camera with Sony IMX500 AI sensor | Interesting, but not necessary for first ANPR product. [Buy link](https://robocraze.com/products/official-raspberry-pi-ai-camera-with-sony-imx500-sensor) |
| NVIDIA Jetson Orin Nano Super | Rs 38,999 | GPU edge AI | Powerful but expensive for a student MVP. [Buy link](https://robocraze.com/products/nvidia-jetson-orin-nano-super-8gb-67tops-development-kit) |

### Why Mini PC Is Recommended First

A refurbished mini PC is the simplest and cheapest serious platform for this project because:

- Python, OpenCV, YOLO, PaddleOCR, Tesseract, Docker, and backend clients are easier to run.
- It has SSD storage.
- It can handle one camera stream.
- It is easy to debug over keyboard/monitor/SSH.
- No model-conversion work is needed at the start.

---

## 8. Software We Will Build

### Module 1 - Camera Stream Reader

Purpose:

Read live video from the IP camera.

Input:

- RTSP stream from IP camera.

Output:

- Video frames for plate detection.

Tools:

- Python
- OpenCV
- RTSP/ONVIF camera URL

### Module 2 - Plate Detector

Purpose:

Find the number plate region in the frame.

Input:

- Full camera frame.

Output:

- Cropped image of plate.

Tools:

- YOLO small model, exported to ONNX.
- Start with a pre-trained license-plate detection model.
- Later fine-tune using our parking-gate images.

### Module 3 - OCR Reader

Purpose:

Read the text from the cropped plate.

Input:

- Cropped plate image.

Output:

- Raw text candidates.

Tools:

- PaddleOCR, EasyOCR, Tesseract, or a small CRNN model.
- For MVP, compare PaddleOCR and Tesseract and choose whichever works better on our real images.

### Module 4 - Indian Plate Normalizer

Purpose:

Convert raw OCR text into a clean Indian vehicle number.

Examples:

- Remove spaces, hyphens, dots, slashes.
- Remove `IND` if detected as part of plate.
- Convert to uppercase.
- Correct OCR mistakes position-wise: `O` vs `0`, `I` vs `1`, `S` vs `5`, `B` vs `8`.
- Validate against Indian formats from `vehicleplate.md`.

We should reuse the logic from:

- `VehicleNumberValidator.kt`
- `vehicleplate.md`

### Module 5 - Decision Layer

Purpose:

Avoid false check-ins.

Rules:

- Do not act on one weak OCR result.
- Require the same plate across multiple frames.
- Use confidence threshold.
- Apply duplicate cooldown.
- If uncertain, send to operator review.

Example rule:

```text
If same normalized plate appears in 3 of last 8 frames
AND OCR confidence is above threshold
AND backend finds exactly one matching booking
THEN create ANPR event and proceed.
ELSE show operator fallback.
```

### Module 6 - Backend Event Sender

Purpose:

Send detected plate events to Gridee backend.

New API:

```http
POST /api/edge/anpr/events
```

Payload:

```json
{
  "eventId": "camera-01-20260602-183500-001",
  "deviceId": "edge-box-01",
  "cameraId": "entry-cam-01",
  "lotId": "lot_123",
  "spotId": "spot_456",
  "gateId": "entry-gate-1",
  "direction": "ENTRY",
  "plateRaw": "TN 09 AB 1234",
  "plateNormalized": "TN09AB1234",
  "confidence": 0.91,
  "capturedAt": "2026-06-02T18:35:00+05:30",
  "imageCropUrl": "local-or-cloud-url"
}
```

### Module 7 - Operator Exception View

Purpose:

Let the operator correct uncertain cases.

States:

- Auto matched.
- Needs confirmation.
- No booking found.
- Multiple possible bookings.
- Low confidence OCR.
- Network error.

---

## 9. Backend Changes Needed

The current backend supports operator check-in/check-out, but full edge ANPR should not pretend to be a human operator.

### Required Backend Work

1. Add `ANPR` to `CheckInMode`.
2. Add `AnprEvent` model/collection.
3. Add `/api/edge/anpr/events`.
4. Add edge-device authentication.
5. Add exact normalized plate matching.
6. Add lot/gate/spot/time-window-aware matching.
7. Add idempotency using `deviceId + eventId`.
8. Add audit logs for every automatic decision.
9. Add operator review state for uncertain cases.

### Important Fix Before Automation

The current backend vehicle-number lookup uses case-insensitive regex on raw `vehicleNumber`. That is acceptable for manual operator flows, but risky for unattended ANPR.

For ANPR, we should store and match:

- `plateRaw`
- `plateNormalized`
- `plateType`
- `lotId`
- `spotId` or `gateId`
- `status`
- scheduled time window

The backend should only auto act when there is exactly one valid booking match.

---

## 10. How We Will Build It

### Phase 0 - Phone Demo Using Existing App

Duration: 1 to 2 days

Goal:

Show that plate-based check-in/check-out already works in Gridee.

Tasks:

1. Create one operator user.
2. Create one test parking lot and spot.
3. Create one booking with a known vehicle number.
4. Use the Android scanner to scan a printed plate.
5. Confirm backend check-in.
6. Switch to checkout mode and complete checkout.

Deliverable:

Live demo that proves the workflow.

### Phase 1 - Edge Camera Prototype

Duration: 5 to 7 days

Goal:

Read plates from an IP camera and print detected plates on the edge computer.

Tasks:

1. Mount IP camera on tripod or gate pole.
2. Connect camera to PoE switch.
3. Connect mini PC to the same network.
4. Open camera RTSP stream in Python.
5. Save sample frames.
6. Run plate detector.
7. Crop plate image.
8. Run OCR.
9. Normalize plate.
10. Log result to CSV.

Deliverable:

Edge box detects and logs vehicle plates.

### Phase 2 - Backend Integration

Duration: 5 to 7 days

Goal:

Send ANPR events to the backend and match bookings.

Tasks:

1. Add `AnprEvent` backend model.
2. Add edge API endpoint.
3. Add normalized plate matching.
4. Add idempotency.
5. Add confidence threshold.
6. Connect edge software to backend.
7. Test pending booking to active booking.
8. Test active booking to completed booking.

Deliverable:

Camera event can check in/out a booking in assisted mode.

### Phase 3 - Operator Confirmation

Duration: 3 to 5 days

Goal:

Make the system safe for real use.

Tasks:

1. Show detected plate and crop in operator dashboard.
2. Let operator approve or correct.
3. Add reason labels: low confidence, no booking, multiple matches.
4. Keep QR/manual flow as fallback.

Deliverable:

Operator handles only uncertain cases.

### Phase 4 - Field Testing

Duration: 1 to 2 weeks

Goal:

Measure if the system is good enough for real parking use.

Tasks:

1. Test day, night, rain/dust, angled plates, two-wheelers, cars.
2. Record every attempt.
3. Measure accuracy and latency.
4. Tune camera angle, lighting, OCR correction, threshold.
5. Decide when to allow auto mode.

Deliverable:

Pilot report with metrics and failure cases.

---

## 11. Team Roles

| Role | Owner | Work |
|---|---|---|
| Hardware and installation | Student 1 | Camera, PoE, lighting, enclosure, mounting, wiring |
| Computer vision | Student 2 | Frame capture, plate detector, OCR, confidence logic |
| Backend | Student 3 | ANPR API, event storage, matching, idempotency, audit |
| Android/operator UI | Student 4 | Confirmation screen, fallback flow, operator dashboard |
| Testing and presentation | Student 5 | Dataset, metrics sheet, demo flow, slides, video |

If the team has fewer people, combine backend + Android and hardware + testing.

---

## 12. Testing Plan

### Dataset To Collect

Minimum MVP dataset:

- 100 clear daytime plate captures.
- 50 night captures.
- 30 angled captures.
- 30 two-wheeler captures.
- 30 low-light or glare captures.

For each test, record:

- Expected plate.
- Detected plate.
- Correct/incorrect.
- Confidence.
- Distance.
- Lighting.
- Camera angle.
- Time taken.
- Whether manual correction was needed.

### Success Metrics

| Metric | MVP Target | Product Target |
|---|---:|---:|
| Exact plate read accuracy | 80%+ | 95%+ |
| Assisted success after manual correction | 95%+ | 99%+ |
| Scan-to-result time | Under 3 sec | Under 1.5 sec |
| Wrong auto check-in | 0 allowed | 0 allowed |
| Duplicate trigger prevention | 100% in demo | 100% in pilot |
| Uptime during demo | 90%+ | 99%+ |

Important rule:

It is better to ask the operator for confirmation than to check in the wrong vehicle.

---

## 13. Product Packaging Idea

What we can sell later:

**Gridee Edge ANPR Kit**

Includes:

- PoE camera.
- Edge mini PC.
- Pre-installed Gridee ANPR software.
- Camera mounting guide.
- Backend integration.
- Operator dashboard.
- Audit logs.
- Optional gate relay.
- Installation support.

Potential buyer:

- Small parking lots.
- Colleges.
- Apartment parking.
- Mall parking.
- Private offices.
- Event parking.

Possible pricing model:

- Hardware kit: Rs 35,000 to Rs 60,000 per lane depending on hardware.
- Setup fee: Rs 5,000 to Rs 20,000.
- Software/support subscription: Rs 1,000 to Rs 5,000 per lane per month.

---

## 14. Presentation Structure

Use this as the slide flow.

### Slide 1 - Title

Low-Cost ANPR Gate Automation for Gridee

### Slide 2 - Problem

Manual vehicle checking causes queues, operator effort, and slower entry/exit.

### Slide 3 - Our Solution

A low-cost camera + edge AI box that reads plates and connects to the existing Gridee booking backend.

### Slide 4 - Why It Is Possible

Parking gates are controlled environments:

- Slow vehicles.
- Fixed camera.
- Fixed lane.
- Controlled lighting.
- Backend already supports vehicle-number check-in/out.

### Slide 5 - Hardware Kit

Show:

- 4MP PoE camera.
- Refurb mini PC.
- PoE switch.
- LED/IR light.
- IP65 box.

### Slide 6 - Software Pipeline

Camera stream -> plate detection -> OCR -> Indian plate correction -> backend event -> operator confirmation.

### Slide 7 - Integration With Gridee

Existing Android scanner and backend operator endpoints reduce development effort.

### Slide 8 - Demo

1. Create booking.
2. Show vehicle plate.
3. Camera reads plate.
4. Backend matches booking.
5. Check-in success.
6. Checkout success.

### Slide 9 - Budget

Student MVP: Rs 22,000 to Rs 28,000.

Product prototype: Rs 30,000 to Rs 45,000.

### Slide 10 - Safety And Fallback

Manual confirmation for low confidence, QR fallback, audit log for every event.

### Slide 11 - Roadmap

Phone demo -> edge prototype -> assisted mode -> automatic mode -> sellable kit.

### Slide 12 - Ask

Need pilot location, test vehicles, hardware budget, and permission to collect plate images for testing.

---

## 15. Main Risks And Mitigations

| Risk | Why It Matters | Mitigation |
|---|---|---|
| Bad lighting | OCR fails or reads wrong characters | Use fixed LED/IR light and tune camera exposure |
| Glare from plate | White/yellow plates reflect light | Mount light off-axis |
| Wide camera angle | Plate has too few pixels | Use 4MP camera and 6mm lens if possible |
| OCR confusion | O/0, I/1, S/5 mistakes | Use Indian plate format correction |
| Wrong booking match | Could check in wrong vehicle | Exact normalized matching, lot/spot/time filters, operator confirmation |
| Multiple bookings for same vehicle | Backend ambiguity | Require selected gate/spot/time window and show operator review |
| Network failure | Event cannot reach backend | Queue events locally and retry |
| Privacy concern | Plates and images are sensitive | Signage, short retention, crop only plate, audit access |
| Gate safety | Barrier could hit vehicle/person | Do not automate barrier in MVP; add relay only after safety review |

---

## 16. Final Recommendation

Use this exact first build:

1. TP-Link VIGI C340I 4MP 6mm PoE camera if available.
2. If not available, use CP PLUS 4MP IP Bullet CP-UNC-TA41L3C-D-Q.
3. Refurb HP ProDesk 400 G4 Mini i5 8th gen, 8GB RAM, 256GB SSD.
4. Mercusys MS105GP PoE switch.
5. Forus 20W IP67 LED flood light.
6. IP65 ABS enclosure.
7. CAT6 cable, camera bracket, extension board.
8. No automatic gate relay in MVP.

Build phone demo first, then edge camera prototype, then assisted operator workflow. Full automatic check-in should only be enabled after accuracy is measured in real parking conditions.

---

## 17. Source Links Used

- TP-Link VIGI C340I 4MP 6mm PoE camera: https://www.moglix.com/tp-link-vigi-c340i-4mp-6mm-outdoor-ir-bullet-network-camera/mp/msnr50n8m6d251
- CP PLUS 4MP IP Bullet CP-UNC-TA41L3C-D-Q: https://fgtechstore.com/product/cp-plus-cp-unc-ta41l3c-d-q/
- CP PLUS 2MP IP Bullet CP-UNC-TA21L3C-Q: https://fgtechstore.com/product/cp-plus-cp-unc-ta21l3c-q/
- HP ProDesk 400 G4 Mini refurbished: https://mtronics.in/product/hp-prodesk-400-g4-tiny-refurbished/
- Mercusys MS105GP PoE switch: https://www.moglix.com/mercusys-ms105gp-5-ports-10-100-1000-mbps-desktop-network-switch/mp/msn85804jlwl92
- Forus 20W IP67 LED flood light: https://www.moglix.com/forus-20w-6kv-aluminium-cool-white-led-flood-light/mp/msnrkreg2j039n
- 850nm IR illuminator: https://bshopy.in/products/96-led-infrared-illuminator-ip65-waterproof-ir-light-for-outdoor-cctv-security-cameras
- IP65 enclosure: https://componentstree.com/product/300x200x125mm-ip65-waterproof-plastic-enclosure/
- Crucial BX500 500GB SSD: https://www.moglix.com/crucial-bx500-500gb-25-inch-3d-nand-internal-ssd-for-desktop-laptop-ct500bx500ssd1/mp/msn39yrz7x325q
- Robu relay category: https://robu.in/product-tag/1-channel-relay-module/
- Raspberry Pi 5 8GB: https://robocraze.com/products/raspberry-pi-5-8gb
- Raspberry Pi Camera Module 3: https://robocraze.com/products/raspberry-pi-camera-module-3
- Raspberry Pi Global Shutter Camera: https://robocraze.com/products/raspberry-pi-global-shutter-camera
- Raspberry Pi AI Camera: https://robocraze.com/products/official-raspberry-pi-ai-camera-with-sony-imx500-sensor
- NVIDIA Jetson Orin Nano Super: https://robocraze.com/products/nvidia-jetson-orin-nano-super-8gb-67tops-development-kit

