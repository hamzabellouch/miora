# Miora

<h3 align="center"> Modern, Privacy-First Offline P2P File & Media Sharing Application for Android.</h3>

<p align="center">
Share high-resolution photos, 4K videos, audio, documents, and large files seamlessly over Wi-Fi Direct, Local Hotspot, Bluetooth, and QR pairing — 100% offline with zero cloud servers.
</p>

<img width="2724" height="1536" alt="Miora" src="https://github.com/user-attachments/assets/c741fb2b-e1e1-445c-b417-f5b9c1d63f5e" />



## Overview

**Miora** is a modern, high-performance, and privacy-focused Android application designed for direct peer-to-peer (P2P) file and media sharing between nearby Android devices without requiring an internet connection, cloud uploads, or user accounts.

Unlike traditional file-sharing apps bloated with ads, cloud relays, and background trackers, Miora operates **100% offline and device-to-device**. It combines a custom high-speed TCP streaming engine (`MIORA_TRANSFER_V1`) with multi-protocol discovery — including **Wi-Fi Direct**, **LocalOnlyHotspot**, **Bluetooth Classic (RFCOMM)**, **Bluetooth Low Energy (BLE)**, **mDNS / NSD**, and **UDP LAN Beacons** — alongside instant **CameraX QR Code Pairing** for effortless connection setup.

Miora is built following **Modern Android Development (MAD)** standards with Kotlin and Jetpack Compose to ensure maximum throughput, battery efficiency, and a clean Material Design 3 user interface with Dynamic Color and AMOLED High-Contrast support.



## Screenshots

Miora UI & Features:

<div align="center">
  <div>
    <img src="https://github.com/hamzabellouch/miora/blob/main/Images/1.jpg" width="30%" />
    <img src="https://github.com/hamzabellouch/miora/blob/main/Images/2.jpg" width="30%" />
    <img src="https://github.com/hamzabellouch/miora/blob/main/Images/3.jpg" width="30%" />
    <img src="https://github.com/hamzabellouch/miora/blob/main/Images/4.jpg" width="30%" />
    <img src="https://github.com/hamzabellouch/miora/blob/main/Images/5.jpg" width="30%" />
    <img src="https://github.com/hamzabellouch/miora/blob/main/Images/6.jpg" width="30%" />
    <img src="https://github.com/hamzabellouch/miora/blob/main/Images/7.jpg" width="30%" />
    <img src="https://github.com/hamzabellouch/miora/blob/main/Images/8.jpg" width="30%" />
    <img src="https://github.com/hamzabellouch/miora/blob/main/Images/9.jpg" width="30%" />
    <img src="https://github.com/hamzabellouch/miora/blob/main/Images/10.jpg" width="30%" />
    <img src="https://github.com/hamzabellouch/miora/blob/main/Images/11.jpg" width="30%" />
    <img src="https://github.com/hamzabellouch/miora/blob/main/Images/12.jpg" width="30%" />
    <img src="https://github.com/hamzabellouch/miora/blob/main/Images/13.jpg" width="30%" />
  </div>
</div>

<br>



## ⭐ Key Features & Capabilities

