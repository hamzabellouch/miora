package com.tkno.miora.model

import android.content.Context
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class IncomingRequest(
    val id: String = UUID.randomUUID().toString(),
    val senderName: String,
    val fileCount: Int,
    val totalSize: Long,
    val firstFileName: String,
    val protocol: TransferProtocol,
    val fileNames: List<String> = emptyList(),
    val fileSizes: List<Long> = emptyList()
) {
    val formattedSize: String
        get() = formatFileSize(totalSize)
}

data class MioraQrPayload(
    val ssid: String,
    val passphrase: String,
    val hostIp: String,
    val hostIps: List<String> = emptyList(),
    val port: Int
)

fun parseMioraQrPayload(raw: String): MioraQrPayload? {
    if (!raw.startsWith("MIORA_WIFI:")) return null
    val payload = raw.removePrefix("MIORA_WIFI:")
    val parts = payload.split(":")

    var ssid = ""
    var passphrase = ""
    var ipsRaw = ""
    var port = MioraTransferEngine.MIORA_PORT

    when {
        parts.size >= 4 -> {
            ssid = parts[0]
            passphrase = parts[1]
            ipsRaw = parts[2]
            port = parts[3].toIntOrNull() ?: MioraTransferEngine.MIORA_PORT
        }
        parts.size == 3 -> {
            ssid = parts[0]
            passphrase = ""
            ipsRaw = parts[1]
            port = parts[2].toIntOrNull() ?: MioraTransferEngine.MIORA_PORT
        }
        parts.size == 2 -> {
            ssid = ""
            passphrase = ""
            ipsRaw = parts[0]
            port = parts[1].toIntOrNull() ?: MioraTransferEngine.MIORA_PORT
        }
        else -> return null
    }

    val ipList = ipsRaw.split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() && it != "127.0.0.1" && it != "0.0.0.0" }
    val primaryIp = ipList.firstOrNull() ?: ipsRaw.trim()

    return MioraQrPayload(
        ssid = ssid.trim(),
        passphrase = passphrase.trim(),
        hostIp = primaryIp,
        hostIps = ipList,
        port = port
    )
}

object TransferController {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Device configuration
    private val _deviceName = MutableStateFlow("Miora Phone (${Build.MODEL})")
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    // Locked/Disabled by default so devices do not conflict until explicitly enabled
    private val _isDiscoverable = MutableStateFlow(false)
    val isDiscoverable: StateFlow<Boolean> = _isDiscoverable.asStateFlow()

    private val _selectedProtocol = MutableStateFlow(TransferProtocol.WIFI_DIRECT)
    val selectedProtocol: StateFlow<TransferProtocol> = _selectedProtocol.asStateFlow()

    // Discovery state
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = _discoveredPeers.asStateFlow()

    // Active transfers
    private val _activeSend = MutableStateFlow<TransferRecord?>(null)
    val activeSend: StateFlow<TransferRecord?> = _activeSend.asStateFlow()

    private val _activeReceive = MutableStateFlow<TransferRecord?>(null)
    val activeReceive: StateFlow<TransferRecord?> = _activeReceive.asStateFlow()

    // Transfer Session for rich real-time sending UI
    private val _activeSession = MutableStateFlow<TransferSessionState?>(null)
    val activeSession: StateFlow<TransferSessionState?> = _activeSession.asStateFlow()

    // Receive Transfer Session for rich real-time receiving UI
    private val _activeReceiveSession = MutableStateFlow<TransferSessionState?>(null)
    val activeReceiveSession: StateFlow<TransferSessionState?> = _activeReceiveSession.asStateFlow()

    // Last scanned QR payload
    private val _lastScannedQrPayload = MutableStateFlow<MioraQrPayload?>(null)
    val lastScannedQrPayload: StateFlow<MioraQrPayload?> = _lastScannedQrPayload.asStateFlow()

    // Incoming request dialog/sheet
    private val _incomingRequest = MutableStateFlow<IncomingRequest?>(null)
    val incomingRequest: StateFlow<IncomingRequest?> = _incomingRequest.asStateFlow()

