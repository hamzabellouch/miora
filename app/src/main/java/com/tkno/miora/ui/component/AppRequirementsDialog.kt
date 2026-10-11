package com.tkno.miora.ui.component

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tkno.miora.R
import com.tkno.miora.model.DeviceConnectivityManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRequirementsDialog(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val isWifiEnabled by DeviceConnectivityManager.isWifiEnabled.collectAsState()
    val isBluetoothEnabled by DeviceConnectivityManager.isBluetoothEnabled.collectAsState()
    val isNearbyGranted by DeviceConnectivityManager.isNearbyPermissionGranted.collectAsState()

    val areAllRequirementsMet = isWifiEnabled &&
            isBluetoothEnabled &&
            isNearbyGranted

    // Permission Launchers
    val nearbyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        DeviceConnectivityManager.checkStates(context)
    }

    val allPermsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        DeviceConnectivityManager.checkStates(context)
    }

    LaunchedEffect(Unit) {
        DeviceConnectivityManager.checkStates(context)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Icon Badge
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(
                        if (areAllRequirementsMet) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (areAllRequirementsMet) Icons.Filled.Check else Icons.Outlined.PermDeviceInformation,
                    contentDescription = null,
                    tint = if (areAllRequirementsMet) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Title
            Text(
                text = stringResource(R.string.requirements_dialog_title),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Description
            Text(
                text = stringResource(R.string.requirements_dialog_desc),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // List of requirements
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Wi-Fi
                RequirementCard(
                    icon = Icons.Outlined.Wifi,
                    title = stringResource(R.string.requirements_wifi_title),
                    description = stringResource(R.string.requirements_wifi_desc),
                    isSatisfied = isWifiEnabled,
                    actionLabel = stringResource(R.string.requirements_turn_on),
                    onAction = { DeviceConnectivityManager.openWifiSettings(context) }
                )

                // 2. Bluetooth
                RequirementCard(
                    icon = Icons.Outlined.Bluetooth,
                    title = stringResource(R.string.requirements_bluetooth_title),
                    description = stringResource(R.string.requirements_bluetooth_desc),
                    isSatisfied = isBluetoothEnabled,
                    actionLabel = stringResource(R.string.requirements_turn_on),
                    onAction = { DeviceConnectivityManager.openBluetoothSettings(context) }
                )

                // 3. Nearby Devices / Bluetooth Permissions
                RequirementCard(
                    icon = Icons.Outlined.Sensors,
                    title = stringResource(R.string.requirements_nearby_perm_title),
                    description = stringResource(R.string.requirements_nearby_perm_desc),
                    isSatisfied = isNearbyGranted,
                    actionLabel = stringResource(R.string.requirements_grant_permission),
                    onAction = {
                        val perms = DeviceConnectivityManager.getNearbyPermissions(context)
                        if (perms.isNotEmpty()) {
                            nearbyLauncher.launch(perms)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // All Set Success Badge Animation
            AnimatedVisibility(
                visible = areAllRequirementsMet,
                enter = fadeIn() + expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.requirements_all_set),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!areAllRequirementsMet) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.requirements_later),
                            fontSize = 14.sp
                        )
                    }

                    Button(
                        onClick = {
                            val missing = DeviceConnectivityManager.getAllMissingPermissions(context)
                            if (missing.isNotEmpty()) {
                                allPermsLauncher.launch(missing)
                            }
                            if (!isWifiEnabled) {
                                DeviceConnectivityManager.openWifiSettings(context)
                            } else if (!isBluetoothEnabled) {
                                DeviceConnectivityManager.openBluetoothSettings(context)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(44.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.requirements_grant_all),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.requirements_done),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RequirementCard(
    icon: ImageVector,
    title: String,
    description: String,
    isSatisfied: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Surface(
        color = if (isSatisfied) MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(14.dp),
        border = if (!isSatisfied) androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSatisfied) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSatisfied) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.padding(end = 6.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }

            // Status Indicator or Action Button
            if (isSatisfied) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = stringResource(R.string.requirements_granted),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            } else {
                FilledTonalButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = actionLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
