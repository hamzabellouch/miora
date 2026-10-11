package com.tkno.miora.model

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("MissingPermission")
object MioraTransferEngine {

    const val MIORA_PORT = 8988
    const val MIORA_UDP_BEACON_PORT = 8989
    const val PROTOCOL_MAGIC = "MIORA_TRANSFER_V1"
    private const val NSD_SERVICE_TYPE = "_miora._tcp"
    private const val BUFFER_SIZE = 512 * 1024 // 512 KB Ultra-High-Speed Buffer

    val MIORA_BLE_SERVICE_UUID: ParcelUuid = ParcelUuid.fromString("0000fef0-0000-1000-8000-00805f9b34fb")
    val MIORA_BLE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    val MIORA_RFCOMM_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")

    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var appContext: Context? = null

    // Multicast & High-Performance Wi-Fi Locks for Android Wi-Fi
    private var multicastLock: WifiManager.MulticastLock? = null
    private var wifiHighPerfLock: WifiManager.WifiLock? = null

    // Server State (Wi-Fi TCP)
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var activeServerSocketConnection: Socket? = null
    private var currentListeningDeviceName: String = ""

    // Server State (UDP LAN Beacon Broadcaster)
    private var udpBeaconJob: Job? = null

    // LAN Subnet Scanner State
    private var lanScanJob: Job? = null

    // Server State (Bluetooth RFCOMM, BLE L2CAP & BLE Advertising)
    private var bluetoothServerSocket: BluetoothServerSocket? = null
    private var bluetoothServerJob: Job? = null
    private var bleL2capServerSocket: BluetoothServerSocket? = null
    private var bleL2capServerJob: Job? = null
    private var currentBleL2capPsm: Int = 0
    private var activeBluetoothSocketConnection: BluetoothSocket? = null
    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private var bleAdvertiseCallback: AdvertiseCallback? = null
    private var isBleAdvertising = false

    // Client State (Active Outgoing Transfer)
    private var clientJob: Job? = null
    private var activeClientTcpSocket: Socket? = null
    private var activeClientBtSocket: BluetoothSocket? = null

    // Common Receiver State
    private var acceptanceDeferred: CompletableDeferred<Boolean>? = null
    private var currentTempFile: File? = null

    // Discovery State (UDP LAN Listener)
    private var udpDiscoveryJob: Job? = null
    private var udpSocket: DatagramSocket? = null

    // NSD Discovery State
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var isNsdRegistered = false
    private var isNsdDiscovering = false

    // Sequential NSD Resolve Queue
    private val resolveQueue = ConcurrentLinkedQueue<NsdServiceInfo>()
    private val isResolving = AtomicBoolean(false)

    // Bluetooth Discovery State
    private var bleScanner: BluetoothLeScanner? = null
    private var bleScanCallback: ScanCallback? = null
    private var isBleScanning = false
    private var bondedProbeJob: Job? = null
    private var activeDiscoveryCallback: ((DiscoveredPeer) -> Unit)? = null

