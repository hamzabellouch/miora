package com.tkno.miora.model

import android.net.Uri

enum class TransferProtocol(val displayName: String, val badge: String) {
    WIFI_DIRECT("Wi-Fi Direct", "250 Mbps"),
    BLUETOOTH("Bluetooth", "Low Power")
}

enum class TransferDirection {
    SEND,
    RECEIVE
}

enum class TransferStatus {
    PENDING,
    CONNECTING,
    TRANSFERRING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class MediaItem(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String,
    val isVideo: Boolean = mimeType.startsWith("video/"),
    val isImage: Boolean = mimeType.startsWith("image/"),
    val isAudio: Boolean = mimeType.startsWith("audio/"),
    val isDocument: Boolean = mimeType.startsWith("text/") ||
            mimeType == "application/pdf" ||
            mimeType.contains("officedocument") ||
            mimeType.contains("msword") ||
            mimeType.contains("ms-excel") ||
            mimeType.contains("ms-powerpoint") ||
            name.substringAfterLast('.', "").lowercase() in listOf("pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx", "csv", "json", "rtf", "odt", "epub")
) {
    val formattedSize: String
        get() = formatFileSize(size)
}

data class DiscoveredPeer(
    val id: String,
    val name: String,
    val deviceAddress: String,
    val protocol: TransferProtocol,
    val hostAddress: String? = null,
    val port: Int = 8988,
    val blePsm: Int = 0,
    val signalStrength: Int = 85, // 0-100%
    val isConnected: Boolean = false,
    val isMioraDevice: Boolean = true
)

data class TransferRecord(
    val id: String,
    val fileName: String,
    val fileSize: Long,
    val direction: TransferDirection,
    val protocol: TransferProtocol,
    val timestamp: Long = System.currentTimeMillis(),
    val status: TransferStatus = TransferStatus.COMPLETED,
    val progress: Float = 1f,
    val speedMbps: Float = 0f,
    val uriString: String? = null
) {
    val formattedSize: String
        get() = formatFileSize(fileSize)

    val extension: String
        get() = fileName.substringAfterLast('.', "").lowercase()

    val isImage: Boolean
        get() = extension in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "svg")

    val isVideo: Boolean
        get() = extension in listOf("mp4", "mkv", "mov", "avi", "webm", "3gp", "flv", "wmv", "m4v")

    val isAudio: Boolean
        get() = extension in listOf("mp3", "m4a", "wav", "aac", "flac", "ogg", "opus", "wma")

    val isDocument: Boolean
        get() = extension in listOf("pdf", "doc", "docx", "txt", "xls", "xlsx", "ppt", "pptx", "csv", "json")
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val size = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(java.util.Locale.US, "%.1f %s", size, units[digitGroups])
}