| Feature | Method / API Used | Performance & Speed | Privacy & Safety Level | Description |
| :--- | :--- | :--- | :--- | :--- |
| **Ultra-Fast Wi-Fi Direct & Hotspot Transfers** | `DeviceConnectivityManager`, `WifiP2pManager` & `LocalOnlyHotspot` | **Up to 250 Mbps** | **100% Offline P2P** | Establishes direct peer-to-peer Wi-Fi groups or local software hotspots to stream 4K videos, photo albums, and large files without a router or internet. |
| **Custom High-Speed Socket Transfer Engine** | `MioraTransferEngine` (`MIORA_TRANSFER_V1` over TCP Port `8988`) | **128 KB Buffered Streaming** | **Explicit Consent Handshake** | Streams multiple files sequentially with real-time throughput metrics, byte-level progress tracking, and mandatory recipient accept/decline verification (`IncomingRequest`). |
| **Multi-Layer Nearby Device Discovery** | `NsdManager` (`_miora._tcp`), UDP Broadcast (`8989`), BLE & TCP Sweep | **Real-Time Discovery** | **Local Network / Proximity Only** | Combines mDNS service discovery, UDP subnet beacons, Bluetooth Low Energy advertisements, and multithreaded LAN sweeps for instant peer detection. |
| **Bluetooth Classic & Smart Auto Fallback** | `BluetoothAdapter`, RFCOMM Sockets & `TransferProtocol` | **Low-Power Mode** | **100% Offline P2P** | Supports energy-efficient Bluetooth RFCOMM transfers for lightweight files and documents, or automatically negotiates the optimal protocol (`SMART_AUTO`). |
| **Instant QR Code Device & Hotspot Pairing** | `QrScannerView`, `QrGenerator`, `QrImageDecoder` (`CameraX` & `ZXing`) | **Instant** (< 200 ms) | **100% On-Device** | Encodes receiver hotspot credentials and host IPs into a scannable `MIORA_WIFI` QR code for one-scan automatic network binding and file dispatch. |
| **Universal Media & Document Picker** | `SendScreen`, `DocumentFile` & Android Storage Access Framework | **Instant Batch Selection** | **Scoped Local Access** | Pick multiple photos, videos, audio tracks, documents, or arbitrary files with live item counts, size aggregation, and saved outputs in `Downloads/Miora`. |
| **Transfer History & Storage Management** | `TransferHistoryManager` & `TransfersScreen` | **Instant** | **Strictly Isolated** | Tracks sent and received transfers with direction filtering (`All`, `Sent`, `Received`), transfer status badges, quick file opening, and direct sharing. |
| **Essential Requirements & Permissions Auditor** | `AppRequirementsDialog` & `DeviceConnectivityManager` | **Real-Time Check** | **Full User Control** | Interactive readiness dashboard that verifies and helps enable Wi-Fi, Bluetooth, Location (GPS), Nearby Devices, and Media/Storage permissions in one place. |
| **In-App Auto Updater & Changelog** | `UpdateUtil`, `GitHub Releases API` & `FileProvider` | **Background Streaming** | **Direct GitHub Fetch** | Checks GitHub Releases (`hamzabellouch/miora`) for new updates, downloads APKs with live progress notifications, and triggers seamless in-app upgrades. |
| **Multilingual Support, Dynamic Packs & RTL** | `LanguageManager`, `DynamicResources` & Jetpack Compose RTL | **Native & OTA Packs** | **N/A** | Built-in localization for English, Arabic, and French with full Right-to-Left (RTL) layout adaptation and downloadable community translation packs. |
| **Zero Telemetry / 100% Privacy** | Pure Local P2P Logic & No Trackers | **N/A** | **Complete Privacy** | Zero cloud servers, zero analytics SDKs, zero advertisement libraries, and no background tracking or personal data collection. |



## 🛠 Tech Stack & Architecture

Miora follows clean architecture principles for maintainability, high transfer throughput, battery efficiency, and minimal resource usage:

* **Core Architecture & Concurrency:**
  - `Single Activity Architecture` (`MainActivity`) powered by modern declarative UI (`MainScreen`)
  - Concurrency, socket I/O, and reactive state management via Kotlin `Coroutines` (`Dispatchers.IO`), `StateFlow`, and `TransferController`
  - Separation of concerns between UI presentation (`ui.*`), P2P transport & connectivity managers (`model.*`), and utility services (`util.*`)

* **Language & Build Tooling:**
  - `100% Kotlin 2.4+` with Kotlin Compose Compiler Plugin
  - `Android Gradle Plugin 9.4+` managed via Gradle Version Catalogs (`libs.versions.toml`)
  - Target & Compile SDK: `Android 16 (API 37)` | Minimum SDK: `Android 7.0 (API 24)`

