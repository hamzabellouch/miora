package com.tkno.miora.ui.main

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tkno.miora.model.DeviceConnectivityManager
import com.tkno.miora.ui.component.AppRequirementsDialog
import com.tkno.miora.ui.component.ClassicBottomBar
import com.tkno.miora.ui.component.FloatingBottomBar
import com.tkno.miora.ui.component.loadNavOrder
import com.tkno.miora.ui.menu.MenuScreen
import com.tkno.miora.ui.page.AppUpdater
import com.tkno.miora.ui.receive.ReceiveScreen
import com.tkno.miora.ui.send.SendScreen
import com.tkno.miora.ui.transfers.TransfersScreen

enum class Tab {
    Send, Receive, Transfers, Menu
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    navigateToUpdate: Boolean = false,
    triggerUpdate: Boolean = false,
    onNavigateToUpdateConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("miora_prefs", Context.MODE_PRIVATE) }
    var isAutoUpdateEnabled by remember { mutableStateOf(prefs.getBoolean("auto_update_enabled", false)) }
    var hideLabels by remember { mutableStateOf(prefs.getBoolean("hide_navigation_labels", false)) }
    var useClassicTaskbar by remember { mutableStateOf(prefs.getBoolean("use_classic_taskbar", false)) }
    var animateIndicator by remember { mutableStateOf(prefs.getBoolean("animate_taskbar_indicator", true)) }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
            when (key) {
                "auto_update_enabled" -> isAutoUpdateEnabled = p.getBoolean("auto_update_enabled", false)
                "hide_navigation_labels" -> hideLabels = p.getBoolean("hide_navigation_labels", false)
                "use_classic_taskbar" -> useClassicTaskbar = p.getBoolean("use_classic_taskbar", false)
                "animate_taskbar_indicator" -> animateIndicator = p.getBoolean("animate_taskbar_indicator", true)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    // Launch background update check on app startup
    AppUpdater(isAutoUpdateEnabled = isAutoUpdateEnabled)

    val isWifiEnabled by DeviceConnectivityManager.isWifiEnabled.collectAsState()
    val isBluetoothEnabled by DeviceConnectivityManager.isBluetoothEnabled.collectAsState()
    val isNearbyGranted by DeviceConnectivityManager.isNearbyPermissionGranted.collectAsState()

    val areAllRequirementsMet = isWifiEnabled && isBluetoothEnabled && isNearbyGranted
    var showRequirementsDialog by rememberSaveable { mutableStateOf(false) }
    var hasPromptedOnEntry by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        DeviceConnectivityManager.checkStates(context)
        if (!hasPromptedOnEntry) {
            if (!DeviceConnectivityManager.areAllRequirementsMet(context)) {
                showRequirementsDialog = true
            }
            hasPromptedOnEntry = true
        }
    }

    val reorderableTabs = remember { mutableStateListOf(*loadNavOrder(prefs).toTypedArray()) }
    val defaultTab by remember { derivedStateOf { reorderableTabs.firstOrNull() ?: Tab.Send } }
    var currentTab by rememberSaveable { mutableStateOf(loadNavOrder(prefs).firstOrNull() ?: Tab.Send) }
    val saveableStateHolder = rememberSaveableStateHolder()

    if (showRequirementsDialog) {
        com.tkno.miora.ui.component.AppRequirementsDialog(
            onDismiss = { showRequirementsDialog = false }
        )
    }

    LaunchedEffect(navigateToUpdate) {
        if (navigateToUpdate) {
            currentTab = Tab.Menu
        }
    }

    BackHandler(enabled = currentTab != defaultTab) {
        currentTab = defaultTab
    }

    val appBackground = MaterialTheme.colorScheme.background

    Scaffold(
        bottomBar = {
            if (useClassicTaskbar) {
                ClassicBottomBar(
                    selectedTab = currentTab,
                    onTabSelect = { currentTab = it },
                    reorderableTabs = reorderableTabs,
                    hideLabels = hideLabels
                )
            }
        },
        containerColor = appBackground,
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(appBackground)
                .padding(
                    top = paddingValues.calculateTopPadding(),
                    bottom = if (useClassicTaskbar) paddingValues.calculateBottomPadding() else 0.dp
                )
        ) {
            saveableStateHolder.SaveableStateProvider(currentTab) {
                when (currentTab) {
                    Tab.Send -> SendScreen()
                    Tab.Receive -> ReceiveScreen()
                    Tab.Transfers -> TransfersScreen()
                    Tab.Menu -> MenuScreen(
                        navigateToUpdate = navigateToUpdate,
                        triggerUpdate = triggerUpdate,
                        onNavigateToUpdateConsumed = onNavigateToUpdateConsumed
                    )
                }
            }

            if (!useClassicTaskbar) {
                FloatingBottomBar(
                    selectedTab = currentTab,
                    onTabSelect = { currentTab = it },
                    reorderableTabs = reorderableTabs,
                    hideLabels = hideLabels,
                    animateIndicator = animateIndicator,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}
