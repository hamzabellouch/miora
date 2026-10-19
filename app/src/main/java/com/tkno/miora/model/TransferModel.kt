package com.tkno.miora.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.FileInputStream

enum class TransferProtocol(val displayName: String, val badge: String) {
    WIFI_DIRECT("Wi-Fi Direct", "250 Mbps"),
    LOCAL_WIFI("Local Wi-Fi (LAN)", "LAN / Router"),
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
    val formattedSpeed: String
        get() = formatSpeed(speedMbps)
}

fun formatSpeed(speedMbps: Float): String {
    if (speedMbps <= 0.0001f) return "0 KB/s"
    return if (speedMbps < 1.0f) {
        val kbps = speedMbps * 1024f
        if (kbps < 10f) {
            String.format(java.util.Locale.US, "%.1f KB/s", kbps)
        } else {
            String.format(java.util.Locale.US, "%.0f KB/s", kbps)
        }
    } else {
        String.format(java.util.Locale.US, "%.1f MB/s", speedMbps)
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val size = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(java.util.Locale.US, "%.1f %s", size, units[digitGroups])
}

fun resolveUriToMediaItem(context: Context, uri: Uri): MediaItem {
    var name = "File_${System.currentTimeMillis()}"
    var size = 0L
    val mime = context.contentResolver.getType(uri) ?: "*/*"

    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx != -1) cursor.getString(nameIdx)?.let { name = it }
                if (sizeIdx != -1 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
            }
        }
    } catch (_: Exception) {}

    if (size <= 0L) {
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val chan = FileInputStream(pfd.fileDescriptor).channel
                val chanSize = chan.size()
                if (chanSize > 0L) size = chanSize
                else if (pfd.statSize > 0L) size = pfd.statSize
            }
        } catch (_: Exception) {}
    }
    if (size <= 0L) {
        try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                if (afd.length > 0L) size = afd.length
            }
        } catch (_: Exception) {}
    }
    if (size <= 0L) {
        try {
            context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val s = c.getLong(0)
                    if (s > 0L) size = s
                }
            }
        } catch (_: Exception) {}
    }
    if (!name.contains('.')) {
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            ?: when {
                mime.startsWith("video/") -> "mp4"
                mime.startsWith("image/") -> "jpg"
                mime.startsWith("audio/") -> "mp3"
                else -> null
            }
        if (!ext.isNullOrBlank()) {
            name = "$name.$ext"
        }
    }

    return MediaItem(uri = uri, name = name, size = size.coerceAtLeast(0L), mimeType = mime)
}

fun resolveUrisToMediaItems(context: Context, uris: List<Uri>): List<MediaItem> {
    return uris.map { resolveUriToMediaItem(context, it) }
}

data class TransferSessionState(
    val sessionId: String = java.util.UUID.randomUUID().toString(),
    val totalFiles: Int = 0,
    val completedFilesCount: Int = 0,
    val baseBytesTransferred: Long = 0L,
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L,
    val speedMbps: Float = 0f,
    val currentFileName: String = "",
    val completedRecords: List<TransferRecord> = emptyList(),
    val status: TransferStatus = TransferStatus.CONNECTING,
    val isFinished: Boolean = false,
    val isSuccess: Boolean = false,
    val protocol: TransferProtocol = TransferProtocol.WIFI_DIRECT,
    val receiverName: String = "",
    val direction: TransferDirection = TransferDirection.SEND
) {
    val progress: Float
        get() = if (totalBytes > 0L) (bytesTransferred.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val formattedSpeed: String
        get() = formatSpeed(speedMbps)

    val formattedBytesProgress: String
        get() = "${formatFileSize(bytesTransferred)} / ${formatFileSize(totalBytes)}"

    val formattedCountProgress: String
        get() = "$completedFilesCount/$totalFiles"
}
