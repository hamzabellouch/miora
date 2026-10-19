# Security Policy

## Supported Versions

The following versions of Miora are currently supported with security, privacy, and stability updates.

| Version       | Supported          |
| ------------- | ------------------ |
| + 0.0.2-beta  | :white_check_mark: |
| < 0.0.2-beta  | :x:                |



## Reporting a Vulnerability

If you discover a security vulnerability, privacy issue, unauthorized file transfer vector, unexpected behavior with permissions, or any issue that could negatively affect users or their data safety, please report it responsibly.

### Before Reporting
Please make sure that:
- The issue is reproducible
- You are using the latest supported version of Miora
- The issue is not caused by third-party modifications, custom ROMs, or unsupported Android environments

### How to Report
You can report vulnerabilities through:
- GitHub Issues (for non-sensitive reports and general bug reports)
- E-mail: hamzabellouchcontact@gmail.com / [Forms](https://docs.google.com/forms/d/e/1FAIpQLSf87zkBsPRiUX19qF42vekAwgV_bW2EWZZPEThTo8PFIOFc0w/viewform?usp=header) 

When reporting, please include:
- Sender and Receiver device models and Android OS versions
- Miora application version
- Active connection method (Wi-Fi Direct, Local Hotspot, Bluetooth, LAN, or QR Pairing)
- Granted permissions state (e.g., Nearby Devices, Location, Bluetooth, Media/Storage, Camera)
- Steps to reproduce the issue
- Screenshots or system `logcat` outputs if available
- A clear explanation of the potential security or privacy impact

### Response Policy
Security reports are reviewed as quickly as possible.  
If the issue is confirmed:
- The vulnerability will be investigated, verified, and patched
- A fix will be included in the next update release
- Credit may be given to the reporter if requested

If the report is invalid, incomplete, or not reproducible, it may be closed without action.



## Security & Architecture Notes

Miora is designed with a strict **Privacy-First & Offline Peer-to-Peer Security Model**:

- **100% Offline P2P Execution:** All file transfers (`MioraTransferEngine`) occur directly between devices over local TCP sockets (port `8988`) or Bluetooth RFCOMM channels. No files, metadata, or camera frames are ever uploaded to external cloud servers.
- **Explicit Recipient Handshake & Consent:** Under the `MIORA_TRANSFER_V1` protocol, the sender must first transmit a metadata header (sender name, file count, and total byte size) and wait for an explicit `ACCEPT` response from the recipient (`IncomingRequest` dialog) before any file content bytes are streamed or written to disk.
- **Ephemeral Wireless Pairing:** Local-only hotspots (`LocalOnlyHotspot`) and Wi-Fi Direct groups (`WifiP2pManager`) are created exclusively when Receive mode is active and are automatically torn down when the user exits Receive mode or closes the application.
- **Root-Free Operation:** Miora does not require root privileges. It relies entirely on standard Android system APIs (`WifiP2pManager`, `ConnectivityManager`, `NsdManager`, `BluetoothAdapter`, `CameraX`, and `DocumentFile`).
- **Camera Isolation:** The camera hardware is accessed exclusively when the user opens the QR code pairing scanner (`QrScannerView`). No background recording, silent frame capture, or image storage takes place at any time.
- **Scoped Storage & Transient Buffer Wiping:** Received files are written strictly to the designated `Downloads/Miora` directory with filename sanitization, and all 128 KB socket transfer buffers and camera frames held in RAM are cleared immediately upon transfer completion or termination.