    // Shared media incoming from external apps (ACTION_SEND / ACTION_SEND_MULTIPLE)
    private val _pendingSharedUris = MutableStateFlow<List<Uri>>(emptyList())
    val pendingSharedUris: StateFlow<List<Uri>> = _pendingSharedUris.asStateFlow()

    fun setPendingSharedUris(uris: List<Uri>) {
        if (uris.isNotEmpty()) {
            val current = _activeSession.value
            if (current != null && current.isFinished) {
                _activeSession.value = null
            }
            _pendingSharedUris.value = uris
        }
    }

    fun clearPendingSharedUris() {
        _pendingSharedUris.value = emptyList()
    }

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("miora_prefs", Context.MODE_PRIVATE)
        val savedName = prefs.getString("miora_device_name", null)
        if (!savedName.isNullOrEmpty()) {
            _deviceName.value = savedName
        } else {
            val defaultName = "Miora-${Build.MODEL.take(12)}"
            _deviceName.value = defaultName
            prefs.edit().putString("miora_device_name", defaultName).apply()
        }

        val savedProto = prefs.getString("miora_default_protocol", TransferProtocol.WIFI_DIRECT.name)
        try {
            val proto = TransferProtocol.valueOf(savedProto ?: TransferProtocol.WIFI_DIRECT.name)
            if (proto == TransferProtocol.LOCAL_WIFI && !DeviceConnectivityManager.isLanTransferEnabled(context)) {
                _selectedProtocol.value = TransferProtocol.WIFI_DIRECT
            } else {
                _selectedProtocol.value = proto
            }
        } catch (_: Exception) {
            _selectedProtocol.value = TransferProtocol.WIFI_DIRECT
        }

        // Initialize Hardware Connectivity & Network Transfer Engine
        DeviceConnectivityManager.init(context)
        DeviceConnectivityManager.setWifiP2pDeviceName(_deviceName.value)
        MioraTransferEngine.init(context)