    // Wi-Fi Direct Peer Collector Job
    private var wifiP2pJob: Job? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        nsdManager = appContext?.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }

    private fun acquireMulticastLock() {
        val wifi = appContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        try {
            if (multicastLock == null) {
                multicastLock = wifi.createMulticastLock("MioraMulticastLock").apply {
                    setReferenceCounted(false)
                }
            }
            if (multicastLock?.isHeld == false) {
                multicastLock?.acquire()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        acquireHighPerfWifiLock()
    }

    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun acquireHighPerfWifiLock() {
        val wifi = appContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        try {
            if (wifiHighPerfLock == null) {
                val lockMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiHighPerfLock = wifi.createWifiLock(lockMode, "Miora:HighSpeedTransferLock").apply {
                    setReferenceCounted(false)
                }
            }
            if (wifiHighPerfLock?.isHeld == false) {
                wifiHighPerfLock?.acquire()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseHighPerfWifiLock() {
        try {
            if (wifiHighPerfLock?.isHeld == true) {
                wifiHighPerfLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun onNetworkChanged() {
        if (currentListeningDeviceName.isNotBlank() && TransferController.isDiscoverable.value) {
            val btManager = appContext?.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = btManager?.adapter
            if (adapter?.isEnabled == true) {
                if (bluetoothServerJob?.isActive != true) {
                    startBluetoothListeningServer(currentListeningDeviceName)
                } else {
                    stopBleAdvertising()
                    startBleAdvertising(adapter, currentListeningDeviceName, currentBleL2capPsm)
                }
            }
        }
        val callback = activeDiscoveryCallback
        if (callback != null && TransferController.isScanning.value) {
            if (DeviceConnectivityManager.isBluetoothEnabled.value && !isBleScanning) {
                startBleDiscovery(callback)
            }
            if (DeviceConnectivityManager.isWifiEnabled.value && wifiP2pJob?.isActive != true) {
                startWifiP2pDiscovery(callback)
                startLanSubnetDiscovery(callback)
            }
        }
    }

    // -------------------------------------------------------------
    // Combined Discovery Engine (BLE + Wi-Fi Direct + UDP + NSD + LAN Scanner)
    // -------------------------------------------------------------

    fun startDiscovery(onPeerFound: (DiscoveredPeer) -> Unit) {
        activeDiscoveryCallback = onPeerFound
        acquireMulticastLock()
        startBleDiscovery(onPeerFound)
        startUdpLanDiscovery(onPeerFound)
        startNsdDiscovery(onPeerFound)
        startWifiP2pDiscovery(onPeerFound)
        startLanSubnetDiscovery(onPeerFound)
    }

    fun stopDiscovery() {
        activeDiscoveryCallback = null
        stopWifiP2pDiscovery()
        stopNsdDiscovery()
        stopUdpLanDiscovery()
        stopLanSubnetDiscovery()
        stopBleDiscovery()
        releaseMulticastLock()
    }

    private fun startWifiP2pDiscovery(onPeerFound: (DiscoveredPeer) -> Unit) {
        stopWifiP2pDiscovery()
        if (!DeviceConnectivityManager.isWifiEnabled.value) return

        DeviceConnectivityManager.discoverWifiDirectPeers()

        wifiP2pJob = engineScope.launch {
            DeviceConnectivityManager.wifiP2pPeers.collect { p2pDevices ->
                p2pDevices.forEach { device ->
                    val rawName = device.deviceName ?: ""
                    // Strictly accept only verified Miora Wi-Fi Direct peers
                    if (!rawName.startsWith("Miora-", ignoreCase = true) &&
                        !rawName.startsWith("DIRECT-MR-", ignoreCase = true)
                    ) {
                        return@forEach
                    }
                    val cleanName = rawName
                        .removePrefix("DIRECT-MR-")
                        .removePrefix("Miora-")
                        .removePrefix("miora-")
                        .replace("_", " ")
                    val peer = DiscoveredPeer(
                        id = "p2p_${device.deviceAddress}",
                        name = if (cleanName.isNotBlank()) cleanName else "Miora Device",
                        deviceAddress = device.deviceAddress,
                        hostAddress = "192.168.49.1",
                        port = MIORA_PORT,
                        protocol = TransferProtocol.WIFI_DIRECT,
                        signalStrength = 95,
                        isMioraDevice = true
                    )
                    onPeerFound(peer)
                }
            }
        }
    }

    private fun stopWifiP2pDiscovery() {
        wifiP2pJob?.cancel()
        wifiP2pJob = null
        DeviceConnectivityManager.stopWifiDirectPeerDiscovery()
    }

    private fun startLanSubnetDiscovery(onPeerFound: (DiscoveredPeer) -> Unit) {
        stopLanSubnetDiscovery()
        val localIp = DeviceConnectivityManager.getLocalIpAddress() ?: return
        if (!DeviceConnectivityManager.isWifiEnabled.value) return

        val prefix = localIp.substringBeforeLast(".")
        val myLastOctet = localIp.substringAfterLast(".").toIntOrNull() ?: -1

        lanScanJob = engineScope.launch {
            val octets = (1..254).filter { it != myLastOctet }
            for (chunk in octets.chunked(64)) {
                if (!isActive) break
                chunk.map { octet ->
                    async(Dispatchers.IO) {
                        val targetIp = "$prefix.$octet"
                        try {
                            Socket().use { testSocket ->
                                testSocket.connect(InetSocketAddress(targetIp, MIORA_PORT), 250)
                                val dos = DataOutputStream(BufferedOutputStream(testSocket.getOutputStream(), 1024))
                                val dis = DataInputStream(BufferedInputStream(testSocket.getInputStream(), 1024))
                                dos.writeUTF("MIORA_PROBE")
                                dos.flush()
                                val ack = dis.readUTF()
                                if (ack == "MIORA_PROBE_ACK") {
                                    val peerName = dis.readUTF()
                                    val peer = DiscoveredPeer(
                                        id = "lan_${targetIp}_$MIORA_PORT",
                                        name = peerName.removePrefix("Miora-").replace("_", " "),
                                        deviceAddress = targetIp,
                                        hostAddress = targetIp,
                                        port = MIORA_PORT,
                                        protocol = TransferProtocol.WIFI_DIRECT,
                                        signalStrength = 100,
                                        isMioraDevice = true
                                    )
                                    onPeerFound(peer)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }.awaitAll()
            }
        }
    }

    private fun stopLanSubnetDiscovery() {
        lanScanJob?.cancel()
        lanScanJob = null
    }

    private fun startUdpLanDiscovery(onPeerFound: (DiscoveredPeer) -> Unit) {
        stopUdpLanDiscovery()
        udpDiscoveryJob = engineScope.launch {
            try {
                val socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(MIORA_UDP_BEACON_PORT))
                    broadcast = true
                }
                udpSocket = socket
                val buffer = ByteArray(1024)

                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: Exception) {
                        break
                    }

                    val message = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    if (message.startsWith("MIORA_BEACON:")) {
                        val parts = message.split(":")
                        if (parts.size >= 4) {
                            val peerName = parts[1]
                            val peerIp = parts[2]
                            val peerPort = parts[3].toIntOrNull() ?: MIORA_PORT
                            val myIp = DeviceConnectivityManager.getLocalIpAddress()

                            if (peerIp != myIp && peerIp != "127.0.0.1") {
                                val peer = DiscoveredPeer(
                                    id = "udp_${peerIp}_$peerPort",
                                    name = peerName.removePrefix("Miora-").replace("_", " "),
                                    deviceAddress = peerIp,
                                    hostAddress = peerIp,
                                    port = peerPort,
                                    protocol = TransferProtocol.WIFI_DIRECT,
                                    signalStrength = 98,
                                    isMioraDevice = true
                                )
                                onPeerFound(peer)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopUdpLanDiscovery() {
        udpDiscoveryJob?.cancel()
        udpDiscoveryJob = null
        try {
            udpSocket?.close()
        } catch (_: Exception) {}
        udpSocket = null
    }

    private fun startNsdDiscovery(onPeerFound: (DiscoveredPeer) -> Unit) {
        val nsd = nsdManager ?: return
        if (isNsdDiscovering) return

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                isNsdDiscovering = true
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                val sType = service.serviceType ?: ""
                val sName = service.serviceName ?: ""
                if (sType.contains("miora", ignoreCase = true) || sName.startsWith("Miora-", ignoreCase = true)) {
                    queueNsdResolve(service, onPeerFound)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {}

            override fun onDiscoveryStopped(serviceType: String) {
                isNsdDiscovering = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                isNsdDiscovering = false
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                isNsdDiscovering = false
            }
        }

        try {
            nsd.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun queueNsdResolve(service: NsdServiceInfo, onPeerFound: (DiscoveredPeer) -> Unit) {
        resolveQueue.offer(service)
        processNextNsdResolve(onPeerFound)
    }

    private fun processNextNsdResolve(onPeerFound: (DiscoveredPeer) -> Unit) {
        val nsd = nsdManager ?: return
        if (!isResolving.compareAndSet(false, true)) return

        val nextService = resolveQueue.poll()
        if (nextService == null) {
            isResolving.set(false)
            return
        }

        try {
            nsd.resolveService(nextService, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    isResolving.set(false)
                    processNextNsdResolve(onPeerFound)
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    try {
                        val host = serviceInfo.host?.hostAddress
                        if (host != null) {
                            val myIp = DeviceConnectivityManager.getLocalIpAddress()
                            if (host != myIp && host != "127.0.0.1") {
                                val rawName = serviceInfo.serviceName ?: "Miora Device"
                                val cleanName = rawName.removePrefix("Miora-").replace("_", " ")

                                val peer = DiscoveredPeer(
                                    id = "nsd_${host}_${serviceInfo.port}",
                                    name = cleanName,
                                    deviceAddress = host,
                                    hostAddress = host,
                                    port = serviceInfo.port,
                                    protocol = TransferProtocol.WIFI_DIRECT,
                                    signalStrength = 95,
                                    isMioraDevice = true
                                )
                                onPeerFound(peer)
                            }
                        }
                    } finally {
                        isResolving.set(false)
                        processNextNsdResolve(onPeerFound)
                    }
                }
            })
        } catch (e: Exception) {
            isResolving.set(false)
            processNextNsdResolve(onPeerFound)
        }
    }

    private fun stopNsdDiscovery() {
        val nsd = nsdManager
        if (isNsdDiscovering && nsd != null) {
            discoveryListener?.let {
                try {
                    nsd.stopServiceDiscovery(it)
                } catch (_: Exception) {}
            }
            isNsdDiscovering = false
            discoveryListener = null
        }
        resolveQueue.clear()
        isResolving.set(false)
    }

    private fun startBleDiscovery(onPeerFound: (DiscoveredPeer) -> Unit) {
        val ctx = appContext ?: return
        if (!DeviceConnectivityManager.isBluetoothEnabled.value) return
        if (isBleScanning) return

        try {
            val btManager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
            val adapter = btManager.adapter ?: return
            if (!adapter.isEnabled) return

            bleScanner = adapter.bluetoothLeScanner ?: return

            val settingsBuilder = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                settingsBuilder.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                settingsBuilder.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            }
            val settings = settingsBuilder.build()

            bleScanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    val device = result?.device ?: return
                    val scanRecord = result.scanRecord ?: return

                    val sData = scanRecord.getServiceData(MIORA_BLE_SERVICE_UUID)
                    val hasMioraUuid = scanRecord.serviceUuids?.contains(MIORA_BLE_SERVICE_UUID) == true
                    if (sData == null && !hasMioraUuid) {
                        return
                    }

                    var discoveredIp: String? = null
                    var discoveredPort = MIORA_PORT
                    var discoveredPsm = 0
                    var customBleName: String? = null

                    if (sData != null && sData.size >= 6) {
                        val ip0 = sData[0].toInt() and 0xFF
                        val ip1 = sData[1].toInt() and 0xFF
                        val ip2 = sData[2].toInt() and 0xFF
                        val ip3 = sData[3].toInt() and 0xFF
                        if (ip0 != 0 || ip1 != 0 || ip2 != 0 || ip3 != 0) {
                            discoveredIp = "$ip0.$ip1.$ip2.$ip3"
                        }
                        discoveredPort = ((sData[4].toInt() and 0xFF) shl 8) or (sData[5].toInt() and 0xFF)
                        if (sData.size >= 8) {
                            discoveredPsm = ((sData[6].toInt() and 0xFF) shl 8) or (sData[7].toInt() and 0xFF)
                        }
                        if (sData.size > 8) {
                            customBleName = try {
                                String(sData, 8, sData.size - 8, Charsets.UTF_8).trim()
                            } catch (_: Exception) { null }
                        }
                    }

                    var deviceName = customBleName
                    if (deviceName.isNullOrEmpty()) {
                        deviceName = scanRecord.deviceName
                    }
                    if (deviceName.isNullOrEmpty()) {
                        deviceName = try { device.name } catch (_: Exception) { null }
                    }
                    val finalName = (deviceName ?: "Miora Device")
                        .removePrefix("Miora-")
                        .removePrefix("miora-")
                        .replace("_", " ")

                    val myIp = DeviceConnectivityManager.getLocalIpAddress()
                    if (discoveredIp != null && discoveredIp == myIp) return

                    val peer = DiscoveredPeer(
                        id = "ble_${device.address}",
                        name = finalName,
                        deviceAddress = device.address,
                        hostAddress = discoveredIp,
                        port = if (discoveredPort > 0) discoveredPort else MIORA_PORT,
                        blePsm = discoveredPsm,
                        protocol = TransferProtocol.BLUETOOTH,
                        signalStrength = ((result.rssi + 100) * 2).coerceIn(15, 100),
                        isMioraDevice = true
                    )
                    onPeerFound(peer)
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                    results?.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
                }

                override fun onScanFailed(errorCode: Int) {
                    isBleScanning = false
                }
            }

            try {
                // Foreground unfiltered scan with software UUID check avoids OEM hardware filter bugs and finds Miora peers in <300ms
                bleScanner?.startScan(null, settings, bleScanCallback)
                isBleScanning = true
            } catch (_: Exception) {
                val filter = ScanFilter.Builder()
                    .setServiceUuid(MIORA_BLE_SERVICE_UUID)
                    .build()
                bleScanner?.startScan(listOf(filter), settings, bleScanCallback)
                isBleScanning = true
            }

            // Probe paired Bluetooth devices in the background to ONLY include devices actively running Miora
            bondedProbeJob?.cancel()
            val bondedList = adapter.bondedDevices?.toList() ?: emptyList()
            if (bondedList.isNotEmpty()) {
                bondedProbeJob = engineScope.launch {
                    bondedList.map { bonded ->
                        async(Dispatchers.IO) {
                            if (!isActive) return@async
                            try {
                                val probeSocket = try {
                                    bonded.createInsecureRfcommSocketToServiceRecord(MIORA_RFCOMM_UUID)
                                } catch (_: Exception) {
                                    bonded.createRfcommSocketToServiceRecord(MIORA_RFCOMM_UUID)
                                }
                                probeSocket.use { btSock ->
                                    btSock.connect()
                                    val dos = DataOutputStream(BufferedOutputStream(btSock.outputStream, 512))
                                    val dis = DataInputStream(BufferedInputStream(btSock.inputStream, 512))
                                    dos.writeUTF("MIORA_PROBE")
                                    dos.flush()
                                    val ack = dis.readUTF()
                                    if (ack == "MIORA_PROBE_ACK") {
                                        val mioraName = dis.readUTF()
                                        val peer = DiscoveredPeer(
                                            id = "bonded_${bonded.address}",
                                            name = mioraName.removePrefix("Miora-").replace("_", " "),
                                            deviceAddress = bonded.address,
                                            protocol = TransferProtocol.BLUETOOTH,
                                            signalStrength = 90,
                                            isMioraDevice = true
                                        )
                                        onPeerFound(peer)
                                    }
                                }
                            } catch (_: Exception) {
                                // Not an active Miora receiver — strictly ignore
                            }
                        }
                    }.awaitAll()
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            isBleScanning = false
        }
    }

    private fun stopBleDiscovery() {
        bondedProbeJob?.cancel()
        bondedProbeJob = null
        if (isBleScanning && bleScanner != null && bleScanCallback != null) {
            try {
                bleScanner?.stopScan(bleScanCallback)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isBleScanning = false
            bleScanCallback = null
        }
    }

    // -------------------------------------------------------------
    // Unified Receiver Server (Wi-Fi TCP + Wi-Fi Direct + UDP Beacon + BLE Advert + Bluetooth RFCOMM & L2CAP)
    // -------------------------------------------------------------

    fun startListeningServer(deviceName: String) {
        stopListeningServer()
        currentListeningDeviceName = deviceName
        acquireMulticastLock()

        registerService(deviceName)

        serverJob = engineScope.launch {
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    try { receiveBufferSize = BUFFER_SIZE } catch (_: Exception) {}
                    bind(InetSocketAddress(MIORA_PORT))
                }
                while (isActive) {
                    val socket = try {
                        serverSocket?.accept() ?: break
                    } catch (_: Exception) {
                        break
                    }
                    try {
                        socket.tcpNoDelay = true
                        socket.receiveBufferSize = BUFFER_SIZE
                        socket.sendBufferSize = BUFFER_SIZE
                        socket.keepAlive = true
                    } catch (_: Exception) {}
                    activeServerSocketConnection = socket
                    launch(Dispatchers.IO) {
                        handleIncomingStream(
                            inputStream = socket.getInputStream(),
                            outputStream = socket.getOutputStream(),
                            protocol = TransferProtocol.WIFI_DIRECT,
                            closeConnection = {
                                try { socket.close() } catch (_: Exception) {}
                                if (activeServerSocketConnection === socket) {
                                    activeServerSocketConnection = null
                                }
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        startUdpBeaconBroadcaster(deviceName)
        startBluetoothListeningServer(deviceName)
    }

    private fun startUdpBeaconBroadcaster(deviceName: String) {
        udpBeaconJob = engineScope.launch {
            var broadcastSocket: DatagramSocket? = null
            try {
                broadcastSocket = DatagramSocket().apply {
                    broadcast = true
                }
                val cleanName = deviceName.replace(" ", "_").take(24)

                while (isActive) {
                    val myIp = DeviceConnectivityManager.getLocalIpAddress()
                    if (myIp != null && DeviceConnectivityManager.isWifiEnabled.value) {
                        val message = "MIORA_BEACON:$cleanName:$myIp:$MIORA_PORT"
                        val data = message.toByteArray(Charsets.UTF_8)

                        try {
                            val packetGlobal = DatagramPacket(
                                data,
                                data.size,
                                InetAddress.getByName("255.255.255.255"),
                                MIORA_UDP_BEACON_PORT
                            )
                            broadcastSocket.send(packetGlobal)
                        } catch (_: Exception) {}

                        try {
                            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
                            for (intf in interfaces) {
                                if (intf.isLoopback || !intf.isUp) continue
                                for (intfAddr in intf.interfaceAddresses) {
                                    val bcast = intfAddr.broadcast
                                    if (bcast != null) {
                                        val packetSubnet = DatagramPacket(data, data.size, bcast, MIORA_UDP_BEACON_PORT)
                                        broadcastSocket.send(packetSubnet)
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    delay(600)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try { broadcastSocket?.close() } catch (_: Exception) {}
            }
        }
    }

    private fun registerService(deviceName: String) {
        val nsd = nsdManager ?: return
        if (isNsdRegistered) return

        val cleanName = deviceName.replace(" ", "_").take(24)
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "Miora-$cleanName"
            serviceType = NSD_SERVICE_TYPE
            port = MIORA_PORT
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                isNsdRegistered = true
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                isNsdRegistered = false
            }
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                isNsdRegistered = false
            }
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                isNsdRegistered = false
            }
        }

        try {
            nsd.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun unregisterService() {
        val nsd = nsdManager ?: return
        if (!isNsdRegistered) return
        registrationListener?.let {
            try {
                nsd.unregisterService(it)
            } catch (_: Exception) {}
        }
        isNsdRegistered = false
        registrationListener = null
    }

    private fun startBluetoothListeningServer(deviceName: String) {
        val ctx = appContext ?: return
        try {
            val btManager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
            val adapter = btManager.adapter ?: return
            if (!adapter.isEnabled) return

            // 1. Start BLE L2CAP Connection-Oriented Channel Server (Android 10+) for direct unbonded BLE transfers
            currentBleL2capPsm = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val l2capServer = adapter.listenUsingInsecureL2capChannel()
                    bleL2capServerSocket = l2capServer
                    currentBleL2capPsm = l2capServer.psm
                    bleL2capServerJob = engineScope.launch {
                        try {
                            while (isActive) {
                                val btSocket = try {
                                    bleL2capServerSocket?.accept() ?: break
                                } catch (_: Exception) {
                                    break
                                }
                                activeBluetoothSocketConnection = btSocket
                                launch(Dispatchers.IO) {
                                    handleIncomingStream(
                                        inputStream = btSocket.inputStream,
                                        outputStream = btSocket.outputStream,
                                        protocol = TransferProtocol.BLUETOOTH,
                                        closeConnection = {
                                            try { btSocket.close() } catch (_: Exception) {}
                                            activeBluetoothSocketConnection = null
                                        }
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 2. Start BLE Advertising with compact ServiceData (IP + Port + L2CAP PSM + Miora Name)
            startBleAdvertising(adapter, deviceName, currentBleL2capPsm)

            // 3. Start Classic Bluetooth RFCOMM Server
            bluetoothServerJob = engineScope.launch {
                try {
                    bluetoothServerSocket = try {
                        adapter.listenUsingInsecureRfcommWithServiceRecord("MioraTransfer", MIORA_RFCOMM_UUID)
                    } catch (_: Exception) {
                        adapter.listenUsingRfcommWithServiceRecord("MioraTransfer", MIORA_RFCOMM_UUID)
                    }

                    while (isActive) {
                        val btSocket = try {
                            bluetoothServerSocket?.accept() ?: break
                        } catch (_: Exception) {
                            break
                        }
                        activeBluetoothSocketConnection = btSocket
                        launch(Dispatchers.IO) {
                            handleIncomingStream(
                                inputStream = btSocket.inputStream,
                                outputStream = btSocket.outputStream,
                                protocol = TransferProtocol.BLUETOOTH,
                                closeConnection = {
                                    try { btSocket.close() } catch (_: Exception) {}
                                    activeBluetoothSocketConnection = null
                                }
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun truncateUtf8Bytes(input: String, maxBytes: Int): ByteArray {
        val raw = input.toByteArray(Charsets.UTF_8)
        if (raw.size <= maxBytes) return raw
        var result = ByteArray(0)
        val sb = StringBuilder()
        for (ch in input) {
            sb.append(ch)
            val candidate = sb.toString().toByteArray(Charsets.UTF_8)
            if (candidate.size > maxBytes) break
            result = candidate
        }
        return result
    }

    private fun startBleAdvertising(adapter: BluetoothAdapter, deviceName: String, l2capPsm: Int = 0) {
        try {
            bleAdvertiser = adapter.bluetoothLeAdvertiser ?: return
            val shortName = deviceName
                .removePrefix("Miora-")
                .removePrefix("miora-")
                .trim()
                .ifEmpty { "Miora" }
            val nameBytes = truncateUtf8Bytes(shortName, 14)

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build()

            val ip = DeviceConnectivityManager.getLocalIpAddress()
            val ipParts = try {
                ip?.split(".")?.map { it.toInt().toByte() }?.takeIf { it.size == 4 }
            } catch (_: Exception) { null }

            // Compact 8-byte header + up to 14-byte name = max 22 bytes (fits comfortably inside 31-byte BLE limit)
            val serviceData = ByteArray(8 + nameBytes.size)
            if (ipParts != null) {
                serviceData[0] = ipParts[0]
                serviceData[1] = ipParts[1]
                serviceData[2] = ipParts[2]
                serviceData[3] = ipParts[3]
            }
            serviceData[4] = (MIORA_PORT shr 8).toByte()
            serviceData[5] = (MIORA_PORT and 0xFF).toByte()
            serviceData[6] = (l2capPsm shr 8).toByte()
            serviceData[7] = (l2capPsm and 0xFF).toByte()
            System.arraycopy(nameBytes, 0, serviceData, 8, nameBytes.size)

            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addServiceData(MIORA_BLE_SERVICE_UUID, serviceData)
                .build()

            val scanResponse = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(MIORA_BLE_SERVICE_UUID)
                .build()

            bleAdvertiseCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    isBleAdvertising = true
                }
                override fun onStartFailure(errorCode: Int) {
                    isBleAdvertising = false
                }
            }

            bleAdvertiser?.startAdvertising(settings, data, scanResponse, bleAdvertiseCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopBleAdvertising() {
        if (isBleAdvertising && bleAdvertiser != null && bleAdvertiseCallback != null) {
            try {
                bleAdvertiser?.stopAdvertising(bleAdvertiseCallback)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isBleAdvertising = false
            bleAdvertiseCallback = null
        }
    }

    fun stopListeningServer() {
        currentListeningDeviceName = ""
        unregisterService()
        releaseMulticastLock()
        releaseHighPerfWifiLock()
        stopBleAdvertising()

        udpBeaconJob?.cancel()
        udpBeaconJob = null

        serverJob?.cancel()
        serverJob = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        try {
            activeServerSocketConnection?.close()
        } catch (_: Exception) {}
        activeServerSocketConnection = null

        bleL2capServerJob?.cancel()
        bleL2capServerJob = null
        try {
            bleL2capServerSocket?.close()
        } catch (_: Exception) {}
        bleL2capServerSocket = null
        currentBleL2capPsm = 0

        bluetoothServerJob?.cancel()
        bluetoothServerJob = null
        try {
            bluetoothServerSocket?.close()
        } catch (_: Exception) {}
        bluetoothServerSocket = null
        try {
            activeBluetoothSocketConnection?.close()
        } catch (_: Exception) {}
        activeBluetoothSocketConnection = null

        DeviceConnectivityManager.disconnectReceiverFromHotspot()
    }

    // -------------------------------------------------------------
    // Incoming Protocol Stream Handler (Unified Wi-Fi & Bluetooth)
    // -------------------------------------------------------------

    private suspend fun handleIncomingStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        protocol: TransferProtocol,
        closeConnection: () -> Unit
    ) = withContext(Dispatchers.IO) {
        var isTransferCompleted = false
        acquireHighPerfWifiLock()
        try {
            val dis = DataInputStream(BufferedInputStream(inputStream, BUFFER_SIZE))
            val dos = DataOutputStream(BufferedOutputStream(outputStream, BUFFER_SIZE))

            val magic = dis.readUTF()
            if (magic == "MIORA_PROBE") {
                val myName = TransferController.deviceName.value
                dos.writeUTF("MIORA_PROBE_ACK")
                dos.writeUTF(myName)
                dos.flush()
                closeConnection()
                return@withContext
            }
            if (magic == "MIORA_QUERY_IP") {
                val myIp = DeviceConnectivityManager.getLocalIpAddress()
                if (myIp != null) {
                    dos.writeUTF("MIORA_IP:$myIp:$MIORA_PORT")
                } else {
                    dos.writeUTF("MIORA_NO_IP")
                }
                dos.flush()
                closeConnection()
                return@withContext
            }

            if (magic != PROTOCOL_MAGIC) {
                closeConnection()
                return@withContext
            }

            val senderName = dis.readUTF()
            val fileCount = dis.readInt()
            val totalSize = dis.readLong()
            val firstFileName = dis.readUTF()

            val fileNames = mutableListOf<String>()
            val fileSizes = mutableListOf<Long>()
            for (i in 0 until fileCount) {
                fileNames.add(dis.readUTF())
                fileSizes.add(dis.readLong())
            }

            val incoming = IncomingRequest(
                senderName = senderName,
                fileCount = fileCount,
                totalSize = totalSize,
                firstFileName = firstFileName,
                protocol = protocol,
                fileNames = fileNames,
                fileSizes = fileSizes
            )

            acceptanceDeferred = CompletableDeferred()
            TransferController.postIncomingRequest(incoming)

            val accepted = acceptanceDeferred?.await() ?: false
            if (!accepted) {
                dos.writeUTF("DECLINED")
                dos.flush()
                closeConnection()
                return@withContext
            }

            dos.writeUTF("ACCEPT")
            dos.flush()

            val targetDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Miora"
            ).apply { if (!exists()) mkdirs() }

            val initialRecord = TransferRecord(
                id = UUID.randomUUID().toString(),
                fileName = if (fileCount == 1) firstFileName else "$firstFileName +${fileCount - 1}",
                fileSize = totalSize,
                direction = TransferDirection.RECEIVE,
                protocol = protocol,
                status = TransferStatus.TRANSFERRING,
                progress = 0f,
                speedMbps = 0f
            )
            TransferController.updateActiveReceive(initialRecord)

            var totalBytesReceived = 0L
            val startTime = System.currentTimeMillis()
            var lastUiUpdateMs = 0L
            val buffer = ByteArray(BUFFER_SIZE)
            val receivedPaths = mutableListOf<String>()

            for (i in 0 until fileCount) {
                val rawName = dis.readUTF()
                val safeName = rawName.substringAfterLast('/').substringAfterLast('\\').ifBlank { "File_${i + 1}" }
                val size = dis.readLong().coerceAtLeast(0L)
                val tempFile = File(targetDir, "$safeName.tmp_${UUID.randomUUID().toString().take(6)}")
                val finalFile = resolveUniqueTargetFile(targetDir, safeName)
                currentTempFile = tempFile

                BufferedOutputStream(FileOutputStream(tempFile), BUFFER_SIZE).use { fos ->
                    var fileBytesRead = 0L
                    while (fileBytesRead < size) {
                        val toRead = ((size - fileBytesRead).coerceAtMost(buffer.size.toLong())).toInt()
                        val bytesRead = dis.read(buffer, 0, toRead)
                        if (bytesRead == -1) break
                        fos.write(buffer, 0, bytesRead)
                        fileBytesRead += bytesRead
                        totalBytesReceived += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastUiUpdateMs >= 80L || fileBytesRead == size) {
                            lastUiUpdateMs = now
                            val elapsedSeconds = (now - startTime) / 1000f
                            val speed = if (elapsedSeconds > 0f) (totalBytesReceived / (1024f * 1024f)) / elapsedSeconds else 0f
                            val progress = if (totalSize > 0L) (totalBytesReceived.toFloat() / totalSize).coerceIn(0f, 1f) else 1f

                            TransferController.updateActiveReceive(
                                initialRecord.copy(
                                    progress = progress,
                                    speedMbps = speed
                                )
                            )
                        }
                    }
                    fos.flush()
                }

                if (tempFile.exists() && tempFile.length() == size) {
                    if (finalFile.exists()) finalFile.delete()
                    val renamed = tempFile.renameTo(finalFile)
                    if (!renamed) {
                        tempFile.copyTo(finalFile, overwrite = true)
                        tempFile.delete()
                    }
                    currentTempFile = null
                    receivedPaths.add(finalFile.absolutePath)
                } else {
                    tempFile.delete()
                    throw IOException("Incomplete file received: $safeName")
                }
            }

            if (receivedPaths.isNotEmpty() && appContext != null) {
                try {
                    android.media.MediaScannerConnection.scanFile(
                        appContext,
                        receivedPaths.toTypedArray(),
                        null,
                        null
                    )
                } catch (_: Exception) {}
            }

            dos.writeUTF("SUCCESS")
            dos.flush()
            isTransferCompleted = true

            val totalElapsedSec = ((System.currentTimeMillis() - startTime) / 1000f).coerceAtLeast(0.05f)
            val finalRecord = initialRecord.copy(
                status = TransferStatus.COMPLETED,
                progress = 1f,
                speedMbps = (totalBytesReceived / (1024f * 1024f)) / totalElapsedSec
            )
            TransferController.completeReceive(finalRecord)

        } catch (e: Exception) {
            e.printStackTrace()
            currentTempFile?.let {
                try { if (it.exists()) it.delete() } catch (_: Exception) {}
            }
            if (!isTransferCompleted) {
                TransferController.cancelReceiveTransfer()
            }
        } finally {
            closeConnection()
            acceptanceDeferred = null
            currentTempFile = null
        }
    }

    private fun resolveUniqueTargetFile(targetDir: File, fileName: String): File {
        val candidate = File(targetDir, fileName)
        if (!candidate.exists()) return candidate
        val dotIndex = fileName.lastIndexOf('.')
        val base = if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
        val ext = if (dotIndex > 0) fileName.substring(dotIndex) else ""
        for (counter in 1..999) {
            val numbered = File(targetDir, "${base}_($counter)$ext")
            if (!numbered.exists()) return numbered
        }
        return File(targetDir, "${base}_${System.currentTimeMillis()}$ext")
    }

    fun acceptIncoming() {
        acceptanceDeferred?.complete(true)
    }

    fun declineIncoming() {
        acceptanceDeferred?.complete(false)
    }

    // -------------------------------------------------------------
    // Sender Client (QR Code Wi-Fi Direct, Bluetooth Discovery & Fallback)
    // -------------------------------------------------------------

    /**
     * Connects directly to Receiver via QR Code information (High-Speed Wi-Fi).
     * Supports instant direct LAN transfer if already on the same Wi-Fi, and automatic Hotspot connection if not.
     */
    fun startSendToQrHost(
        ssid: String,
        passphrase: String,
        hostIp: String,
        hostIps: List<String> = emptyList(),
        port: Int,
        items: List<MediaItem>,
        senderDeviceName: String,
        onComplete: (Boolean) -> Unit
    ) {
        clientJob?.cancel()
        clientJob = engineScope.launch {
            acquireHighPerfWifiLock()
            val ctx = appContext
            val resolvedItems = if (ctx != null) {
                items.map { item ->
                    val exactSize = resolveExactFileSize(ctx, item)
                    if (exactSize > 0L && exactSize != item.size) item.copy(size = exactSize) else item
                }
            } else {
                items
            }

            val totalBytes = resolvedItems.sumOf { it.size }
            val displayName = if (resolvedItems.size == 1) resolvedItems.first().name else "${resolvedItems.first().name} +${resolvedItems.size - 1}"

            val initialRecord = TransferRecord(
                id = UUID.randomUUID().toString(),
                fileName = displayName,
                fileSize = totalBytes,
                direction = TransferDirection.SEND,
                protocol = TransferProtocol.WIFI_DIRECT,
                status = TransferStatus.CONNECTING,
                progress = 0f,
                speedMbps = 0f
            )
            TransferController.updateActiveSend(initialRecord)

            val targetPort = if (port > 0) port else MIORA_PORT
            val allCandidateIps = (hostIps + hostIp)
                .filter { it.isNotBlank() && it != "127.0.0.1" && it != "0.0.0.0" }
                .distinct()

            var socket: Socket? = null
            var isSuccess = false
            var connectedViaHotspot = false

            // -------------------------------------------------------------
            // Phase 1: Fast Parallel Probe on Existing Wi-Fi / Local Network
            // Only probe IPs that could be reachable before switching Wi-Fi networks
            // -------------------------------------------------------------
            val myCurrentIps = DeviceConnectivityManager.getAllLocalIpAddresses()
            val myPrefixes = myCurrentIps.map { it.substringBeforeLast(".") }.toSet()

            val phase1Ips = if (ssid.isNotBlank()) {
                // If QR has a dedicated Wi-Fi Direct SSID, only probe IPs matching our current active subnet(s) first
                allCandidateIps.filter { candidate ->
                    myPrefixes.contains(candidate.substringBeforeLast("."))
                }
            } else {
                allCandidateIps
            }

            if (phase1Ips.isNotEmpty()) {
                val connectedSockets = ConcurrentLinkedQueue<Socket>()
                coroutineScope {
                    phase1Ips.map { candidateIp ->
                        async(Dispatchers.IO) {
                            try {
                                val probeSocket = Socket()
                                probeSocket.tcpNoDelay = true
                                probeSocket.sendBufferSize = BUFFER_SIZE
                                probeSocket.receiveBufferSize = BUFFER_SIZE
                                probeSocket.keepAlive = true
                                probeSocket.connect(InetSocketAddress(candidateIp, targetPort), 650)
                                connectedSockets.offer(probeSocket)
                            } catch (_: Exception) {}
                        }
                    }.awaitAll()
                }
                socket = connectedSockets.poll()
                while (connectedSockets.isNotEmpty()) {
                    try { connectedSockets.poll()?.close() } catch (_: Exception) {}
                }
                if (socket != null) {
                    activeClientTcpSocket = socket
                }
            }

            // -------------------------------------------------------------
            // Phase 2: Connect via Receiver's Direct Wi-Fi AP / Hotspot
            // -------------------------------------------------------------
            if (socket == null && ssid.isNotBlank()) {
                val connectDeferred = CompletableDeferred<Pair<android.net.Network?, String?>?>()
                DeviceConnectivityManager.connectReceiverToHotspot(
                    ssid = ssid,
                    passphrase = passphrase,
                    onConnected = { network, gatewayIp ->
                        connectDeferred.complete(Pair(network, gatewayIp))
                    },
                    onFailed = {
                        connectDeferred.complete(null)
                    }
                )

                val hotspotResult = withTimeoutOrNull(22000) { connectDeferred.await() }
                if (hotspotResult == null) {
                    TransferController.cancelSendTransfer()
                    releaseHighPerfWifiLock()
                    withContext(Dispatchers.Main) { onComplete(false) }
                    return@launch
                }

                connectedViaHotspot = true
                val boundNetwork = hotspotResult.first
                val initialGatewayIp = hotspotResult.second

                // Retry loop to allow Android DHCP / ARP routing to settle after joining Wi-Fi Direct AP
                for (attempt in 1..6) {
                    if (!isActive || socket != null) break

                    val dynamicGateway = if (ctx != null) {
                        DeviceConnectivityManager.getGatewayIp(ctx, boundNetwork)
                    } else null

                    val hotspotIps = mutableListOf<String>()
                    if (!dynamicGateway.isNullOrBlank()) hotspotIps.add(dynamicGateway)
                    if (!initialGatewayIp.isNullOrBlank()) hotspotIps.add(initialGatewayIp)
                    hotspotIps.add("192.168.49.1")
                    hotspotIps.add("192.168.43.1")
                    hotspotIps.addAll(allCandidateIps)
                    hotspotIps.addAll(listOf("192.168.50.1", "192.168.137.1"))
                    val distinctHotspotIps = hotspotIps.distinct()

                    for (targetIp in distinctHotspotIps) {
                        try {
                            val testSocket = if (boundNetwork != null) {
                                boundNetwork.socketFactory.createSocket()
                            } else {
                                Socket()
                            }
                            testSocket.tcpNoDelay = true
                            testSocket.sendBufferSize = BUFFER_SIZE
                            testSocket.receiveBufferSize = BUFFER_SIZE
                            testSocket.keepAlive = true
                            testSocket.connect(InetSocketAddress(targetIp, targetPort), 1800)

                            socket = testSocket
                            activeClientTcpSocket = socket
                            break
                        } catch (_: Exception) {}
                    }

                    if (socket == null && attempt < 6) {
                        delay(350)
                    }
                }
            }

            // Final fallback: if ssid was blank or hotspot didn't yield a socket yet, try any remaining candidate IPs
            if (socket == null) {
                for (candidateIp in allCandidateIps) {
                    try {
                        val fallbackSocket = Socket()
                        fallbackSocket.tcpNoDelay = true
                        fallbackSocket.sendBufferSize = BUFFER_SIZE
                        fallbackSocket.receiveBufferSize = BUFFER_SIZE
                        fallbackSocket.keepAlive = true
                        fallbackSocket.connect(InetSocketAddress(candidateIp, targetPort), 1500)
                        socket = fallbackSocket
                        activeClientTcpSocket = socket
                        break
                    } catch (_: Exception) {}
                }
            }

            if (socket == null) {
                TransferController.cancelSendTransfer()
                if (connectedViaHotspot) {
                    DeviceConnectivityManager.disconnectReceiverFromHotspot()
                }
                releaseHighPerfWifiLock()
                withContext(Dispatchers.Main) { onComplete(false) }
                return@launch
            }

            // -------------------------------------------------------------
            // Phase 3: Perform High-Speed File Streaming
            // -------------------------------------------------------------
            try {
                isSuccess = performSendStream(
                    inputStream = socket.getInputStream(),
                    outputStream = socket.getOutputStream(),
                    items = resolvedItems,
                    totalBytes = totalBytes,
                    senderDeviceName = senderDeviceName,
                    initialRecord = initialRecord
                )
            } catch (e: Exception) {
                e.printStackTrace()
                TransferController.cancelSendTransfer()
                isSuccess = false
            } finally {
                try { socket.close() } catch (_: Exception) {}
                activeClientTcpSocket = null
                if (connectedViaHotspot) {
                    DeviceConnectivityManager.disconnectReceiverFromHotspot()
                }
                if (!TransferController.isDiscoverable.value) {
                    releaseHighPerfWifiLock()
                }
            }

            withContext(Dispatchers.Main) {
                onComplete(isSuccess)
            }
        }
    }

    fun startSend(
        peer: DiscoveredPeer,
        items: List<MediaItem>,
        protocolPreference: TransferProtocol,
        senderDeviceName: String,
        onComplete: (Boolean) -> Unit
    ) {
        clientJob?.cancel()
        clientJob = engineScope.launch {
            acquireHighPerfWifiLock()
            val ctx = appContext
            val resolvedItems = if (ctx != null) {
                items.map { item ->
                    val exactSize = resolveExactFileSize(ctx, item)
                    if (exactSize > 0L && exactSize != item.size) item.copy(size = exactSize) else item
                }
            } else {
                items
            }

            val totalBytes = resolvedItems.sumOf { it.size }
            val displayName = if (resolvedItems.size == 1) resolvedItems.first().name else "${resolvedItems.first().name} +${resolvedItems.size - 1}"

            val initialRecord = TransferRecord(
                id = UUID.randomUUID().toString(),
                fileName = displayName,
                fileSize = totalBytes,
                direction = TransferDirection.SEND,
                protocol = protocolPreference,
                status = TransferStatus.CONNECTING,
                progress = 0f,
                speedMbps = 0f
            )
            TransferController.updateActiveSend(initialRecord)

            var isSuccess = false

            when (protocolPreference) {
                TransferProtocol.WIFI_DIRECT -> {
                    // Try LAN/Wi-Fi TCP Socket if IP is already known
                    if (peer.hostAddress != null) {
                        isSuccess = sendViaWifi(
                            peer = peer,
                            items = resolvedItems,
                            totalBytes = totalBytes,
                            senderDeviceName = senderDeviceName,
                            initialRecord = initialRecord.copy(protocol = TransferProtocol.WIFI_DIRECT)
                        )
                    }
                    // Fallback to Bluetooth if Wi-Fi IP is unreachable
                    if (!isSuccess && peer.deviceAddress.contains(":")) {
                        isSuccess = sendViaBluetooth(
                            peer = peer,
                            items = resolvedItems,
                            totalBytes = totalBytes,
                            senderDeviceName = senderDeviceName,
                            initialRecord = initialRecord.copy(protocol = TransferProtocol.BLUETOOTH)
                        )
                    }
                }
                TransferProtocol.BLUETOOTH -> {
                    if (peer.deviceAddress.contains(":")) {
                        isSuccess = sendViaBluetooth(
                            peer = peer,
                            items = resolvedItems,
                            totalBytes = totalBytes,
                            senderDeviceName = senderDeviceName,
                            initialRecord = initialRecord.copy(protocol = TransferProtocol.BLUETOOTH)
                        )
                    }
                    // Fallback to direct socket if peer was discovered over network or BLE provided its IP
                    if (!isSuccess && peer.hostAddress != null) {
                        isSuccess = sendViaWifi(
                            peer = peer,
                            items = resolvedItems,
                            totalBytes = totalBytes,
                            senderDeviceName = senderDeviceName,
                            initialRecord = initialRecord.copy(protocol = TransferProtocol.BLUETOOTH)
                        )
                    }
                }
            }

            if (!isSuccess) {
                TransferController.cancelSendTransfer()
            }
            if (!TransferController.isDiscoverable.value) {
                releaseHighPerfWifiLock()
            }

            withContext(Dispatchers.Main) {
                onComplete(isSuccess)
            }
        }
    }

    private suspend fun sendViaWifi(
        peer: DiscoveredPeer,
        items: List<MediaItem>,
        totalBytes: Long,
        senderDeviceName: String,
        initialRecord: TransferRecord
    ): Boolean = withContext(Dispatchers.IO) {
        val host = peer.hostAddress ?: peer.deviceAddress
        val targetPort = if (peer.port > 0) peer.port else MIORA_PORT

        var socket: Socket? = null
        try {
            socket = Socket()
            activeClientTcpSocket = socket
            socket.tcpNoDelay = true
            socket.sendBufferSize = BUFFER_SIZE
            socket.receiveBufferSize = BUFFER_SIZE
            socket.keepAlive = true
            socket.connect(InetSocketAddress(host, targetPort), 4500)

            return@withContext performSendStream(
                inputStream = socket.getInputStream(),
                outputStream = socket.getOutputStream(),
                items = items,
                totalBytes = totalBytes,
                senderDeviceName = senderDeviceName,
                initialRecord = initialRecord
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        } finally {
            try { socket?.close() } catch (_: Exception) {}
            activeClientTcpSocket = null
        }
    }

    private suspend fun sendViaBluetooth(
        peer: DiscoveredPeer,
        items: List<MediaItem>,
        totalBytes: Long,
        senderDeviceName: String,
        initialRecord: TransferRecord
    ): Boolean = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext false
        var btSocket: BluetoothSocket? = null
        try {
            val btManager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = btManager?.adapter ?: BluetoothAdapter.getDefaultAdapter() ?: return@withContext false
            val device = adapter.getRemoteDevice(peer.deviceAddress)
            adapter.cancelDiscovery()

            // Try BLE L2CAP channel first if advertised (works with unbonded BLE random RPA addresses)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && peer.blePsm > 0) {
                try {
                    val l2capSocket = device.createInsecureL2capChannel(peer.blePsm)
                    activeClientBtSocket = l2capSocket
                    l2capSocket.connect()
                    btSocket = l2capSocket
                } catch (_: Exception) {
                    try { activeClientBtSocket?.close() } catch (_: Exception) {}
                    activeClientBtSocket = null
                    btSocket = null
                }
            }

            // Fallback to Classic RFCOMM Socket
            if (btSocket == null) {
                val rfcommSocket = try {
                    device.createInsecureRfcommSocketToServiceRecord(MIORA_RFCOMM_UUID)
                } catch (_: Exception) {
                    device.createRfcommSocketToServiceRecord(MIORA_RFCOMM_UUID)
                }
                activeClientBtSocket = rfcommSocket
                rfcommSocket.connect()
                btSocket = rfcommSocket
            }

            return@withContext performSendStream(
                inputStream = btSocket.inputStream,
                outputStream = btSocket.outputStream,
                items = items,
                totalBytes = totalBytes,
                senderDeviceName = senderDeviceName,
                initialRecord = initialRecord
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        } finally {
            try { btSocket?.close() } catch (_: Exception) {}
            activeClientBtSocket = null
        }
    }

    fun resolveExactFileSize(context: Context, item: MediaItem): Long {
        try {
            context.contentResolver.openFileDescriptor(item.uri, "r")?.use { pfd ->
                val stat = pfd.statSize
                if (stat > 0L) return stat
            }
        } catch (_: Exception) {}
        try {
            context.contentResolver.openAssetFileDescriptor(item.uri, "r")?.use { afd ->
                val len = afd.length
                if (len > 0L) return len
            }
        } catch (_: Exception) {}
        if (item.size > 0L) return item.size
        try {
            context.contentResolver.openInputStream(item.uri)?.use { stream ->
                val buf = ByteArray(64 * 1024)
                var count = 0L
                var r: Int
                while (stream.read(buf).also { r = it } != -1) {
                    count += r
                }
                if (count > 0L) return count
            }
        } catch (_: Exception) {}
        return item.size.coerceAtLeast(0L)
    }

    private suspend fun performSendStream(
        inputStream: InputStream,
        outputStream: OutputStream,
        items: List<MediaItem>,
        totalBytes: Long,
        senderDeviceName: String,
        initialRecord: TransferRecord
    ): Boolean = withContext(Dispatchers.IO) {
        val dos = DataOutputStream(BufferedOutputStream(outputStream, BUFFER_SIZE))
        val dis = DataInputStream(BufferedInputStream(inputStream, BUFFER_SIZE))

        // 1. Handshake
        dos.writeUTF(PROTOCOL_MAGIC)
        dos.writeUTF(senderDeviceName)
        dos.writeInt(items.size)
        dos.writeLong(totalBytes)
        dos.writeUTF(items.first().name)

        items.forEach { item ->
            dos.writeUTF(item.name)
            dos.writeLong(item.size)
        }
        dos.flush()

        // 2. Await Recipient Acceptance
        val response = dis.readUTF()
        if (response != "ACCEPT") {
            TransferController.cancelSendTransfer()
            return@withContext false
        }

        // 3. Streaming File Bytes
        TransferController.updateActiveSend(initialRecord.copy(status = TransferStatus.TRANSFERRING))

        val context = appContext ?: return@withContext false
        var totalBytesSent = 0L
        val startTime = System.currentTimeMillis()
        var lastUiUpdateMs = 0L
        val buffer = ByteArray(BUFFER_SIZE)

        for (item in items) {
            val expectedSize = item.size.coerceAtLeast(0L)
            dos.writeUTF(item.name)
            dos.writeLong(expectedSize)
            dos.flush()

            var fileBytesSent = 0L
            context.contentResolver.openInputStream(item.uri)?.use { inStream ->
                val bufferedIn = BufferedInputStream(inStream, BUFFER_SIZE)
                while (fileBytesSent < expectedSize) {
                    if (!isActive) throw CancellationException("Transfer cancelled")
                    val maxToRead = ((expectedSize - fileBytesSent).coerceAtMost(buffer.size.toLong())).toInt()
                    val read = bufferedIn.read(buffer, 0, maxToRead)
                    if (read == -1) break
                    dos.write(buffer, 0, read)
                    fileBytesSent += read
                    totalBytesSent += read

                    val now = System.currentTimeMillis()
                    if (now - lastUiUpdateMs >= 80L || fileBytesSent == expectedSize) {
                        lastUiUpdateMs = now
                        val elapsed = (now - startTime) / 1000f
                        val speed = if (elapsed > 0f) (totalBytesSent / (1024f * 1024f)) / elapsed else 0f
                        val progress = if (totalBytes > 0L) (totalBytesSent.toFloat() / totalBytes).coerceIn(0f, 1f) else 1f

                        TransferController.updateActiveSend(
                            initialRecord.copy(
                                status = TransferStatus.TRANSFERRING,
                                progress = progress,
                                speedMbps = speed
                            )
                        )
                    }
                }
            }

            // Guarantee stream frame alignment if a virtual content provider reported slightly more bytes than EOF
            if (fileBytesSent < expectedSize) {
                val zeroPad = ByteArray(8192)
                while (fileBytesSent < expectedSize) {
                    val toPad = ((expectedSize - fileBytesSent).coerceAtMost(zeroPad.size.toLong())).toInt()
                    dos.write(zeroPad, 0, toPad)
                    fileBytesSent += toPad
                    totalBytesSent += toPad
                }
            }
            dos.flush()
        }

        // 4. Verification & Completion
        val finalResponse = dis.readUTF()
        if (finalResponse == "SUCCESS") {
            val totalElapsedSec = ((System.currentTimeMillis() - startTime) / 1000f).coerceAtLeast(0.05f)
            val finalRecord = initialRecord.copy(
                status = TransferStatus.COMPLETED,
                progress = 1f,
                speedMbps = (totalBytesSent / (1024f * 1024f)) / totalElapsedSec
            )
            TransferController.completeSend(finalRecord)
            return@withContext true
        } else {
            TransferController.cancelSendTransfer()
            return@withContext false
        }
    }

    fun cancelSend() {
        clientJob?.cancel()
        clientJob = null
        try { activeClientTcpSocket?.close() } catch (_: Exception) {}
        activeClientTcpSocket = null
        try { activeClientBtSocket?.close() } catch (_: Exception) {}
        activeClientBtSocket = null
    }

    fun cancelReceive() {
        try { activeServerSocketConnection?.close() } catch (_: Exception) {}
        activeServerSocketConnection = null
        try { activeBluetoothSocketConnection?.close() } catch (_: Exception) {}
        activeBluetoothSocketConnection = null
    }
}