* **UI Framework & Design System:**
  - `Jetpack Compose` with `Material Design 3` (BOM `2026.09.00`)
  - Dynamic Color theming (Material You) with adaptive Dark/Light mode and High-Contrast Pure Black (AMOLED) support (`MioraTheme`)
  - Custom SVG vector icons (`AppIcons`, `Coder`), radar pulse animations (`RadarPulseAnimation`, `BigRadarAnimation`), and customizable floating/classic navigation (`FloatingBottomBar`)
  - Full native RTL (Right-to-Left) mirroring and edge-to-edge system insets integration

* **P2P Connectivity, Discovery & Transfer Protocols:**
  - `MioraTransferEngine` implementing the `MIORA_TRANSFER_V1` binary stream protocol over TCP sockets (`ServerSocket` / `Socket` on port `8988`) with 128 KB transfer buffers
  - Android `WifiP2pManager` (Wi-Fi Direct), `WifiManager.LocalOnlyHotspotReservation`, and `WifiNetworkSpecifier` (`ConnectivityManager`) for zero-router high-speed Wi-Fi links
  - Android `NsdManager` (`_miora._tcp`) and UDP broadcast beacons (port `8989`) for zero-configuration local network discovery
  - Android `BluetoothAdapter`, `BluetoothLeAdvertiser`, `BluetoothLeScanner`, and RFCOMM `BluetoothServerSocket` / `BluetoothSocket` for proximity discovery and offline Bluetooth transfers

* **Vision & QR Code Pairing:**
  - `AndroidX CameraX 1.6+` (`camera2`, `lifecycle`, `view`) for real-time camera viewfinder, tap-to-focus, zoom, and torch control (`QrScannerView`)
  - `ZXing Core 3.5.4` (`QrGenerator`, `QrImageDecoder`) for encoding `MIORA_WIFI` connection payloads and decoding QR codes from camera frames or gallery images

* **Networking, Storage & System Services:**
  - `OkHttp 5.5+` (`HttpClient`) for GitHub Releases update checks, APK streaming (`UpdateUtil`), and community language pack downloads (`LanguageManager`)
  - `Kotlinx Serialization JSON` for schema-safe serialization of transfer history (`TransferHistoryManager`) and release metadata
  - `AndroidX DocumentFile` & Storage APIs for reading media/documents and writing received files to `Downloads/Miora`
  - Android `NotificationManager` (`UpdateNotificationHelper`) and `FileProvider` (`REQUEST_INSTALL_PACKAGES`) for background update progress and secure APK installation



## 🔥 Installation

1. Go to the Releases page:
   https://github.com/hamzabellouch/miora/releases

2. Download the latest `.apk` file.

3. Install the application on your Android device.

4. Make sure that `Install from unknown sources` is enabled in your Android settings.

## 🔨 Building from Source

To build Miora locally, make sure you have the latest version of Android Studio installed.

1. Clone the repository: `git clone https://github.com/hamzabellouch/miora.git`
2. Open the project in Android Studio.
3. Sync Gradle dependencies.
4. Build and run the application on your device or emulator.



> [!WARNING]
> There is always a possibility of error, so we assume no responsibility for any inaccuracies.


### <a name="Copyright©2026"></a> Copyright © 2026

Thank you for checking out Miora. If you have any feedback or suggestions, feel free to contact us:
hamzabellouchcontact@gmail.com

Stay connected and follow us on:  
[WhatsApp](https://whatsapp.com/channel/0029Vb7MArw0LKZMpjjqOk2P) | [Facebook](https://facebook.com/hamzabellouch0) | [Instagram](https://instagram.com/hamzabellouch0) | [Twitter](https://twitter.com/hamzabellouch0) | [Telegram](https://t.me/hammzabellouch) | [LinkedIn](https://www.linkedin.com/in/hamzabellouch)
