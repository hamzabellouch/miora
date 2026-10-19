package com.tkno.miora.ui.receive

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
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
import com.tkno.miora.ui.component.TransferProtocolDialog
import com.tkno.miora.ui.send.SendTransferSessionView

@Composable
fun ReceiveScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("miora_prefs", Context.MODE_PRIVATE) }
    var useClassicTaskbar by remember { mutableStateOf(prefs.getBoolean("use_classic_taskbar", false)) }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            if (key == "use_classic_taskbar") {
                useClassicTaskbar = p.getBoolean("use_classic_taskbar", false)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val deviceName by TransferController.deviceName.collectAsState()
    val isDiscoverable by TransferController.isDiscoverable.collectAsState()
    val incomingRequest by TransferController.incomingRequest.collectAsState()
    val activeReceive by TransferController.activeReceive.collectAsState()
    val activeReceiveSession by TransferController.activeReceiveSession.collectAsState()
    val isWifiEnabled by DeviceConnectivityManager.isWifiEnabled.collectAsState()
    val isBluetoothEnabled by DeviceConnectivityManager.isBluetoothEnabled.collectAsState()
    val use5GhzBand by DeviceConnectivityManager.use5GhzBand.collectAsState()

    var selectedReceiveMode by remember { mutableStateOf<TransferProtocol?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isHotspotLoading by remember { mutableStateOf(false) }
    var currentHotspotSsid by remember { mutableStateOf("") }
    var currentHotspotIp by remember { mutableStateOf("") }
    var showProtocolDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isDiscoverable, deviceName, isBluetoothEnabled, isWifiEnabled) {
        if (isDiscoverable) {
            MioraTransferEngine.startListeningServer(deviceName)
        }
    }

    // Manage Hotspot & QR Code when Wi-Fi Direct or Local Wi-Fi mode is active
    LaunchedEffect(selectedReceiveMode, isDiscoverable, isWifiEnabled, deviceName, use5GhzBand) {
        if (isDiscoverable && isWifiEnabled) {
            when (selectedReceiveMode) {
                TransferProtocol.WIFI_DIRECT -> {
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
                            // Fallback to local LAN IP only if LAN transfer is enabled
                            if (DeviceConnectivityManager.isLanTransferEnabled()) {
                                val lanIps = DeviceConnectivityManager.getAllLocalIpAddresses().filter { it != "127.0.0.1" }
                                val ipCsv = if (lanIps.isNotEmpty()) lanIps.joinToString(",") else "127.0.0.1"
                                currentHotspotIp = ipCsv
                                currentHotspotSsid = ""
                                val qrContent = "MIORA_WIFI:::$ipCsv:${MioraTransferEngine.MIORA_PORT}"
                                qrBitmap = QrGenerator.generate(qrContent, 600)
                            } else {
                                qrBitmap = null
                            }
                            isHotspotLoading = false
                        }
                    )
                }
                TransferProtocol.LOCAL_WIFI -> {
                    DeviceConnectivityManager.stopReceiverAccessPoint()
                    isHotspotLoading = false
                    val lanIps = DeviceConnectivityManager.getAllLocalIpAddresses().filter { it != "127.0.0.1" }
                    val ipCsv = if (lanIps.isNotEmpty()) lanIps.joinToString(",") else "127.0.0.1"
                    currentHotspotIp = ipCsv
                    currentHotspotSsid = ""
                    val qrContent = "MIORA_WIFI:::$ipCsv:${MioraTransferEngine.MIORA_PORT}"
                    qrBitmap = QrGenerator.generate(qrContent, 600)
                }
                else -> {
                    DeviceConnectivityManager.stopReceiverAccessPoint()
                    isHotspotLoading = false
                    qrBitmap = null
                }
            }
        } else {
            DeviceConnectivityManager.stopReceiverAccessPoint()
            isHotspotLoading = false
            qrBitmap = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            DeviceConnectivityManager.stopReceiverAccessPoint()
            TransferController.setDiscoverable(false)
            MioraTransferEngine.stopListeningServer()
            TransferController.clearReceiveSession()
        }
    }

    var showEditNameDialog by remember { mutableStateOf(false) }
    var showRequirementsDialog by remember { mutableStateOf(false) }

    if (showRequirementsDialog) {
        com.tkno.miora.ui.component.AppRequirementsDialog(
            onDismiss = { showRequirementsDialog = false }
        )
    }

    if (showProtocolDialog) {
        TransferProtocolDialog(
            selectedProtocol = selectedReceiveMode,
            onProtocolSelect = { protocol ->
                selectedReceiveMode = protocol
                TransferController.setProtocol(context, protocol)
                if (!isDiscoverable) {
                    TransferController.setDiscoverable(true)
                }
            },
            onDismiss = { showProtocolDialog = false }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        if (activeReceiveSession != null) {
            SendTransferSessionView(
                session = activeReceiveSession!!,
                useClassicTaskbar = useClassicTaskbar,
                isReceiver = true,
                onCloseSession = {
                    TransferController.clearReceiveSession()
                }
            )
        } else {
            LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(
                top = 24.dp,
                bottom = if (useClassicTaskbar) 120.dp else 220.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with Title & Transfer Protocol Capsule
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

                    // Transfer Protocol Capsule
                    Surface(
                        onClick = { showProtocolDialog = true },
                        shape = CircleShape,
                        color = if (selectedReceiveMode != null) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier
                                .height(36.dp)
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = when (selectedReceiveMode) {
                                    TransferProtocol.WIFI_DIRECT -> Icons.Outlined.Wifi
                                    TransferProtocol.LOCAL_WIFI -> Icons.Outlined.Router
                                    TransferProtocol.BLUETOOTH -> Icons.Outlined.Bluetooth
                                    null -> Icons.Outlined.Tune
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (selectedReceiveMode != null) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = selectedReceiveMode?.displayName ?: stringResource(R.string.select_protocol),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = if (selectedReceiveMode != null) MaterialTheme.colorScheme.onSecondaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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

                        if (!isWifiEnabled || !isBluetoothEnabled) {
                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(14.dp))
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

            // Central Display: Prompt, QR Code (Wi-Fi Direct) or Radar (Bluetooth)
            if (selectedReceiveMode == null) {
                // Initial State: Prompt to select a Transfer Protocol
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showProtocolDialog = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.SwapHoriz,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.select_receive_protocol_title),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.select_receive_protocol_desc),
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            } else if (isDiscoverable) {
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
                            if (selectedReceiveMode == TransferProtocol.WIFI_DIRECT || selectedReceiveMode == TransferProtocol.LOCAL_WIFI) {
                                // Wi-Fi Direct or Local Wi-Fi QR Code Mode
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
            } else {
                // Discoverability is turned off
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                shape = CircleShape,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.VisibilityOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.device_hidden_title),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = stringResource(R.string.device_hidden_desc),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { TransferController.setDiscoverable(true) },
                                shape = CircleShape,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(stringResource(R.string.make_visible))
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
                    .then(
                        if (!useClassicTaskbar) {
                            Modifier
                                .navigationBarsPadding()
                                .padding(bottom = 90.dp, start = 22.dp, end = 22.dp)
                        } else {
                            Modifier.padding(bottom = 16.dp, start = 22.dp, end = 22.dp)
                        }
                    )
            )
        }
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
                            text = "${(transfer.progress * 100).toInt()}% • ${transfer.formattedSpeed} (${transfer.protocol.displayName})",
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
