# Contributing

Thank you for your interest in contributing to **Miora**! Miora is a modern, privacy-first Android application built with Kotlin and Jetpack Compose designed for 100% offline peer-to-peer (P2P) file and media sharing via Wi-Fi Direct, Local Hotspot, Bluetooth, LAN discovery, and QR code pairing.

Before submitting a bug report or feature request, please search existing issues (including closed ones) to ensure it hasn't already been reported or discussed. If there are no duplicates, feel free to submit a new issue using the appropriate template.

**Please note:** Issues that do not use existing templates or lack sufficient detail may be closed without review.

For questions or any other ideas to improve, you can join our official e-mail : hamzabellouchcontact@gmail.com or [Social Media platforms](https://sites.google.com/view/hamzabellouch/).


## Disclaimer

Miora is an active Android project focused on providing a clean, fast, secure, and privacy-first offline P2P file sharing tool. While we strive for code quality, transfer reliability, and cross-device compatibility, contributions and feedback are always welcome to improve performance and user experience.



## Bug Reports

When submitting a bug report, please make sure your issue contains **sufficient information** to reproduce the problem. Useful details include:

- Sender and Receiver device models and Android OS versions
- Miora app version (e.g., `0.0.1-beta`)
- Active transfer protocol (`Wi-Fi Direct`, `Bluetooth`, `Smart Auto`, or `QR Pairing`)
- Granted permission states (Nearby Devices, Location, Bluetooth, Media/Storage, Camera)
- Steps to reproduce the bug
- System logs (`logcat`) or screenshots if applicable



## Feature Requests

Miora aims to remain a fast, privacy-focused, and lightweight offline P2P file and media sharing utility for Android. We welcome suggestions that enhance peer discovery reliability, transfer speeds, UI responsiveness, or device pairing capabilities.

When suggesting a new feature, please consider:
- **Relevance:** Does the feature fit the core goal of offline peer-to-peer file sharing, local device discovery, or seamless wireless pairing?
- **Privacy & Safety:** Does it preserve 100% on-device / local P2P processing and user privacy without requiring cloud servers or intrusive permissions?
- **Simplicity:** We prefer clean, intuitive features over overly complex options that bloat the user interface.



## Pull Requests

If you wish to contribute directly by submitting code:

1. **Discuss First:** Leave a comment under an existing issue or open a new issue describing the changes you plan to make before writing code.
2. **Avoid Conflicts:** Comment on the issue to let others know you are working on it to prevent duplicate efforts.
3. **Follow Code Style:** Ensure your code follows modern Kotlin conventions, Coroutines structured concurrency, and Jetpack Compose best practices.



## New Contributors

If you are new to the project:
- Browse our open issues for beginner-friendly tasks marked as `good first issue` or `help wanted`.
- Feel free to ask clarifying questions directly on the issue thread!



## Building From Source

To build Miora locally:

1. **Prerequisites:**
   - [Android Studio](https://developer.android.com/studio) (latest stable or preview release)
   - JDK 17 or higher
   - Android SDK API Level 24+

2. **Steps:**
   ```bash
   # Clone the repository
   git clone https://github.com/hamzabellouch/miora.git

   # Open the project in Android Studio and sync Gradle files
   ```
