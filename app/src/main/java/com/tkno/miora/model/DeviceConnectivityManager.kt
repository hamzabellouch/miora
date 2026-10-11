package com.tkno.miora.model

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

@SuppressLint("MissingPermission")
object DeviceConnectivityManager {

    private val _isWifiEnabled = MutableStateFlow(false)
    val isWifiEnabled: StateFlow<Boolean> = _isWifiEnabled.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(false)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _isWifiP2pEnabled = MutableStateFlow(false)
    val isWifiP2pEnabled: StateFlow<Boolean> = _isWifiP2pEnabled.asStateFlow()

    private const val PREF_ALLOW_LOCATION = "allow_location_permission"
    private const val PREF_USE_5GHZ_BAND = "use_5ghz_wifi_direct"

    private val _isNearbyPermissionGranted = MutableStateFlow(false)
    val isNearbyPermissionGranted: StateFlow<Boolean> = _isNearbyPermissionGranted.asStateFlow()

    private val _allowLocationPermission = MutableStateFlow(true)
    val allowLocationPermission: StateFlow<Boolean> = _allowLocationPermission.asStateFlow()

    private val _use5GhzBand = MutableStateFlow(false)
    val use5GhzBand: StateFlow<Boolean> = _use5GhzBand.asStateFlow()

    // Wi-Fi P2P (Direct) Live State
    private val _wifiP2pInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val wifiP2pInfo: StateFlow<WifiP2pInfo?> = _wifiP2pInfo.asStateFlow()

    private val _wifiP2pGroup = MutableStateFlow<WifiP2pGroup?>(null)
    val wifiP2pGroup: StateFlow<WifiP2pGroup?> = _wifiP2pGroup.asStateFlow()

    private val _wifiP2pPeers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val wifiP2pPeers: StateFlow<List<WifiP2pDevice>> = _wifiP2pPeers.asStateFlow()

    // Hotspot (LocalOnlyHotspot) State for Sender
    private var hotspotReservation: WifiManager.LocalOnlyHotspotReservation? = null
    private val _isHotspotActive = MutableStateFlow(false)
    val isHotspotActive: StateFlow<Boolean> = _isHotspotActive.asStateFlow()

    private var activeHotspotNetworkCallback: ConnectivityManager.NetworkCallback? = null

    private var appContext: Context? = null
    private var isReceiverRegistered = false

    var wifiP2pManager: WifiP2pManager? = null
        private set
    var wifiP2pChannel: WifiP2pManager.Channel? = null
        private set

    var isWifiP2pSupported: Boolean = false
        private set

    var isBluetoothSupported: Boolean = false
        private set

