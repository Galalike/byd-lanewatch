# BYD LaneWatch (Blind Spot Picture-in-Picture)

แอปพลิเคชันสำหรับจอ Android (DiLink) บนรถยนต์ **BYD** สำหรับแสดงภาพมุมอับสายตาจากกล้องมองข้างแบบ Picture-in-Picture (PiP) ซ้อนทับหน้าจอเมื่อผู้ขับเปิดไฟเลี้ยว

---

## 📥 ดาวน์โหลดติดตั้ง (Downloads)

| เวอร์ชัน | ประเภท | ดาวน์โหลด APK | ไฮไลท์สำคัญ |
| :--- | :---: | :--- | :--- |
| **v1.0.3** | 🛡️ **Stable (ล่าสุด)** | [⬇️ ดาวน์โหลด BYDLaneWatch_v1.0.3.apk](https://github.com/Galalike/byd-lanewatch/releases/download/v1.0.3/BYDLaneWatch_v1.0.3.apk) | ค่าแนะนำมาตรฐานจากรถจริง (OEM Preset), ตรวจอัปเดตอัตโนมัติ, Vehicle Signal Probe, และยกระดับความปลอดภัย |
| **v1.0.2** | 🛡️ **Stable** | [⬇️ ดาวน์โหลด BYDLaneWatch_v1.0.2.apk](https://github.com/Galalike/byd-lanewatch/releases/download/v1.0.2/BYDLaneWatch_v1.0.2.apk) | ปลดล็อก Autostart อัตโนมัติด้วย Physical Gesture Scrolling |

> 📌 หรือไปที่หน้า **[GitHub Releases ทั้งหมด](https://github.com/Galalike/byd-lanewatch/releases)** เพื่อเลือกดาวน์โหลดทุกเวอร์ชันย้อนหลัง

---

## 🛠️ โครงสร้างโค้ดหลัก (Key Architecture)

```
app/src/main/java/
├── android/hardware/
│   ├── JNIBMMCamera.java          # JNI Stub สำหรับเชื่อมต่อ Framework กล้องของ BYD
│   └── watermark/                 # Watermark renderer สำหรับกล้อง
└── com/nineoad/
    ├── avc/
    │   └── AvcClient.java         # ตัวเชื่อมต่อ Service กล้องรอบคันของ BYD (com.byd.action.AVCSERVICE)
    └── bydsignpicinpic/
        ├── App.java               # Application class
        ├── BootReceiver.java      # ดักจับการเปิดสวิตช์กุญแจรถ (BOOT_COMPLETED, ACC_ON)
        ├── HiddenApi.java         # ปลดล็อคข้อจำกัด Hidden API ของ Android
        ├── autoservice/
        │   ├── AutoserviceClient.java # IPC เรียกคำสั่ง service call autoservice ดักข้อมูลรถ
        │   ├── SentinelDecoder.java   # ถอดรหัสค่าตัวเลข Sentinel จากระบบรถ
        │   └── TurnFids.java          # ตารางรหัส Feature ID สัญญาณไฟเลี้ยว (Left=2/3, Right=4/5, Hazard=6)
        ├── camera/
        │   ├── CameraFeed.java        # จัดการ Surface และ Feed ภาพวิดีโอกล้อง
        │   └── PreviewTestActivity.java # หน้าทดสอบ Feed กล้องและปรับองศาหมุน
        ├── overlay/
        │   ├── FisheyeGLView.java     # OpenGL ES Shader แก้มุมมองเลนส์ตาปลา (Fisheye dewarping)
        │   └── OverlayService.java    # Foreground Service จัดการ Floating Window ลอยทับหน้าจอ
        ├── signal/
        │   ├── CarContext.java        # Context สภาพแวดล้อมรถยนต์ BYD
        │   ├── TurnProbeActivity.java # เครื่องมือ Scan ตรวจหา ID สัญญาณไฟเลี้ยวสำหรับรถรุ่นใหม่
        │   └── TurnSignalListener.java# Thread ดักฟังสัญญาณไฟเลี้ยวและหลีกทางให้เกียร์ถอย (R)
        ├── ui/
        │   └── MainActivity.java      # หน้าจอตั้งค่าการทำงาน (ภาษาไทย)
        └── update/
            └── UpdateChecker.java     # ตรวจสอบอัปเดตเวอร์ชันใหม่
```

---

## 💻 วิธีเปิดโปรเจกต์ใน Android Studio

1. เปิดโปรแกรม **Android Studio**
2. เลือก **Open** หรือ **File > Open...**
3. เลือกโฟลเดอร์โปรเจกต์:
   `/Users/dee/Desktop/BYDSignalPnP`
4. รอให้ Android Studio ทำการ Sync Gradle จนเสร็จสมบูรณ์
5. สามารถกดปุ่ม **Build > Build Bundle(s) / APK(s) > Build APK(s)** เพื่อคอมไพล์เป็นไฟล์ APK นำไปติดตั้งบนรถได้ทันที

---

## 🚗 วิธีนำไฟล์ APK ไปติดตั้งและทดสอบบนรถ BYD

1. **เปิดโหมดผู้พัฒนา (Developer Options) & USB Debugging บนจอรถ BYD**
   * เข้าการตั้งค่าระบบบนจอรถ > ซอฟต์แวร์ > กดที่เวอร์ชันซ้ำๆ 7 ครั้งจนเปิดโหมดนักพัฒนา
   * เปิด USB Debugging (หรือ Wireless ADB)
2. **ติดตั้งแอปผ่านสาย USB หรือ Wi-Fi:**
   ```bash
   adb install -r app-debug.apk
   ```
3. **ให้สิทธิ์ Overlay Window (จำเป็น):**
   * เข้าไปที่การตั้งค่าแอปบนจอรถ และเปิดสิทธิ์ **"แสดงทับแอปอื่น" (Display over other apps)**
4. **เปิดแอป BYD Signal PnP และตั้งค่า:**
   * เลือกโหมดที่ต้องการ (Safety / Driving / Balance / Custom)
   * หากเป็นรถรุ่นใหม่ที่ไฟเลี้ยวไม่ติด ให้เปิดหน้า **TurnProbeActivity** เพื่อทดสอบอ่านค่า ID สัญญาณ
