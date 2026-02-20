# BtRemote 📱➡️💻

[![Build Android APK](https://github.com/UsmanXTech/BtRemote/actions/workflows/build-apk.yml/badge.svg)](https://github.com/UsmanXTech/BtRemote/actions/workflows/build-apk.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**BtRemote** is a powerful, open-source Android application developed by **UsmanXTech** that transforms your smartphone into a versatile Bluetooth Human Interface Device (HID). It allows you to control your PC, Smart TV, or Tablet without any additional software on the receiving device.

## 🚀 Key Features

*   **No Server Required:** Uses the standard Bluetooth HID profile. If your device supports Bluetooth keyboards/mice, it works with BtRemote.
*   **Air Mouse:** Control your cursor using your phone's gyroscope (motion sensing).
*   **Touchpad:** High-precision trackpad with multi-touch support and scrolling.
*   **Live Keyboard:** Real-time character transmission. What you type on your phone appears instantly on the remote screen.
*   **Voice-to-Type:** Dictate text using your phone's microphone and have it "typed" live on your paired device.
*   **Multimedia Controls:** Dedicated layouts for volume, playback, and navigation.
*   **Customizable UI:** Support for Material You dynamic colors, Dark/Black themes, and multiple keyboard layouts.

## 🛠 Tech Stack

*   **Language:** 100% Kotlin
*   **UI Framework:** Jetpack Compose (Modern, declarative UI)
*   **Dependency Injection:** Koin
*   **Persistence:** Jetpack DataStore
*   **Asynchrony:** Kotlin Coroutines & Flow
*   **Architecture:** Clean Architecture with MVVM pattern

## 📦 Installation & Build

### Download
You can download the latest **Debug APK** directly from the [GitHub Actions](https://github.com/UsmanXTech/BtRemote/actions) artifacts.

### Manual Build
1. Clone the repository:
   ```bash
   git clone https://github.com/UsmanXTech/BtRemote.git
   ```
2. Open in Android Studio (Ladybug or newer recommended).
3. Build using Gradle:
   ```bash
   ./gradlew assembleDebug
   ```

## 🤝 Contributing

BtRemote is an open-source project by **UsmanXTech**. Contributions are welcome!
1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.

---
Developed with ❤️ by [UsmanXTech](https://github.com/UsmanXTech)
