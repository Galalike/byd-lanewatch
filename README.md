# BYD LaneWatch (Blind Spot Picture-in-Picture)

[![Platform](https://img.shields.io/badge/Platform-BYD%20DiLink%20%7C%20Android-green.svg)](https://github.com/Galalike/byd-lanewatch)
[![Latest Release](https://img.shields.io/github/v/release/Galalike/byd-lanewatch?color=blue)](https://github.com/Galalike/byd-lanewatch/releases/latest)
[![License](https://img.shields.io/badge/License-Proprietary-orange.svg)](#)

An intelligent camera overlay application tailored for **BYD DiLink** Android infotainment screens. **BYD LaneWatch** provides real-time blind spot camera feeds in a Picture-in-Picture (PiP) floating window whenever the driver signals or turns the steering wheel, seamlessly enhancing driving safety without disrupting navigation or multimedia apps.

---

## 🌟 Key Features

- **Automatic LaneWatch Overlay (PiP):** Displays the corresponding side mirror camera immediately when the left or right turn signal is activated.
- **Front-Wheel Camera Attachment:** Automatically activates front-wheel side views when turning the steering wheel past the threshold angle at low speeds (parking and narrow alley maneuvers).
- **Digital Rearview Mirror:** Dedicated full-time or on-demand digital rearview camera streaming.
- **OEM Calibrated Presets:** Pre-tuned angles, aspect ratios, and fisheye dewarping parameters calibrated directly on BYD vehicles.
- **Smart Reverse Protection:** Automatically yields to the native 360° AVM system when shifting into Reverse (`R`).
- **ADAS Blind Spot Integration:** Dynamic visual warning frames that activate only when actual obstacles or vehicles are detected in the blind zone.
- **Self-Healing Autostart:** Automatically resolves BYD DiLink background restrictions to ensure the service runs continuously upon vehicle start.
- **Built-in OTA Update Checker:** Notifies and downloads the latest releases directly from within the app.

---

## 📥 Downloads

| Version | Channel | Direct APK Download | Release Highlights |
| :--- | :---: | :--- | :--- |
| **v1.0.7-beta** | 🧪 **Beta (Latest)** | [⬇️ **Download BYDLaneWatch_v1.0.7-beta.apk**](https://github.com/Galalike/byd-lanewatch/releases/download/v1.0.7-beta/BYDLaneWatch_v1.0.7-beta.apk) | Universal Atto 3 / Dolphin black screen fix, multi-camera candidate discovery, DiLink 4 safe HAL path, watchdog clock sync |
| **v1.0.6** | 🛡️ **Stable** | [⬇️ Download BYDLaneWatch_v1.0.6.apk](https://github.com/Galalike/byd-lanewatch/releases/download/v1.0.6/BYDLaneWatch_v1.0.6.apk) | Global localization (English base, auto Thai default on Thai cars), Dolphin black screen fix (ID 0/1 auto-detect), persistent expanded PIP view |
| **v1.0.5** | 🛡️ **Stable** | [⬇️ Download BYDLaneWatch_v1.0.5.apk](https://github.com/Galalike/byd-lanewatch/releases/download/v1.0.5/BYDLaneWatch_v1.0.5.apk) | Modern Noto Sans Thai typography, full weight support, enhanced touchscreen legibility |
| **v1.0.4** | 🛡️ **Stable** | [⬇️ Download BYDLaneWatch_v1.0.4.apk](https://github.com/Galalike/byd-lanewatch/releases/download/v1.0.4/BYDLaneWatch_v1.0.4.apk) | Autostart recovery & duplicate lock, front-wheel camera steering mode, instant aspect calibration |
| **v1.0.3** | 🛡️ **Stable** | [⬇️ Download BYDLaneWatch_v1.0.3.apk](https://github.com/Galalike/byd-lanewatch/releases/download/v1.0.3/BYDLaneWatch_v1.0.3.apk) | Recommended OEM calibrated presets, auto OTA check, vehicle signal probe |
| **v1.0.2** | 🛡️ **Stable** | [⬇️ Download BYDLaneWatch_v1.0.2.apk](https://github.com/Galalike/byd-lanewatch/releases/download/v1.0.2/BYDLaneWatch_v1.0.2.apk) | Autostart unblock automation via physical gesture scrolling |

> 📌 Browse all previous versions and change logs on the **[GitHub Releases Page](https://github.com/Galalike/byd-lanewatch/releases)**.

---

## 🚗 How to Install on Your BYD Vehicle

Installing BYD LaneWatch on your car is straightforward and does not require complex tools:

1. **Download the APK:**
   - **Option A (Direct on Car):** Open the built-in Web Browser on your BYD infotainment screen, navigate to this page, and tap the download link for the latest APK.
   - **Option B (USB Flash Drive):** Download the APK file on your computer or phone, copy it to a standard USB flash drive (FAT32/NTFS), and plug it into the vehicle's USB port.

2. **Install the Package:**
   - Open the **File Manager** (or Downloads) app on the BYD screen.
   - Locate `BYDLaneWatch_v1.0.6.apk` (or the downloaded version) and tap to install.
   - If prompted to allow installation from unknown sources, select **Allow**.

3. **Grant Required Permissions:**
   - Open **BYD LaneWatch** from the vehicle's app launcher.
   - **Display Over Other Apps (Overlay):** Tap the prompt and toggle the permission to **ON**.
   - **Accessibility Service:** Tap to enable `BYD LaneWatch Service` (used for steering wheel button remap and automatic autostart configuration).

4. **Ready to Drive:**
   - The application comes pre-configured with recommended OEM vehicle presets.
   - The background monitoring service automatically starts and remains active whenever the car is turned on.

---

## 🛠️ Architecture & Core Components

```
app/src/main/java/
├── android/hardware/
│   ├── JNIBMMCamera.java          # JNI bridge for BYD camera HAL & framework
│   └── watermark/                 # Hardware camera watermark renderer
└── com/nineoad/
    ├── avc/
    │   └── AvcClient.java         # BYD 360 AVC camera service connector (com.byd.action.AVCSERVICE)
    └── bydsignpicinpic/
        ├── App.java               # Global application state and lifecycle
        ├── BootReceiver.java      # Boot & ignition receiver (BOOT_COMPLETED, ACC_ON, MY_PACKAGE_REPLACED)
        ├── HiddenApi.java         # Android hidden API bypass for system integrations
        ├── autoservice/
        │   ├── AutoserviceClient.java # IPC connector for vehicle signal telemetry
        │   ├── SentinelDecoder.java   # Telemetry decoder for CAN signal frames
        │   └── TurnFids.java          # Signal ID table (Turn Signals, Hazard, Steering, Speed)
        ├── camera/
        │   ├── CameraFeed.java        # Camera surface allocator & video feed manager
        │   └── PreviewTestActivity.java # Visual diagnostic & orientation calibration tool
        ├── overlay/
        │   ├── FisheyeGLView.java     # OpenGL ES shader for fisheye lens dewarping & aspect scaling
        │   ├── OverlayService.java    # Core foreground service managing the floating PiP window
        │   └── OemPanoProfile.java    # OEM surround camera resources & calibrations
        ├── signal/
        │   ├── BydKeyRemapService.java# Accessibility service for wheel buttons & autostart automation
        │   ├── TurnSignalListener.java# High-frequency signal polling with reverse-gear detection
        │   └── AdasBlindSpotMonitor.java # ADAS BSD radar signal evaluator
        ├── ui/
        │   ├── MainActivity.java      # Main control & configuration dashboard
        │   └── CameraStudioActivity.java # Real-time interactive camera alignment studio
        └── update/
            └── UpdateChecker.java     # OTA version verification & background downloader
```

---

## 📄 License & Disclaimer

- **Disclaimer:** This software is an independent third-party tool developed for BYD DiLink systems. Drive safely and always verify your surroundings using your vehicle's physical mirrors and built-in safety systems.
