package com.tkno.miora.ui.send

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.camera.core.CameraSelector
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.tkno.miora.QrBox
import com.tkno.miora.QrScannerView
import com.tkno.miora.R
import com.tkno.miora.model.*
import com.tkno.miora.ui.component.GoogleStyleViewfinder
import com.tkno.miora.util.QrImageDecoder
import kotlinx.coroutines.launch

@Composable
fun SendScreen() {
    val context = LocalContext.current
    val globalProtocol by TransferController.selectedProtocol.collectAsState()
    var selectedMedia by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var selectedProtocol by remember { mutableStateOf(globalProtocol) }
    var showQrScannerModal by remember { mutableStateOf(false) }

    LaunchedEffect(globalProtocol) {
        selectedProtocol = globalProtocol
    }

    val isScanning by TransferController.isScanning.collectAsState()
    val peers by TransferController.discoveredPeers.collectAsState()
    val activeSend by TransferController.activeSend.collectAsState()
    val isWifiEnabled by DeviceConnectivityManager.isWifiEnabled.collectAsState()
    val isBluetoothEnabled by DeviceConnectivityManager.isBluetoothEnabled.collectAsState()

    LaunchedEffect(selectedProtocol, isBluetoothEnabled, isWifiEnabled, selectedMedia.size) {
        if (selectedProtocol != TransferProtocol.WIFI_DIRECT) {
            TransferController.startScanning()
        } else {
            TransferController.stopScanning()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            TransferController.stopScanning()
        }
    }
    var showRequirementsDialog by remember { mutableStateOf(false) }
    var showProtocolDialog by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showQrScannerModal = true
        } else {
            Toast.makeText(context, "Camera permission is needed to scan receiver QR code", Toast.LENGTH_SHORT).show()
        }
    }

    if (showRequirementsDialog) {
        com.tkno.miora.ui.component.AppRequirementsDialog(
            onDismiss = { showRequirementsDialog = false }
        )
    }

    if (showProtocolDialog) {
        com.tkno.miora.ui.component.TransferProtocolDialog(
            selectedProtocol = selectedProtocol,
            onProtocolSelect = { protocol ->
                selectedProtocol = protocol
                TransferController.setProtocol(context, protocol)
            },
            onDismiss = { showProtocolDialog = false }
        )
    }

    // Activity Result Launchers
    val handleSelectedUris: (List<Uri>) -> Unit = { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val items = uris.map { uri ->
                var name = "File_${System.currentTimeMillis()}"
                var size = 0L
                val mime = context.contentResolver.getType(uri) ?: "*/*"

                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx != -1) cursor.getString(nameIdx)?.let { name = it }
                        if (sizeIdx != -1 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                    }
                }
                if (size <= 0L) {
                    try {
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            if (pfd.statSize > 0L) size = pfd.statSize
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

                MediaItem(uri = uri, name = name, size = size.coerceAtLeast(0L), mimeType = mime)
            }
            selectedMedia = (selectedMedia + items).distinctBy { it.uri }
        }
    }

    val mediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = handleSelectedUris
    )

    val docLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = handleSelectedUris
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 24.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header with Title & Transfer Protocol Capsule (Matching About's What's New capsule style)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.tab_send),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.select_media),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Transfer Protocol Capsule
                    Surface(
                        onClick = { showProtocolDialog = true },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .height(36.dp)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = when (selectedProtocol) {
                                    TransferProtocol.WIFI_DIRECT -> Icons.Outlined.Wifi
                                    TransferProtocol.BLUETOOTH -> Icons.Outlined.Bluetooth
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = selectedProtocol.displayName,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // Quick Media Pickers (Row 1: Photos, Videos, Audios | Row 2: Documents, Files)
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Row 1: Photos, Videos, Audios (3 items)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MediaPickerChip(
                            icon = Icons.Outlined.PhotoLibrary,
                            label = stringResource(R.string.pick_images),
                            modifier = Modifier.weight(1f),
                            onClick = { mediaLauncher.launch("image/*") }
                        )
                        MediaPickerChip(
                            icon = Icons.Outlined.VideoLibrary,
                            label = stringResource(R.string.pick_videos),
                            modifier = Modifier.weight(1f),
                            onClick = { mediaLauncher.launch("video/*") }
                        )
                        MediaPickerChip(
                            icon = Icons.Outlined.Audiotrack,
                            label = stringResource(R.string.pick_audio),
                            modifier = Modifier.weight(1f),
                            onClick = { mediaLauncher.launch("audio/*") }
                        )
                    }

                    // Row 2: Documents, Files (2 items)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MediaPickerChip(
                            icon = Icons.Outlined.Description,
                            label = stringResource(R.string.pick_documents),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                docLauncher.launch(
                                    arrayOf(
                                        "application/pdf",
                                        "application/msword",
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                        "application/vnd.ms-excel",
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "application/vnd.ms-powerpoint",
                                        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                        "text/*",
                                        "application/rtf",
                                        "application/epub+zip",
                                        "application/zip"
                                    )
                                )
                            }
                        )
                        MediaPickerChip(
                            icon = Icons.Outlined.Folder,
                            label = stringResource(R.string.pick_files),
                            modifier = Modifier.weight(1f),
                            onClick = { mediaLauncher.launch("*/*") }
                        )
                    }
                }
            }

            // Selected Media Summary & Preview Cards
            if (selectedMedia.isNotEmpty()) {
                item {
                    val totalBytes = selectedMedia.sumOf { it.size }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = stringResource(R.string.selected_items, selectedMedia.size),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.total_size, formatFileSize(totalBytes)),
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        selectedMedia = emptyList()
                                        TransferController.stopScanning()
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text(stringResource(R.string.clear_selection))
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(selectedMedia) { media ->
                                    MediaThumbnailCard(
                                        item = media,
                                        onRemove = {
                                            val updated = selectedMedia.filter { it.uri != media.uri }
                                            selectedMedia = updated
                                            if (updated.isEmpty()) {
                                                TransferController.stopScanning()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Central Action: Wi-Fi Direct (QR Scanner Mode) vs Bluetooth (Nearby Devices Radar)
            if (selectedProtocol == TransferProtocol.WIFI_DIRECT) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape,
                                modifier = Modifier.size(72.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.QrCodeScanner,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Wi-Fi Direct Transfer",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Point your camera at the QR code displayed on the receiver's screen to connect and send files instantly.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }

                            if (!isWifiEnabled) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                            Text("Wi-Fi is required for direct transfer", fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                                        }
                                        TextButton(onClick = { DeviceConnectivityManager.openWifiSettings(context) }) {
                                            Text("Enable")
                                        }
                                    }
                                }
                            }

                            val hasSelectedFiles = selectedMedia.isNotEmpty()
                            Button(
                                onClick = {
                                    if (!hasSelectedFiles) {
                                        Toast.makeText(context, "Please select files to send first", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                                    if (hasCamera) {
                                        showQrScannerModal = true
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                                enabled = hasSelectedFiles,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                                )
                            ) {
                                Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Scan Receiver QR Code",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            } else {
                // Nearby Radar & Discovered Devices (Bluetooth Mode)
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = stringResource(R.string.nearby_devices),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = when {
                                            isScanning && peers.isEmpty() -> stringResource(R.string.searching_devices)
                                            peers.isNotEmpty() -> "${peers.size} Miora device(s) found"
                                            selectedMedia.isEmpty() -> stringResource(R.string.select_media_to_scan)
                                            else -> "${peers.size} Miora device(s) found"
                                        },
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                FilledTonalIconButton(
                                    onClick = {
                                        if (isScanning) TransferController.stopScanning()
                                        else TransferController.startScanning()
                                    }
                                ) {
                                    if (isScanning) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Icon(Icons.Filled.Refresh, contentDescription = "Scan")
                                    }
                                }
                            }

                            if (!isWifiEnabled || !isBluetoothEnabled) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                            Text(stringResource(R.string.requirements_missing_warning), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (!isWifiEnabled) {
                                                OutlinedButton(
                                                    onClick = { DeviceConnectivityManager.openWifiSettings(context) },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
                                                ) {
                                                    Text(stringResource(R.string.requirements_turn_on) + " Wi-Fi", fontSize = 11.sp)
                                                }
                                            }
                                            if (!isBluetoothEnabled) {
                                                OutlinedButton(
                                                    onClick = { DeviceConnectivityManager.openBluetoothSettings(context) },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
                                                ) {
                                                    Text(stringResource(R.string.requirements_turn_on) + " BT", fontSize = 11.sp)
                                                }
                                            }
                                            FilledTonalButton(
                                                onClick = { showRequirementsDialog = true },
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
                                            ) {
                                                Text(stringResource(R.string.requirements_check_again), fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            if (peers.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        RadarPulseAnimation(isScanning = isScanning)
                                        Text(
                                            text = when {
                                                isScanning -> stringResource(R.string.searching_devices)
                                                selectedMedia.isEmpty() -> stringResource(R.string.select_media_to_scan_hint)
                                                else -> stringResource(R.string.scan_timed_out)
                                            },
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    peers.forEach { peer ->
                                        DiscoveredPeerCard(
                                            peer = peer,
                                            selectedProtocol = selectedProtocol,
                                            canSend = selectedMedia.isNotEmpty(),
                                            onSendClick = {
                                                TransferController.startSendTransfer(
                                                    peer = peer,
                                                    items = selectedMedia,
                                                    protocolPreference = selectedProtocol,
                                                    onComplete = { success ->
                                                        if (success) {
                                                            Toast.makeText(context, context.getString(R.string.transfer_completed), Toast.LENGTH_SHORT).show()
                                                            selectedMedia = emptyList()
                                                            TransferController.stopScanning()
                                                        } else {
                                                            Toast.makeText(context, context.getString(R.string.transfer_failed), Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Active Send Floating Card
        activeSend?.let { transfer ->
            ActiveTransferCard(
                transfer = transfer,
                onCancel = { TransferController.cancelSendTransfer() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp)
            )
        }

        // Fullscreen QR Code Camera Scanner Modal for Wi-Fi Direct
        if (showQrScannerModal) {
            QrSendScannerModal(
                onQrScanned = { payload ->
                    showQrScannerModal = false
                    TransferController.startSendTransferViaQr(
                        ssid = payload.ssid,
                        passphrase = payload.passphrase,
                        hostIp = payload.hostIp,
                        hostIps = payload.hostIps,
                        port = payload.port,
                        items = selectedMedia,
                        onComplete = { success ->
                            if (success) {
                                Toast.makeText(context, context.getString(R.string.transfer_completed), Toast.LENGTH_SHORT).show()
                                selectedMedia = emptyList()
                                TransferController.stopScanning()
                            } else {
                                Toast.makeText(context, context.getString(R.string.transfer_failed), Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                },
                onDismiss = { showQrScannerModal = false }
            )
        }
    }
}

@Composable
fun MediaPickerChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.height(64.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MediaThumbnailCard(
    item: MediaItem,
    onRemove: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.width(130.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when {
                        item.isVideo -> Icons.Outlined.Videocam
                        item.isImage -> Icons.Outlined.Image
                        item.isAudio -> Icons.Outlined.AudioFile
                        item.isDocument -> Icons.Outlined.Description
                        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = item.formattedSize,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DiscoveredPeerCard(
    peer: DiscoveredPeer,
    selectedProtocol: TransferProtocol = TransferProtocol.WIFI_DIRECT,
    canSend: Boolean,
    onSendClick: () -> Unit
) {
    val effectiveProtocol = when (selectedProtocol) {
        TransferProtocol.WIFI_DIRECT -> TransferProtocol.WIFI_DIRECT
        TransferProtocol.BLUETOOTH -> TransferProtocol.BLUETOOTH
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (effectiveProtocol == TransferProtocol.WIFI_DIRECT)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.secondaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (effectiveProtocol == TransferProtocol.WIFI_DIRECT) Icons.Outlined.Wifi
                        else Icons.Outlined.Bluetooth,
                        contentDescription = null,
                        tint = if (effectiveProtocol == TransferProtocol.WIFI_DIRECT)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = peer.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Miora",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = effectiveProtocol.displayName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (effectiveProtocol == TransferProtocol.WIFI_DIRECT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "• Signal ${peer.signalStrength}%",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Button(
                onClick = onSendClick,
                enabled = canSend,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (canSend) stringResource(R.string.tab_send) else stringResource(R.string.select_media),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun RadarPulseAnimation(isScanning: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val wave1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave1"
    )
    val wave2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, delayMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave2"
    )

    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier.size(90.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isScanning) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val maxRadius = size.minDimension / 2f

                drawCircle(
                    color = primaryColor.copy(alpha = (1f - wave1) * 0.4f),
                    radius = maxRadius * wave1,
                    style = Stroke(width = 3.dp.toPx())
                )
                drawCircle(
                    color = primaryColor.copy(alpha = (1f - wave2) * 0.4f),
                    radius = maxRadius * wave2,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Sensors,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun ActiveTransferCard(
    transfer: TransferRecord,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 8.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { transfer.progress },
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Column {
                        Text(
                            text = transfer.fileName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${(transfer.progress * 100).toInt()}% • ${String.format(java.util.Locale.US, "%.1f MB/s", transfer.speedMbps)} (${transfer.protocol.displayName})",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(onClick = onCancel) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { transfer.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
fun QrSendScannerModal(
    onQrScanned: (MioraQrPayload) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isTorchEnabled by remember { mutableStateOf(false) }
    var cameraLensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var zoomRatio by remember { mutableFloatStateOf(0f) }
    val softBlue = Color(0xFF8AB4F8)

    val prefs = remember { context.getSharedPreferences("miora_prefs", Context.MODE_PRIVATE) }
    var showZoomSlider by remember {
        mutableStateOf(
            if (prefs.contains("show_zoom_slider")) {
                prefs.getBoolean("show_zoom_slider", false)
            } else if (prefs.contains("qr_pinch_zoom")) {
                !prefs.getBoolean("qr_pinch_zoom", true)
            } else {
                false
            }
        )
    }

    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == "show_zoom_slider") {
                showZoomSlider = p.getBoolean("show_zoom_slider", false)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val activity = remember(context) {
        var ctx: Context = context
        while (ctx is android.content.ContextWrapper && ctx !is android.app.Activity) {
            ctx = ctx.baseContext
        }
        ctx as? android.app.Activity
    }

    val hasFrontCameraFlash = remember {
        try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val frontId = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) ==
                        android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT
            }
            frontId?.let {
                cm.getCameraCharacteristics(it)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: false
        } catch (e: Exception) { false }
    }

    var isScreenFlashEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(isScreenFlashEnabled) {
        activity?.window?.let { win ->
            val lp = win.attributes
            lp.screenBrightness = if (isScreenFlashEnabled) 0.33f
            else android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            win.attributes = lp
        }
    }

    LaunchedEffect(cameraLensFacing) {
        isTorchEnabled = false
        isScreenFlashEnabled = false
    }

    DisposableEffect(Unit) {
        onDispose {
            isTorchEnabled = false
            isScreenFlashEnabled = false
            activity?.window?.let { win ->
                val lp = win.attributes
                lp.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                win.attributes = lp
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val scanned = QrImageDecoder.decodeQrFromImageUri(context, it)
                if (scanned != null) {
                    val payload = parseMioraQrPayload(scanned)
                    if (payload != null && payload.hostIp.isNotBlank()) {
                        onQrScanned(payload)
                    } else {
                        Toast.makeText(context, "Invalid Miora QR Code", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, context.getString(R.string.no_qr_found_in_image), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val density = LocalDensity.current
            val screenWidthPx = constraints.maxWidth.toFloat()
            val screenHeightPx = constraints.maxHeight.toFloat()
            val defaultBoxSizePx = with(density) { 240.dp.toPx() }

            val defaultBox = remember(screenWidthPx, screenHeightPx, defaultBoxSizePx) {
                QrBox(
                    centerX = screenWidthPx / 2f,
                    centerY = screenHeightPx / 2f,
                    width = defaultBoxSizePx,
                    height = defaultBoxSizePx,
                    rotationDegrees = 0f
                )
            }

            var detectedBox by remember { mutableStateOf<QrBox?>(null) }
            var isLocked by remember { mutableStateOf(false) }
            var pendingPayload by remember { mutableStateOf<MioraQrPayload?>(null) }

            val targetBox = detectedBox ?: defaultBox

            val animCenterX by animateFloatAsState(
                targetValue = targetBox.centerX,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "animCenterX"
            )
            val animCenterY by animateFloatAsState(
                targetValue = targetBox.centerY,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "animCenterY"
            )
            val animWidth by animateFloatAsState(
                targetValue = targetBox.width,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "animWidth"
            )
            val animHeight by animateFloatAsState(
                targetValue = targetBox.height,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "animHeight"
            )
            val animRotation by animateFloatAsState(
                targetValue = targetBox.rotationDegrees,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "animRotation"
            )

            LaunchedEffect(isLocked, pendingPayload) {
                if (isLocked && pendingPayload != null) {
                    try {
                        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                            vm?.defaultVibrator
                        } else {
                            @Suppress("DEPRECATION")
                            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                        }
                        vibrator?.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
                    } catch (_: Exception) {}

                    kotlinx.coroutines.delay(360)
                    onQrScanned(pendingPayload!!)
                }
            }

            // Camera Stream
            QrScannerView(
                onQrCodeScanned = { result, box ->
                    if (!isLocked) {
                        val payload = parseMioraQrPayload(result)
                        if (payload != null && payload.hostIp.isNotBlank()) {
                            isLocked = true
                            detectedBox = box
                            pendingPayload = payload
                        }
                    }
                },
                isTorchEnabled = isTorchEnabled,
                cameraLensFacing = cameraLensFacing,
                zoomRatio = zoomRatio,
                modifier = Modifier.fillMaxSize(),
            )

            // Screen Flash Overlay
            if (isScreenFlashEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(width = 18.dp, color = Color.White)
                )
            }

            // Animated Viewfinder Corners overlay
            GoogleStyleViewfinder(
                centerX = animCenterX,
                centerY = animCenterY,
                width = animWidth,
                height = animHeight,
                rotationDegrees = animRotation,
                color = softBlue,
                modifier = Modifier.fillMaxSize(),
            )

            // Top Control Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                // Close button (white circle with black X)
                Surface(
                    onClick = onDismiss,
                    shape = CircleShape,
                    color = Color.White,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.Black,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                // Title
                Text(
                    text = stringResource(R.string.scan_qr_code),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )

                // Actions Column: Flash, Flip Camera, Gallery
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. Flashlight toggle button (white circle)
                    Surface(
                        onClick = {
                            val isFront = cameraLensFacing == CameraSelector.LENS_FACING_FRONT
                            if (isFront && !hasFrontCameraFlash) {
                                isScreenFlashEnabled = !isScreenFlashEnabled
                            } else {
                                isTorchEnabled = !isTorchEnabled
                            }
                        },
                        shape = CircleShape,
                        color = Color.White,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isTorchEnabled || isScreenFlashEnabled)
                                    Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                                contentDescription = "Flashlight",
                                tint = Color.Black,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    // 2. Flip Camera circle button
                    Surface(
                        onClick = {
                            cameraLensFacing = if (cameraLensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                        },
                        shape = CircleShape,
                        color = Color.White,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Cameraswitch,
                                contentDescription = "Flip camera",
                                tint = Color.Black,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    // 3. Gallery picker button
                    Surface(
                        onClick = { galleryLauncher.launch("image/*") },
                        shape = CircleShape,
                        color = Color.White,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Image,
                                contentDescription = "Scan from Image",
                                tint = Color.Black,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }

            // Bottom Control Area: Zoom Slider + Scanned by Miora Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Zoom Control Pill Slider
                if (showZoomSlider) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ZoomOut,
                                contentDescription = "Zoom out",
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { zoomRatio = 0f }
                            )

                            Slider(
                                value = zoomRatio,
                                onValueChange = { zoomRatio = it },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = softBlue,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            )

                            Icon(
                                imageVector = Icons.Outlined.ZoomIn,
                                contentDescription = "Zoom in",
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { zoomRatio = 1f }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Bottom Footer ("Scanned by Miora")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(22.dp),
                    )

                    Text(
                        text = "Scanned by Miora",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    )

                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}