        if (_isDiscoverable.value) {
            MioraTransferEngine.startListeningServer(_deviceName.value)
        }
    }

    fun setDeviceName(context: Context, name: String) {
        _deviceName.value = name
        val prefs = context.getSharedPreferences("miora_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("miora_device_name", name).apply()
        DeviceConnectivityManager.setWifiP2pDeviceName(name)

        if (_isDiscoverable.value) {
            MioraTransferEngine.startListeningServer(name)
        }
    }

    fun setProtocol(context: Context, protocol: TransferProtocol) {
        _selectedProtocol.value = protocol
        val prefs = context.getSharedPreferences("miora_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("miora_default_protocol", protocol.name).apply()
    }

    fun toggleDiscoverability() {
        _isDiscoverable.value = !_isDiscoverable.value
        if (_isDiscoverable.value) {
            MioraTransferEngine.startListeningServer(_deviceName.value)
        } else {
            MioraTransferEngine.stopListeningServer()
        }
    }

    fun setDiscoverable(enabled: Boolean) {
        if (_isDiscoverable.value != enabled) {
            _isDiscoverable.value = enabled
            if (enabled) {
                MioraTransferEngine.startListeningServer(_deviceName.value)
            } else {
                MioraTransferEngine.stopListeningServer()
            }
        }
    }

    private var scanTimeoutJob: Job? = null

    fun startScanning(timeoutMillis: Long = 60_000L) {
        scanTimeoutJob?.cancel()

        if (!_isScanning.value) {
            _isScanning.value = true
            _discoveredPeers.value = emptyList()
        }

        MioraTransferEngine.startDiscovery { peer ->
            // Protocol strictly filters and displays only verified Miora devices
            if (peer.isMioraDevice) {
                scope.launch {
                    val current = _discoveredPeers.value
                    val existingIndex = current.indexOfFirst {
                        it.id == peer.id ||
                        it.deviceAddress == peer.deviceAddress ||
                        (it.hostAddress != null && it.hostAddress == peer.hostAddress) ||
                        it.name.equals(peer.name, ignoreCase = true)
                    }
                    if (existingIndex != -1) {
                        val updatedList = current.toMutableList()
                        val existing = updatedList[existingIndex]
                        val bestHost = peer.hostAddress ?: existing.hostAddress
                        val bestBtAddress = if (peer.deviceAddress.contains(":")) peer.deviceAddress else existing.deviceAddress
                        val bestPsm = if (peer.blePsm > 0) peer.blePsm else existing.blePsm
                        updatedList[existingIndex] = existing.copy(
                            name = if (peer.name.isNotBlank() && peer.name != "Miora Device") peer.name else existing.name,
                            deviceAddress = bestBtAddress,
                            hostAddress = bestHost,
                            port = if (peer.port > 0) peer.port else existing.port,
                            blePsm = bestPsm,
                            protocol = when {
                                peer.protocol == TransferProtocol.LOCAL_WIFI || existing.protocol == TransferProtocol.LOCAL_WIFI -> TransferProtocol.LOCAL_WIFI
                                peer.protocol == TransferProtocol.WIFI_DIRECT || existing.protocol == TransferProtocol.WIFI_DIRECT -> TransferProtocol.WIFI_DIRECT
                                else -> TransferProtocol.BLUETOOTH
                            },
                            signalStrength = Math.max(existing.signalStrength, peer.signalStrength)
                        )
                        _discoveredPeers.value = updatedList
                    } else {
                        _discoveredPeers.value = current + peer
                    }
                }
            }
        }

        // Auto-stop scanning after 60s to prevent battery drain
        scanTimeoutJob = scope.launch {
            delay(timeoutMillis)
            stopScanning()
        }
    }

    fun stopScanning() {
        scanTimeoutJob?.cancel()
        scanTimeoutJob = null
        MioraTransferEngine.stopDiscovery()
        _isScanning.value = false
    }

    const val MAX_FILES_WIFI = 500
    const val MAX_FILES_BLUETOOTH = 100

    fun startSendTransfer(
        peer: DiscoveredPeer,
        items: List<MediaItem>,
        protocolPreference: TransferProtocol,
        onComplete: (Boolean) -> Unit = {}
    ) {
        if (items.isEmpty()) return
        val maxLimit = if (protocolPreference == TransferProtocol.BLUETOOTH) MAX_FILES_BLUETOOTH else MAX_FILES_WIFI
        val safeItems = if (items.size > maxLimit) items.take(maxLimit) else items

        MioraTransferEngine.startSend(
            peer = peer,
            items = safeItems,
            protocolPreference = protocolPreference,
            senderDeviceName = _deviceName.value,
            onComplete = onComplete
        )
    }

    fun startSendTransferViaQr(
        ssid: String,
        passphrase: String,
        hostIp: String,
        hostIps: List<String> = emptyList(),
        port: Int,
        items: List<MediaItem>,
        onComplete: (Boolean) -> Unit = {}
    ) {
        if (items.isEmpty()) return
        val safeItems = if (items.size > MAX_FILES_WIFI) items.take(MAX_FILES_WIFI) else items

        MioraTransferEngine.startSendToQrHost(
            ssid = ssid,
            passphrase = passphrase,
            hostIp = hostIp,
            hostIps = hostIps,
            port = port,
            items = safeItems,
            senderDeviceName = _deviceName.value,
            onComplete = onComplete
        )
    }

    fun setLastScannedQrPayload(payload: MioraQrPayload?) {
        _lastScannedQrPayload.value = payload
    }

    fun startTransferSession(
        items: List<MediaItem>,
        protocol: TransferProtocol = TransferProtocol.WIFI_DIRECT,
        receiverName: String = ""
    ) {
        val totalBytes = items.sumOf { it.size }
        val currentSession = _activeSession.value

        if (currentSession != null && currentSession.completedRecords.isNotEmpty()) {
            val prevRecords = currentSession.completedRecords
            val prevCompletedCount = prevRecords.size
            val prevBytes = currentSession.totalBytes.coerceAtLeast(prevRecords.sumOf { it.fileSize })
            _activeSession.value = currentSession.copy(
                totalFiles = prevCompletedCount + items.size,
                completedFilesCount = prevCompletedCount,
                baseBytesTransferred = prevBytes,
                bytesTransferred = prevBytes,
                totalBytes = prevBytes + totalBytes,
                speedMbps = 0f,
                currentFileName = items.firstOrNull()?.name ?: "",
                status = TransferStatus.CONNECTING,
                isFinished = false,
                isSuccess = false,
                protocol = protocol,
                receiverName = receiverName.ifBlank { currentSession.receiverName }
            )
        } else {
            _activeSession.value = TransferSessionState(
                totalFiles = items.size,
                completedFilesCount = 0,
                baseBytesTransferred = 0L,
                bytesTransferred = 0L,
                totalBytes = totalBytes,
                speedMbps = 0f,
                currentFileName = items.firstOrNull()?.name ?: "",
                completedRecords = emptyList(),
                status = TransferStatus.CONNECTING,
                isFinished = false,
                isSuccess = false,
                protocol = protocol,
                receiverName = receiverName
            )
        }
    }

    fun updateSessionProgress(
        bytesTransferred: Long,
        speedMbps: Float,
        currentFileName: String
    ) {
        _activeSession.value = _activeSession.value?.let { session ->
            session.copy(
                bytesTransferred = session.baseBytesTransferred + bytesTransferred,
                speedMbps = speedMbps,
                currentFileName = currentFileName,
                status = TransferStatus.TRANSFERRING
            )
        }
    }

    fun onFileCompletedInSession(record: TransferRecord) {
        TransferHistoryManager.addRecord(record)
        _activeSession.value = _activeSession.value?.let { session ->
            val updated = session.completedRecords + record
            session.copy(
                completedFilesCount = (session.completedFilesCount + 1).coerceAtMost(session.totalFiles),
                completedRecords = updated
            )
        }
    }

    fun finishTransferSession(success: Boolean, finalSpeedMbps: Float? = null) {
        _activeSession.value = _activeSession.value?.let { session ->
            session.copy(
                isFinished = true,
                isSuccess = success,
                status = if (success) TransferStatus.COMPLETED else TransferStatus.FAILED,
                completedFilesCount = if (success) session.totalFiles else session.completedFilesCount,
                bytesTransferred = if (success) session.totalBytes else session.bytesTransferred,
                baseBytesTransferred = if (success) session.totalBytes else session.baseBytesTransferred,
                speedMbps = finalSpeedMbps ?: session.speedMbps
            )
        }
    }

    fun clearTransferSession() {
        _activeSession.value = null
    }

    fun updateActiveSend(record: TransferRecord?) {
        _activeSend.value = record
    }

    fun completeSend(record: TransferRecord) {
        scope.launch {
            _activeSend.value = record
            delay(1000)
            _activeSend.value = null
        }
    }

    fun cancelSendTransfer() {
        MioraTransferEngine.cancelSend()
        val current = _activeSend.value
        val hasCompletedFiles = !_activeSession.value?.completedRecords.isNullOrEmpty()
        if (current != null && !hasCompletedFiles) {
            val cancelledRecord = current.copy(status = TransferStatus.CANCELLED)
            TransferHistoryManager.addRecord(cancelledRecord)
        }
        _activeSend.value = null
        _activeSession.value = _activeSession.value?.copy(
            isFinished = true,
            isSuccess = false,
            status = TransferStatus.CANCELLED
        )
    }

    fun postIncomingRequest(request: IncomingRequest) {
        _incomingRequest.value = request
    }

    fun acceptIncoming(request: IncomingRequest) {
        _incomingRequest.value = null
        MioraTransferEngine.acceptIncoming()
    }

    fun declineIncoming() {
        _incomingRequest.value = null
        MioraTransferEngine.declineIncoming()
    }

    fun startReceiveSession(
        fileCount: Int,
        totalBytes: Long,
        firstFileName: String,
        protocol: TransferProtocol,
        senderName: String
    ) {
        val currentSession = _activeReceiveSession.value

        if (currentSession != null && currentSession.completedRecords.isNotEmpty()) {
            val prevRecords = currentSession.completedRecords
            val prevCompletedCount = prevRecords.size
            val prevBytes = currentSession.totalBytes.coerceAtLeast(prevRecords.sumOf { it.fileSize })
            _activeReceiveSession.value = currentSession.copy(
                totalFiles = prevCompletedCount + fileCount,
                completedFilesCount = prevCompletedCount,
                baseBytesTransferred = prevBytes,
                bytesTransferred = prevBytes,
                totalBytes = prevBytes + totalBytes,
                speedMbps = 0f,
                currentFileName = firstFileName,
                status = TransferStatus.TRANSFERRING,
                isFinished = false,
                isSuccess = false,
                protocol = protocol,
                receiverName = senderName.ifBlank { currentSession.receiverName },
                direction = TransferDirection.RECEIVE
            )
        } else {
            _activeReceiveSession.value = TransferSessionState(
                totalFiles = fileCount,
                completedFilesCount = 0,
                baseBytesTransferred = 0L,
                bytesTransferred = 0L,
                totalBytes = totalBytes,
                speedMbps = 0f,
                currentFileName = firstFileName,
                completedRecords = emptyList(),
                status = TransferStatus.TRANSFERRING,
                isFinished = false,
                isSuccess = false,
                protocol = protocol,
                receiverName = senderName,
                direction = TransferDirection.RECEIVE
            )
        }
    }

    fun updateReceiveSessionProgress(
        bytesReceived: Long,
        speedMbps: Float,
        currentFileName: String
    ) {
        _activeReceiveSession.value = _activeReceiveSession.value?.let { session ->
            session.copy(
                bytesTransferred = session.baseBytesTransferred + bytesReceived,
                speedMbps = speedMbps,
                currentFileName = currentFileName,
                status = TransferStatus.TRANSFERRING
            )
        }
    }

    fun onFileCompletedInReceiveSession(record: TransferRecord) {
        TransferHistoryManager.addRecord(record)
        _activeReceiveSession.value = _activeReceiveSession.value?.let { session ->
            val updated = session.completedRecords + record
            session.copy(
                completedFilesCount = (session.completedFilesCount + 1).coerceAtMost(session.totalFiles),
                completedRecords = updated
            )
        }
    }

    fun finishReceiveSession(success: Boolean, finalSpeedMbps: Float? = null) {
        _activeReceiveSession.value = _activeReceiveSession.value?.let { session ->
            session.copy(
                isFinished = true,
                isSuccess = success,
                status = if (success) TransferStatus.COMPLETED else TransferStatus.FAILED,
                completedFilesCount = if (success) session.totalFiles else session.completedFilesCount,
                bytesTransferred = if (success) session.totalBytes else session.bytesTransferred,
                baseBytesTransferred = if (success) session.totalBytes else session.baseBytesTransferred,
                speedMbps = finalSpeedMbps ?: session.speedMbps
            )
        }
    }

    fun clearReceiveSession() {
        _activeReceiveSession.value = null
    }

    fun updateActiveReceive(record: TransferRecord?) {
        _activeReceive.value = record
    }

    fun completeReceive(record: TransferRecord) {
        scope.launch {
            _activeReceive.value = record
            delay(1200)
            _activeReceive.value = null
        }
    }

    fun cancelReceiveTransfer() {
        MioraTransferEngine.cancelReceive()
        val current = _activeReceive.value
        val hasCompletedFiles = !_activeReceiveSession.value?.completedRecords.isNullOrEmpty()
        if (current != null && !hasCompletedFiles) {
            val cancelledRecord = current.copy(status = TransferStatus.CANCELLED)
            TransferHistoryManager.addRecord(cancelledRecord)
        }
        _activeReceive.value = null
        _activeReceiveSession.value = _activeReceiveSession.value?.copy(
            isFinished = true,
            isSuccess = false,
            status = TransferStatus.CANCELLED
        )
    }
}
