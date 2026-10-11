package com.tkno.miora.ui.receive

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkno.miora.QrGenerator
import com.tkno.miora.R
import com.tkno.miora.model.*

@Composable
fun ReceiveScreen() {
    val context = LocalContext.current
    val deviceName by TransferController.deviceName.collectAsState()
    val isDiscoverable by TransferController.isDiscoverable.collectAsState()
    val incomingRequest by TransferController.incomingRequest.collectAsState()
    val activeReceive by TransferController.activeReceive.collectAsState()
    val isWifiEnabled by DeviceConnectivityManager.isWifiEnabled.collectAsState()
    val isBluetoothEnabled by DeviceConnectivityManager.isBluetoothEnabled.collectAsState()
    val use5GhzBand by DeviceConnectivityManager.use5GhzBand.collectAsState()

    var selectedReceiveMode by rememberSaveable { mutableStateOf(TransferProtocol.WIFI_DIRECT) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isHotspotLoading by remember { mutableStateOf(false) }
    var currentHotspotSsid by remember { mutableStateOf("") }
    var currentHotspotIp by remember { mutableStateOf("") }

    LaunchedEffect(isDiscoverable, deviceName, isBluetoothEnabled, isWifiEnabled) {
        if (isDiscoverable) {
            MioraTransferEngine.startListeningServer(deviceName)
        }
    }

    // Manage Hotspot & QR Code when Wi-Fi Direct mode is active
    LaunchedEffect(selectedReceiveMode, isDiscoverable, isWifiEnabled, deviceName, use5GhzBand) {
        if (isDiscoverable && selectedReceiveMode == TransferProtocol.WIFI_DIRECT && isWifiEnabled) {
            isHotspotLoading = true
            DeviceConnectivityManager.startReceiverAccessPoint(
                deviceName = deviceName,
                onStarted = { ssid, password, ips ->
                    currentHotspotSsid = ssid
                    currentHotspotIp = ips.firstOrNull() ?: "192.168.49.1"
                    val ipCsv = ips.joinToString(",")
                    val qrContent = "MIORA_WIFI:$ssid:$password:$ipCsv:${MioraTransferEngine.MIORA_PORT}"
                    qrBitmap = QrGenerator.generate(qrContent, 600)
                    isHotspotLoading = false
                },
                onFailed = { reason ->
                    // Fallback to local LAN IP if Hotspot is not supported/failed
                    val lanIps = DeviceConnectivityManager.getAllLocalIpAddresses().filter { it != "127.0.0.1" }
                    val ipCsv = if (lanIps.isNotEmpty()) lanIps.joinToString(",") else "127.0.0.1"
                    currentHotspotIp = ipCsv
                    currentHotspotSsid = ""
                    val qrContent = "MIORA_WIFI:::$ipCsv:${MioraTransferEngine.MIORA_PORT}"
                    qrBitmap = QrGenerator.generate(qrContent, 600)
                    isHotspotLoading = false
                }
            )
        } else {
            DeviceConnectivityManager.stopReceiverAccessPoint()
            isHotspotLoading = false
            qrBitmap = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            DeviceConnectivityManager.stopReceiverAccessPoint()
        }
    }

    var showEditNameDialog by remember { mutableStateOf(false) }
    var showRequirementsDialog by remember { mutableStateOf(false) }

    if (showRequirementsDialog) {
        com.tkno.miora.ui.component.AppRequirementsDialog(
            onDismiss = { showRequirementsDialog = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 24.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.tab_receive),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.receive_mode_title),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Device Status & Visibility Card
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.device_visible_as),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = deviceName,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    IconButton(
                                        onClick = { showEditNameDialog = true },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Edit,
                                            contentDescription = "Edit name",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Switch(
                                checked = isDiscoverable,
                                onCheckedChange = { TransferController.toggleDiscoverability() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(14.dp))

                        // Interactive Mode Selectors (Wi-Fi Direct with QR vs Bluetooth with Radar)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ReceiveModeCard(
                                icon = Icons.Outlined.Wifi,
                                title = "Wi-Fi Direct",
                                subtitle = "QR Code Mode",
                                isSelected = selectedReceiveMode == TransferProtocol.WIFI_DIRECT,
                                isEnabled = isWifiEnabled,
                                onClick = {
                                    if (!isWifiEnabled) {
                                        DeviceConnectivityManager.openWifiSettings(context)
                                    } else {
                                        selectedReceiveMode = TransferProtocol.WIFI_DIRECT
                                        if (!isDiscoverable) {
                                            TransferController.toggleDiscoverability()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            ReceiveModeCard(
                                icon = Icons.Outlined.Bluetooth,
                                title = "Bluetooth",
                                subtitle = "Radar Mode",
                                isSelected = selectedReceiveMode == TransferProtocol.BLUETOOTH,
                                isEnabled = isBluetoothEnabled,
                                onClick = {
                                    if (!isBluetoothEnabled) {
                                        DeviceConnectivityManager.openBluetoothSettings(context)
                                    } else {
                                        selectedReceiveMode = TransferProtocol.BLUETOOTH
                                        if (!isDiscoverable) {
                                            TransferController.toggleDiscoverability()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (!isWifiEnabled || !isBluetoothEnabled) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.WarningAmber,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.requirements_missing_warning),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
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
                    }
                }
            }

            // Central Display: QR Code (for Wi-Fi Direct) OR Radar (for Bluetooth)
            if (isDiscoverable) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(26.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp, horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (selectedReceiveMode == TransferProtocol.WIFI_DIRECT) {
                                // Wi-Fi Direct QR Code Mode
                                if (isHotspotLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(48.dp),
                                        strokeWidth = 3.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                Text(
                                    text = "Creating fast Wi-Fi Direct connection...",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            } else if (qrBitmap != null) {
                                Surface(
                                    color = Color.White,
                                    shape = RoundedCornerShape(20.dp),
                                    shadowElevation = 6.dp,
                                    modifier = Modifier.size(220.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(12.dp)
                                    ) {
                                        Image(
                                            bitmap = qrBitmap!!.asImageBitmap(),
                                            contentDescription = "Receiver QR Code",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Outlined.QrCodeScanner,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Scan QR Code to Send",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (currentHotspotSsid.isNotBlank()) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    Icons.Outlined.Wifi,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = currentHotspotSsid,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "Point the Sender's camera at this QR code to connect and receive files instantly.",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = "Wi-Fi is disabled. Turn on Wi-Fi above to generate QR code.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            // Bluetooth Radar Mode
                            BigRadarAnimation(isActive = isDiscoverable && isBluetoothEnabled)

                            Text(
                                text = if (isBluetoothEnabled) stringResource(R.string.listening_connections)
                                else "Bluetooth is disabled",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = if (isBluetoothEnabled)
                                    "Nearby Miora senders can discover this device over Bluetooth and send files."
                                else "Turn on Bluetooth to enable nearby discovery.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                    }
                }
            }
            }
        }

        // Active Receive Floating Card
        activeReceive?.let { transfer ->
            ActiveReceiveCard(
                transfer = transfer,
                onCancel = { TransferController.cancelReceiveTransfer() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp)
            )
        }

        // Incoming Request Alert Card / Dialog
        incomingRequest?.let { request ->
            IncomingRequestDialog(
                request = request,
                onAccept = { TransferController.acceptIncoming(request) },
                onDecline = { TransferController.declineIncoming() }
            )
        }

        // Edit Device Name Dialog
        if (showEditNameDialog) {
            EditNameDialog(
                currentName = deviceName,
                onDismiss = { showEditNameDialog = false },
                onSave = { newName ->
                    TransferController.setDeviceName(context, newName)
                    showEditNameDialog = false
                }
            )
        }
    }
}

@Composable
fun ReceiveModeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                           else if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant
                           else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    if (isSelected) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun BigRadarAnimation(isActive: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "bigRadar")
    val wave1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "w1"
    )
    val wave2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, delayMillis = 860, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "w2"
    )
    val wave3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, delayMillis = 1720, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "w3"
    )

    val primary = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier.size(160.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isActive) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val maxRadius = size.minDimension / 2f

                drawCircle(
                    color = primary.copy(alpha = (1f - wave1) * 0.35f),
                    radius = maxRadius * wave1,
                    style = Stroke(width = 3.dp.toPx())
                )
                drawCircle(
                    color = primary.copy(alpha = (1f - wave2) * 0.35f),
                    radius = maxRadius * wave2,
                    style = Stroke(width = 3.dp.toPx())
                )
                drawCircle(
                    color = primary.copy(alpha = (1f - wave3) * 0.35f),
                    radius = maxRadius * wave3,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
fun IncomingRequestDialog(
    request: IncomingRequest,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDecline,
        shape = RoundedCornerShape(24.dp),
        icon = {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.UploadFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.incoming_transfer),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(
                        R.string.incoming_transfer_prompt,
                        request.senderName,
                        request.fileCount,
                        request.formattedSize
                    ),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Protocol: ${request.protocol.displayName}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.accept))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDecline,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.decline))
            }
        }
    )
}

@Composable
fun ActiveReceiveCard(
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
                            text = stringResource(R.string.receiving_file, transfer.fileName),
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
fun EditNameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.device_name)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.device_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (text.isNotBlank()) onSave(text.trim()) }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
