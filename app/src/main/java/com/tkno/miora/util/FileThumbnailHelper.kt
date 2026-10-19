package com.tkno.miora.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.LruCache
import android.util.Size
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.tkno.miora.R
import com.tkno.miora.model.TransferRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object FileThumbnailHelper {
    private val thumbnailCache = LruCache<String, Bitmap>(250)
    private val durationCache = LruCache<String, String>(500)

    /**
     * Resolves the actual File on device storage if available.
     */
    fun resolveFile(record: TransferRecord): File? {
        val uriStr = record.uriString
        if (!uriStr.isNullOrBlank()) {
            if (uriStr.startsWith("/")) {
                val f = File(uriStr)
                if (f.exists() && f.isFile) return f
            } else if (uriStr.startsWith("file://")) {
                val f = File(Uri.parse(uriStr).path ?: "")
                if (f.exists() && f.isFile) return f
            }
        }

        // Check standard Miora received folder in Downloads
        val mioraDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Miora"
        )
        if (mioraDir.exists()) {
            val candidate = File(mioraDir, record.fileName)
            if (candidate.exists() && candidate.isFile) return candidate

            // In case of multi-file names like "photo.jpg +2", check the first part
            val firstName = record.fileName.substringBefore(" +").trim()
            val candidateFirst = File(mioraDir, firstName)
            if (candidateFirst.exists() && candidateFirst.isFile) return candidateFirst
        }

        return null
    }

    /**
     * Resolves a shareable / openable Uri using Content Uri or FileProvider.
     */
    fun getFileUri(context: Context, record: TransferRecord): Uri? {
        val uriStr = record.uriString
        if (!uriStr.isNullOrBlank() && uriStr.startsWith("content://")) {
            return Uri.parse(uriStr)
        }

        val localFile = resolveFile(record)
        if (localFile != null && localFile.exists()) {
            return try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    localFile
                )
            } catch (_: Exception) {
                Uri.fromFile(localFile)
            }
        }

        return null
    }

    /**
     * Opens the transferred file with an appropriate viewer app.
     */
    fun openFile(context: Context, record: TransferRecord) {
        val uri = getFileUri(context, record)
        if (uri == null) {
            Toast.makeText(context, R.string.no_transfers, Toast.LENGTH_SHORT).show()
            return
        }

        val mimeType = resolveMimeType(record, uri, context)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // Fallback with intent chooser or wildcard mime
            try {
                val chooser = Intent.createChooser(intent, record.fileName).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (_: Exception) {
                Toast.makeText(context, "No app available to open this file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Shares the transferred file with external apps.
     */
    fun shareFile(context: Context, record: TransferRecord) {
        val uri = getFileUri(context, record)
        if (uri == null) {
            // Text fallback if physical file was cleared
            val textIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "${record.fileName} (${record.formattedSize})")
            }
            context.startActivity(Intent.createChooser(textIntent, "Share with"))
            return
        }

        val mimeType = resolveMimeType(record, uri, context)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(record.fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(Intent.createChooser(shareIntent, "Share ${record.fileName}"))
        } catch (_: Exception) {
            Toast.makeText(context, "Unable to share file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resolveMimeType(record: TransferRecord, uri: Uri, context: Context): String {
        var mime = context.contentResolver.getType(uri)
        if (mime.isNullOrBlank()) {
            val ext = record.extension
            if (ext.isNotBlank()) {
                mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
            }
        }
        return mime ?: when {
            record.isVideo -> "video/*"
            record.isImage -> "image/*"
            record.isAudio -> "audio/*"
            record.extension == "pdf" -> "application/pdf"
            record.extension == "apk" -> "application/vnd.android.package-archive"
            else -> "*/*"
        }
    }

    /**
     * Asynchronously loads and caches a video/image thumbnail bitmap.
     */
    suspend fun loadThumbnail(context: Context, record: TransferRecord): Bitmap? = withContext(Dispatchers.IO) {
        if (!record.isImage && !record.isVideo) return@withContext null

        val cacheKey = record.id + "_" + record.fileName
        thumbnailCache.get(cacheKey)?.let { return@withContext it }

        val localFile = resolveFile(record)
        val contentUri = if (record.uriString?.startsWith("content://") == true) Uri.parse(record.uriString) else null

        var bitmap: Bitmap? = null

        try {
            if (record.isVideo) {
                bitmap = loadVideoThumbnailInternal(context, localFile, contentUri)
            } else if (record.isImage) {
                bitmap = loadImageThumbnailInternal(context, localFile, contentUri)
            }
        } catch (_: Exception) {}

        if (bitmap != null) {
            thumbnailCache.put(cacheKey, bitmap)
        }

        bitmap
    }

    private fun loadVideoThumbnailInternal(context: Context, file: File?, uri: Uri?): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                if (uri != null) {
                    return context.contentResolver.loadThumbnail(uri, Size(320, 180), null)
                }
                if (file != null && file.exists()) {
                    return ThumbnailUtils.createVideoThumbnail(file, Size(320, 180), null)
                }
            } catch (_: Exception) {}
        }

        // Fallback using MediaMetadataRetriever
        try {
            val retriever = MediaMetadataRetriever()
            if (file != null && file.exists()) {
                retriever.setDataSource(file.absolutePath)
            } else if (uri != null) {
                retriever.setDataSource(context, uri)
            } else {
                return null
            }
            val frame = retriever.frameAtTime
            retriever.release()
            return frame
        } catch (_: Exception) {}

        return null
    }

    private fun loadImageThumbnailInternal(context: Context, file: File?, uri: Uri?): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                if (uri != null) {
                    return context.contentResolver.loadThumbnail(uri, Size(320, 180), null)
                }
                if (file != null && file.exists()) {
                    return ThumbnailUtils.createImageThumbnail(file, Size(320, 180), null)
                }
            } catch (_: Exception) {}
        }

        // Downsampled BitmapFactory fallback
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            if (file != null && file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath, options)
            } else if (uri != null) {
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
            }

            options.inSampleSize = calculateInSampleSize(options, 320, 180)
            options.inJustDecodeBounds = false

            return if (file != null && file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath, options)
            } else if (uri != null) {
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
            } else null
        } catch (_: Exception) {}

        return null
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    /**
     * Resolves video duration (e.g. "03:45")
     */
    suspend fun loadVideoDuration(context: Context, record: TransferRecord): String? = withContext(Dispatchers.IO) {
        if (!record.isVideo) return@withContext null

        val cacheKey = "dur_" + record.id + "_" + record.fileName
        durationCache.get(cacheKey)?.let { return@withContext it }

        val localFile = resolveFile(record)
        val contentUri = if (record.uriString?.startsWith("content://") == true) Uri.parse(record.uriString) else null

        try {
            val retriever = MediaMetadataRetriever()
            if (localFile != null && localFile.exists()) {
                retriever.setDataSource(localFile.absolutePath)
            } else if (contentUri != null) {
                retriever.setDataSource(context, contentUri)
            } else {
                return@withContext null
            }
            val timeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            val durationMs = timeStr?.toLongOrNull() ?: 0L
            if (durationMs > 0) {
                val totalSec = durationMs / 1000
                val min = totalSec / 60
                val sec = totalSec % 60
                val formatted = String.format(Locale.US, "%02d:%02d", min, sec)
                durationCache.put(cacheKey, formatted)
                return@withContext formatted
            }
        } catch (_: Exception) {}

        null
    }

    /**
     * Formats timestamp into user-friendly time string.
     */
    fun formatRecordTime(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val recordCal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val todayCal = Calendar.getInstance()

        return when {
            diff in 0..59_999L -> "Just now"
            recordCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                    recordCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR) -> {
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
            }
            recordCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) -> {
                SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(timestamp))
            }
            else -> {
                SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(timestamp))
            }
        }
    }
}

@Composable
fun rememberThumbnail(record: TransferRecord): Bitmap? {
    val context = LocalContext.current
    var bitmap by remember(record.id, record.uriString) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(record.id, record.uriString) {
        if (record.isImage || record.isVideo) {
            bitmap = FileThumbnailHelper.loadThumbnail(context, record)
        }
    }

    return bitmap
}

@Composable
fun rememberVideoDuration(record: TransferRecord): String? {
    val context = LocalContext.current
    var duration by remember(record.id, record.uriString) { mutableStateOf<String?>(null) }

    LaunchedEffect(record.id, record.uriString) {
        if (record.isVideo) {
            duration = FileThumbnailHelper.loadVideoDuration(context, record)
        }
    }

    return duration
}