    private val connectivityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    _isBluetoothEnabled.value = (state == BluetoothAdapter.STATE_ON)
                    context?.let { checkStates(it) }
                    MioraTransferEngine.onNetworkChanged()
                }
                WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
                    _isWifiEnabled.value = (state == WifiManager.WIFI_STATE_ENABLED)
                    context?.let { checkStates(it) }
                    MioraTransferEngine.onNetworkChanged()
                }
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, WifiP2pManager.WIFI_P2P_STATE_DISABLED)
                    _isWifiP2pEnabled.value = (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    requestWifiP2pPeers()
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    requestWifiP2pConnectionInfo()
                    context?.let { checkStates(it) }
                    MioraTransferEngine.onNetworkChanged()
                }
            }
        }
    }

    fun init(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx

        val pm = appCtx.packageManager
        isWifiP2pSupported = pm.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)
        isBluetoothSupported = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)

        if (isWifiP2pSupported) {
            try {
                wifiP2pManager = appCtx.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
                wifiP2pChannel = wifiP2pManager?.initialize(appCtx, Looper.getMainLooper(), null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        checkStates(appCtx)

        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            }
            try {
                appCtx.registerReceiver(connectivityReceiver, filter)
                isReceiverRegistered = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun checkStates(context: Context? = null) {
        val ctx = context ?: appContext ?: return
        // Bluetooth check
        val btManager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val btAdapter = btManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
        _isBluetoothEnabled.value = btAdapter?.isEnabled == true

        // Wi-Fi check
        val wifiManager = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        _isWifiEnabled.value = wifiManager?.isWifiEnabled == true

        // Location & 5GHz preference check
        val prefs = ctx.getSharedPreferences("miora_prefs", Context.MODE_PRIVATE)
        _allowLocationPermission.value = prefs.getBoolean(PREF_ALLOW_LOCATION, true)
        _use5GhzBand.value = prefs.getBoolean(PREF_USE_5GHZ_BAND, false)

        // Permissions check
        _isNearbyPermissionGranted.value = hasNearbyPermissions(ctx)
    }

    // -------------------------------------------------------------
    // Sender Hotspot (LocalOnlyHotspot) Operations
    // -------------------------------------------------------------

    /**
     * Starts a high-speed local Wi-Fi Hotspot on the Sender device.
     */
    fun startSenderHotspot(
        onStarted: (ssid: String, passphrase: String, ip: String) -> Unit,
        onFailed: (Int) -> Unit
    ) {
        val ctx = appContext ?: return
        val wifiManager = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation?) {
                        super.onStarted(reservation)
                        hotspotReservation = reservation
                        _isHotspotActive.value = true

                        var ssid = ""
                        var password = ""

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val config = reservation?.softApConfiguration
                            ssid = config?.ssid ?: ""
                            password = config?.passphrase ?: ""
                        } else {
                            @Suppress("DEPRECATION")
                            val config = reservation?.wifiConfiguration
                            ssid = config?.SSID ?: ""
                            password = config?.preSharedKey ?: ""
                        }

                        // Determine the Hotspot Gateway IP (typically 192.168.43.1 or from interface)
                        val ip = getLocalIpAddress() ?: "192.168.43.1"
                        onStarted(ssid.trim('"'), password.trim('"'), ip)
                    }

                    override fun onStopped() {
                        super.onStopped()
                        hotspotReservation = null
                        _isHotspotActive.value = false
                    }

                    override fun onFailed(reason: Int) {
                        super.onFailed(reason)
                        hotspotReservation = null
                        _isHotspotActive.value = false
                        onFailed(reason)
                    }
                }, Handler(Looper.getMainLooper()))
            } catch (e: Exception) {
                e.printStackTrace()
                _isHotspotActive.value = false
                onFailed(-1)
            }
        } else {
            onFailed(-2)
        }
    }

    /**
     * Stops the active LocalOnlyHotspot on the Sender device.
     */
    fun stopSenderHotspot() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                hotspotReservation?.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            hotspotReservation = null
            _isHotspotActive.value = false
        }
    }

    /**
     * Synchronizes device visible name with Wi-Fi Direct P2P Device.
     */
    fun setWifiP2pDeviceName(name: String) {
        val mgr = wifiP2pManager ?: return
        val ch = wifiP2pChannel ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val prefixed = if (trimmed.startsWith("Miora-", ignoreCase = true)) {
            trimmed.take(24)
        } else {
            "Miora-${trimmed.take(18)}"
        }
        try {
            val method = mgr.javaClass.getMethod(
                "setDeviceName",
                WifiP2pManager.Channel::class.java,
                String::class.java,
                WifiP2pManager.ActionListener::class.java
            )
            method.invoke(mgr, ch, prefixed, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {}
                override fun onFailure(reason: Int) {}
            })
        } catch (_: Exception) {}
    }

    /**
     * Creates a Wi-Fi Direct Group (Access Point) configured with the device name
     * and retrieves its SSID, Passphrase, and IP.
     */
    fun startWifiDirectAccessPoint(
        deviceName: String,
        onStarted: (ssid: String, passphrase: String, ip: String) -> Unit,
        onFailed: (Int) -> Unit
    ) {
        val mgr = wifiP2pManager
        val ch = wifiP2pChannel
        if (mgr == null || ch == null) {
            onFailed(-1)
            return
        }

        setWifiP2pDeviceName(deviceName)

        try {
            mgr.removeGroup(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    createNewP2pGroup(mgr, ch, deviceName, onStarted, onFailed)
                }
                override fun onFailure(reason: Int) {
                    createNewP2pGroup(mgr, ch, deviceName, onStarted, onFailed)
                }
            })
        } catch (e: Exception) {
            createNewP2pGroup(mgr, ch, deviceName, onStarted, onFailed)
        }
    }

    private fun pollP2pGroupInfo(
        mgr: WifiP2pManager,
        ch: WifiP2pManager.Channel,
        fallbackSsid: String,
        fallbackPass: String,
        requireNonNullGroup: Boolean,
        attemptsLeft: Int = 6,
        onStarted: (ssid: String, passphrase: String, ip: String) -> Unit,
        onFailed: (Int) -> Unit
    ) {
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.postDelayed({
            try {
                mgr.requestGroupInfo(ch) { group ->
                    val hasReadyGroup = group != null && !group.networkName.isNullOrBlank()
                    if (!hasReadyGroup && attemptsLeft > 1) {
                        pollP2pGroupInfo(
                            mgr = mgr,
                            ch = ch,
                            fallbackSsid = fallbackSsid,
                            fallbackPass = fallbackPass,
                            requireNonNullGroup = requireNonNullGroup,
                            attemptsLeft = attemptsLeft - 1,
                            onStarted = onStarted,
                            onFailed = onFailed
                        )
                        return@requestGroupInfo
                    }

                    if (group == null && requireNonNullGroup) {
                        onFailed(-2)
                        return@requestGroupInfo
                    }

                    _wifiP2pGroup.value = group
                    val finalSsid = group?.networkName?.takeIf { it.isNotBlank() } ?: fallbackSsid
                    val finalPass = group?.passphrase?.takeIf { it.isNotBlank() } ?: fallbackPass
                    val ip = getP2pInterfaceIpAddress() ?: getLocalIpAddress() ?: "192.168.49.1"
                    onStarted(finalSsid, finalPass, ip)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (requireNonNullGroup) {
                    onFailed(-3)
                } else {
                    val ip = getP2pInterfaceIpAddress() ?: getLocalIpAddress() ?: "192.168.49.1"
                    onStarted(fallbackSsid, fallbackPass, ip)
                }
            }
        }, 250)
    }

    private fun getP2pInterfaceIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return null
            val p2pInterface = interfaces.firstOrNull {
                it.isUp && it.name.contains("p2p", ignoreCase = true)
            } ?: return null
            for (addr in p2pInterface.inetAddresses) {
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    val host = addr.hostAddress
                    if (!host.isNullOrBlank() && !host.startsWith("127.")) {
                        return host
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun createNewP2pGroup(
        mgr: WifiP2pManager,
        ch: WifiP2pManager.Channel,
        deviceName: String,
        onStarted: (ssid: String, passphrase: String, ip: String) -> Unit,
        onFailed: (Int) -> Unit
    ) {
        val cleanName = deviceName
            .replace(Regex("[^a-zA-Z0-9_-]"), "")
            .trim('-', '_')
            .take(18)
            .ifEmpty { "Miora" }

        val customSsid = if (cleanName.startsWith("DIRECT-", ignoreCase = true)) cleanName else "DIRECT-MR-$cleanName"
        val customPass = "miora" + (100000..999999).random().toString()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val prefer5Ghz = isUse5GhzBandEnabled()
                val primaryBand = if (prefer5Ghz) {
                    WifiP2pConfig.GROUP_OWNER_BAND_5GHZ
                } else {
                    WifiP2pConfig.GROUP_OWNER_BAND_2GHZ
                }

                val primaryConfig = WifiP2pConfig.Builder()
                    .setNetworkName(customSsid)
                    .setPassphrase(customPass)
                    .setGroupOperatingBand(primaryBand)
                    .build()

                mgr.createGroup(ch, primaryConfig, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        pollP2pGroupInfo(
                            mgr = mgr,
                            ch = ch,
                            fallbackSsid = customSsid,
                            fallbackPass = customPass,
                            requireNonNullGroup = false,
                            onStarted = onStarted,
                            onFailed = onFailed
                        )
                    }

                    override fun onFailure(reason: Int) {
                        // Fallback to AUTO band if primary band fails
                        try {
                            val configAuto = WifiP2pConfig.Builder()
                                .setNetworkName(customSsid)
                                .setPassphrase(customPass)
                                .setGroupOperatingBand(WifiP2pConfig.GROUP_OWNER_BAND_AUTO)
                                .build()

                            mgr.createGroup(ch, configAuto, object : WifiP2pManager.ActionListener {
                                override fun onSuccess() {
                                    pollP2pGroupInfo(
                                        mgr = mgr,
                                        ch = ch,
                                        fallbackSsid = customSsid,
                                        fallbackPass = customPass,
                                        requireNonNullGroup = false,
                                        onStarted = onStarted,
                                        onFailed = onFailed
                                    )
                                }

                                override fun onFailure(reasonAuto: Int) {
                                    createDefaultP2pGroup(mgr, ch, onStarted, onFailed)
                                }
                            })
                        } catch (_: Exception) {
                            createDefaultP2pGroup(mgr, ch, onStarted, onFailed)
                        }
                    }
                })
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        createDefaultP2pGroup(mgr, ch, onStarted, onFailed)
    }

    private fun createDefaultP2pGroup(
        mgr: WifiP2pManager,
        ch: WifiP2pManager.Channel,
        onStarted: (ssid: String, passphrase: String, ip: String) -> Unit,
        onFailed: (Int) -> Unit
    ) {
        try {
            mgr.createGroup(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    pollP2pGroupInfo(
                        mgr = mgr,
                        ch = ch,
                        fallbackSsid = "",
                        fallbackPass = "",
                        requireNonNullGroup = true,
                        onStarted = onStarted,
                        onFailed = onFailed
                    )
                }
                override fun onFailure(reason: Int) {
                    onFailed(reason)
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
            onFailed(-3)
        }
    }

    /**
     * Unified Access Point creation for Receive Mode:
     * 1. Creates Wi-Fi Direct Group named after the device visible name (DIRECT-MR-<deviceName>).
     * 2. If Hotspot/P2P fails or is unsupported, automatically falls back to LocalOnlyHotspot.
     * 3. If both fail, falls back to LAN IP.
     */
    fun startReceiverAccessPoint(
        deviceName: String,
        onStarted: (ssid: String, passphrase: String, ips: List<String>) -> Unit,
        onFailed: (reason: String) -> Unit
    ) {
        val ctx = appContext
        if (ctx == null) {
            onFailed("Context not initialized")
            return
        }

        setWifiP2pDeviceName(deviceName)

        // Tier 1: Try Wi-Fi Direct Group Owner AP with custom device name
        if (isWifiP2pSupported) {
            startWifiDirectAccessPoint(
                deviceName = deviceName,
                onStarted = { p2pSsid, p2pPass, p2pIp ->
                    val allIps = (listOf(p2pIp, "192.168.49.1") + getAllLocalIpAddresses() + listOf("192.168.43.1"))
                        .filter { it.isNotBlank() && it != "127.0.0.1" && it != "0.0.0.0" }
                        .distinct()
                    onStarted(p2pSsid, p2pPass, allIps)
                },
                onFailed = { p2pErrorCode ->
                    // Tier 2: Fall back to LocalOnlyHotspot
                    startSenderHotspot(
                        onStarted = { ssid, passphrase, ip ->
                            val allIps = (listOf(ip, "192.168.43.1") + getAllLocalIpAddresses() + listOf("192.168.49.1"))
                                .filter { it.isNotBlank() && it != "127.0.0.1" && it != "0.0.0.0" }
                                .distinct()
                            onStarted(ssid, passphrase, allIps)
                        },
                        onFailed = { hotspotErrorCode ->
                            // Tier 3: Fallback to existing LAN IP
                            val lanIps = getAllLocalIpAddresses()
                                .filter { it.isNotBlank() && it != "127.0.0.1" && it != "0.0.0.0" }
                                .distinct()
                            if (lanIps.isNotEmpty()) {
                                onStarted("", "", lanIps)
                            } else {
                                onFailed("Could not start Wi-Fi Direct or Hotspot (Codes: $p2pErrorCode / $hotspotErrorCode)")
                            }
                        }
                    )
                }
            )
        } else {
            // Tier 1b (If P2P not supported): Try LocalOnlyHotspot
            startSenderHotspot(
                onStarted = { ssid, passphrase, ip ->
                    val allIps = (listOf(ip, "192.168.43.1") + getAllLocalIpAddresses() + listOf("192.168.49.1"))
                        .filter { it.isNotBlank() && it != "127.0.0.1" && it != "0.0.0.0" }
                        .distinct()
                    onStarted(ssid, passphrase, allIps)
                },
                onFailed = { hotspotErrorCode ->
                    val lanIps = getAllLocalIpAddresses()
                        .filter { it.isNotBlank() && it != "127.0.0.1" && it != "0.0.0.0" }
                        .distinct()
                    if (lanIps.isNotEmpty()) {
                        onStarted("", "", lanIps)
                    } else {
                        onFailed("Could not start Hotspot (Code: $hotspotErrorCode)")
                    }
                }
            )
        }
    }

    /**
     * Stops any active Hotspot or Wi-Fi Direct Group on the receiver.
     */
    fun stopReceiverAccessPoint() {
        stopSenderHotspot()
        removeWifiDirectGroup()
    }

    /**
     * Receiver/Sender connects to peer's Hotspot/Wi-Fi Direct AP automatically using WifiNetworkSpecifier.
     */
    fun connectReceiverToHotspot(
        ssid: String,
        passphrase: String,
        onConnected: (network: Network?, gatewayIp: String?) -> Unit,
        onFailed: () -> Unit
    ) {
        val ctx = appContext ?: run {
            onFailed()
            return
        }
        val connManager = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: run {
            onFailed()
            return
        }

        disconnectReceiverFromHotspot()

        val cleanSsid = ssid.trim('"').trim()
        val cleanPass = passphrase.trim('"').trim()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val specifierBuilder = WifiNetworkSpecifier.Builder()
                    .setSsid(cleanSsid)

                if (cleanPass.isNotBlank()) {
                    specifierBuilder.setWpa2Passphrase(cleanPass)
                }

                val specifier = specifierBuilder.build()

                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .setNetworkSpecifier(specifier)
                    .build()

                val mainHandler = Handler(Looper.getMainLooper())
                val callback = object : ConnectivityManager.NetworkCallback() {
                    private var hasReported = false

                    private fun reportConnected(network: Network, gateway: String?) {
                        mainHandler.post {
                            if (!hasReported) {
                                hasReported = true
                                onConnected(network, gateway)
                            }
                        }
                    }

                    override fun onAvailable(network: Network) {
                        super.onAvailable(network)
                        try {
                            connManager.bindProcessToNetwork(network)
                        } catch (_: Exception) {}

                        val immediateGateway = getGatewayIp(ctx, network)
                        if (!immediateGateway.isNullOrBlank()) {
                            reportConnected(network, immediateGateway)
                        } else {
                            // Wait briefly for onLinkPropertiesChanged to populate DHCP gateway route
                            mainHandler.postDelayed({
                                reportConnected(network, getGatewayIp(ctx, network))
                            }, 250)
                        }
                    }

                    override fun onLinkPropertiesChanged(network: Network, linkProperties: android.net.LinkProperties) {
                        super.onLinkPropertiesChanged(network, linkProperties)
                        try {
                            connManager.bindProcessToNetwork(network)
                        } catch (_: Exception) {}
                        val gateway = linkProperties.routes
                            .firstOrNull { it.isDefaultRoute || it.gateway != null }
                            ?.gateway?.hostAddress
                            ?.takeIf { it.isNotBlank() && it != "0.0.0.0" }
                            ?: getGatewayIp(ctx, network)
                        if (!gateway.isNullOrBlank()) {
                            reportConnected(network, gateway)
                        }
                    }

                    override fun onUnavailable() {
                        super.onUnavailable()
                        mainHandler.post {
                            if (!hasReported) {
                                hasReported = true
                                onFailed()
                            }
                        }
                    }

                    override fun onLost(network: Network) {
                        super.onLost(network)
                    }
                }

                activeHotspotNetworkCallback = callback
                connManager.requestNetwork(request, callback, 20000)
            } catch (e: Exception) {
                e.printStackTrace()
                onFailed()
            }
        } else {
            // Android 9 and below fallback
            try {
                val wifiManager = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                @Suppress("DEPRECATION")
                val wifiConfig = WifiConfiguration().apply {
                    SSID = "\"$cleanSsid\""
                    if (cleanPass.isNotBlank()) {
                        preSharedKey = "\"$cleanPass\""
                    } else {
                        allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                    }
                }
                @Suppress("DEPRECATION")
                val netId = wifiManager?.addNetwork(wifiConfig) ?: -1
                if (netId != -1) {
                    @Suppress("DEPRECATION")
                    wifiManager?.disconnect()
                    @Suppress("DEPRECATION")
                    wifiManager?.enableNetwork(netId, true)
                    @Suppress("DEPRECATION")
                    wifiManager?.reconnect()
                    Handler(Looper.getMainLooper()).postDelayed({
                        val gateway = getGatewayIp(ctx, null)
                        onConnected(null, gateway)
                    }, 2500)
                } else {
                    onFailed()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onFailed()
            }
        }
    }

    /**
     * Extracts the gateway IP address of the connected Wi-Fi or Hotspot network.
     */
    fun getGatewayIp(context: Context, network: Network? = null): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (network != null && cm != null) {
            try {
                val lp = cm.getLinkProperties(network)
                val gateway = lp?.routes?.firstOrNull { it.isDefaultRoute || it.gateway != null }?.gateway?.hostAddress
                if (!gateway.isNullOrBlank() && gateway != "0.0.0.0") {
                    return gateway
                }
            } catch (_: Exception) {}
        }
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            val dhcp = wm?.dhcpInfo
            if (dhcp != null && dhcp.gateway != 0) {
                val ip = ((dhcp.gateway and 0xFF).toString() + "." +
                        ((dhcp.gateway shr 8) and 0xFF) + "." +
                        ((dhcp.gateway shr 16) and 0xFF) + "." +
                        ((dhcp.gateway shr 24) and 0xFF))
                if (ip != "0.0.0.0") return ip
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Unbinds receiver from Sender's Hotspot.
     */
    fun disconnectReceiverFromHotspot() {
        val ctx = appContext ?: return
        val connManager = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        try {
            activeHotspotNetworkCallback?.let {
                connManager.unregisterNetworkCallback(it)
            }
        } catch (_: Exception) {}
        activeHotspotNetworkCallback = null
        try {
            connManager.bindProcessToNetwork(null)
        } catch (_: Exception) {}
    }

    // -------------------------------------------------------------
    // Wi-Fi P2P (Wi-Fi Direct) Operations
    // -------------------------------------------------------------

    fun createWifiDirectGroup(onSuccess: (() -> Unit)? = null, onFailure: ((Int) -> Unit)? = null) {
        val mgr = wifiP2pManager ?: return
        val ch = wifiP2pChannel ?: return
        try {
            mgr.createGroup(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    requestWifiP2pConnectionInfo()
                    onSuccess?.invoke()
                }
                override fun onFailure(reason: Int) {
                    onFailure?.invoke(reason)
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
            onFailure?.invoke(-1)
        }
    }

    fun removeWifiDirectGroup(onComplete: (() -> Unit)? = null) {
        val mgr = wifiP2pManager ?: return
        val ch = wifiP2pChannel ?: return
        try {
            mgr.removeGroup(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    _wifiP2pInfo.value = null
                    _wifiP2pGroup.value = null
                    onComplete?.invoke()
                }
                override fun onFailure(reason: Int) {
                    onComplete?.invoke()
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
            onComplete?.invoke()
        }
    }

    fun discoverWifiDirectPeers(onSuccess: (() -> Unit)? = null, onFailure: ((Int) -> Unit)? = null) {
        val mgr = wifiP2pManager ?: return
        val ch = wifiP2pChannel ?: return
        try {
            mgr.discoverPeers(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    onSuccess?.invoke()
                }
                override fun onFailure(reason: Int) {
                    onFailure?.invoke(reason)
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
            onFailure?.invoke(-1)
        }
    }

    fun stopWifiDirectPeerDiscovery(onComplete: (() -> Unit)? = null) {
        val mgr = wifiP2pManager ?: return
        val ch = wifiP2pChannel ?: return
        try {
            mgr.stopPeerDiscovery(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() { onComplete?.invoke() }
                override fun onFailure(reason: Int) { onComplete?.invoke() }
            })
        } catch (e: Exception) {
            e.printStackTrace()
            onComplete?.invoke()
        }
    }

    fun connectWifiDirect(deviceAddress: String, onSuccess: (() -> Unit)? = null, onFailure: ((Int) -> Unit)? = null) {
        val mgr = wifiP2pManager ?: return
        val ch = wifiP2pChannel ?: return
        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
        }
        try {
            mgr.connect(ch, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    onSuccess?.invoke()
                }
                override fun onFailure(reason: Int) {
                    onFailure?.invoke(reason)
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
            onFailure?.invoke(-1)
        }
    }

    fun requestWifiP2pPeers() {
        val mgr = wifiP2pManager ?: return
        val ch = wifiP2pChannel ?: return
        try {
            mgr.requestPeers(ch) { peersList ->
                _wifiP2pPeers.value = peersList.deviceList.toList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun requestWifiP2pConnectionInfo() {
        val mgr = wifiP2pManager ?: return
        val ch = wifiP2pChannel ?: return
        try {
            mgr.requestConnectionInfo(ch) { info ->
                _wifiP2pInfo.value = info
            }
            mgr.requestGroupInfo(ch) { group ->
                _wifiP2pGroup.value = group
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setAllowLocationPermission(context: Context, allow: Boolean) {
        val prefs = context.getSharedPreferences("miora_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREF_ALLOW_LOCATION, allow).apply()
        _allowLocationPermission.value = allow
        checkStates(context)
    }

    fun isLocationPermissionAllowed(context: Context? = null): Boolean {
        val ctx = context ?: appContext
        if (ctx != null) {
            return ctx.getSharedPreferences("miora_prefs", Context.MODE_PRIVATE)
                .getBoolean(PREF_ALLOW_LOCATION, true)
        }
        return _allowLocationPermission.value
    }

    fun setUse5GhzBand(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("miora_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(PREF_USE_5GHZ_BAND, enabled).apply()
        _use5GhzBand.value = enabled
    }

    fun isUse5GhzBandEnabled(context: Context? = null): Boolean {
        val ctx = context ?: appContext
        if (ctx != null) {
            return ctx.getSharedPreferences("miora_prefs", Context.MODE_PRIVATE)
                .getBoolean(PREF_USE_5GHZ_BAND, false)
        }
        return _use5GhzBand.value
    }

    fun getNearbyPermissions(context: Context? = null): Array<String> {
        val allowLocation = isLocationPermissionAllowed(context)
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> buildList {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
                if (allowLocation) {
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }.toTypedArray()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> buildList {
                if (allowLocation) {
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }.toTypedArray()
            else -> if (allowLocation) {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            } else {
                emptyArray()
            }
        }
    }

    fun getAllMissingPermissions(context: Context): Array<String> {
        val allNeeded = getNearbyPermissions(context)
        return allNeeded.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.distinct().toTypedArray()
    }

    fun hasNearbyPermissions(context: Context): Boolean {
        return getNearbyPermissions(context).all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun areAllRequirementsMet(context: Context): Boolean {
        return isWifiEnabled.value &&
               isBluetoothEnabled.value &&
               hasNearbyPermissions(context)
    }

    fun openWifiSettings(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val panelIntent = Intent(Settings.Panel.ACTION_WIFI).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(panelIntent)
            } else {
                val wifiIntent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(wifiIntent)
            }
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (ignored: Exception) {}
        }
    }

    fun openBluetoothSettings(context: Context) {
        try {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val settingsIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
            } catch (ignored: Exception) {}
        }
    }

    fun openLocationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (ignored: Exception) {}
        }
    }

    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Modern, robust multi-interface IP discovery without deprecated APIs.
     * Prioritizes active Wi-Fi Direct (P2P), Hotspot (AP), LAN Wi-Fi (WLAN), and Ethernet.
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: return null

            // 1. Check for Hotspot (AP) or Tethering interfaces first (e.g. ap0, softap, wlan1)
            val apInterface = interfaces.firstOrNull {
                it.isUp && (it.name.contains("ap", ignoreCase = true) ||
                            it.name.contains("softap", ignoreCase = true) ||
                            it.name.contains("rndis", ignoreCase = true))
            }
            if (apInterface != null) {
                for (addr in apInterface.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (host != null && !host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }

            // 2. Check for active Wi-Fi Direct (P2P) interface (e.g., p2p-wlan0-0, p2p0)
            val p2pInterface = interfaces.firstOrNull {
                it.isUp && it.name.contains("p2p", ignoreCase = true)
            }
            if (p2pInterface != null) {
                for (addr in p2pInterface.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (host != null && !host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }

            // 3. Check for active Standard Wi-Fi interface (wlan0, wifi, swlan)
            val wifiInterface = interfaces.firstOrNull {
                it.isUp && (it.name.contains("wlan", ignoreCase = true) ||
                            it.name.contains("wifi", ignoreCase = true) ||
                            it.name.contains("swlan", ignoreCase = true))
            }
            if (wifiInterface != null) {
                for (addr in wifiInterface.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (host != null && !host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }

            // 4. Fallback: Any active non-loopback IPv4 interface
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (host != null && !host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Discovers all available non-loopback IPv4 addresses across all active interfaces.
     */
    fun getAllLocalIpAddresses(): List<String> {
        val ips = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (host != null && !host.startsWith("127.") && host != "0.0.0.0") {
                            ips.add(host)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ips.distinct()
    }
}
