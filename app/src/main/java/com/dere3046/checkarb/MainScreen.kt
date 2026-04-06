// MainScreen.kt
package com.dere3046.checkarb

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import com.dere3046.checkarb.ui.components.DataCard
import com.dere3046.checkarb.ui.components.DataRow

private val BUTTON_CORNER_RADIUS = 4.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(LocalContext.current))
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val hasRoot by viewModel.hasRootAccess.collectAsState()
    val availableDevices by viewModel.availableDevices.collectAsState()

    var showTargetDeviceDialogForSlot by remember { mutableStateOf<String?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showArbStatsDialog by remember { mutableStateOf(false) }

    val currentRoute = navController.currentBackStackEntryFlow.collectAsState(initial = navController.currentBackStackEntry).value?.destination?.route

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentRoute != Screen.Main.route) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back)
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(48.dp))
                        }
                        Text(
                            stringResource(R.string.app_name),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (currentRoute?.startsWith(Screen.Detail.route) == true) {
                            IconButton(onClick = { viewModel.scanAllSlots() }) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.refresh),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(48.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            AppNavigation(
                navController = navController,
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onNavigateToSettings = { showSettingsDialog = true },
                onNavigateToArbStats = { showArbStatsDialog = true }
            )

            if (showSettingsDialog) {
                SettingsDialog(
                    onDismiss = { showSettingsDialog = false },
                    context = context,
                    hasRoot = hasRoot
                )
            }

            if (showTargetDeviceDialogForSlot != null) {
                val slot = showTargetDeviceDialogForSlot!!
                TargetDeviceDialog(
                    availableDevices = availableDevices,
                    currentDevice = if (slot == "_a") viewModel.slotADevicePath.value else viewModel.slotBDevicePath.value,
                    onSelect = { path ->
                        viewModel.setSlotDevicePath(slot, path)
                        showTargetDeviceDialogForSlot = null
                    },
                    onReset = {
                        viewModel.resetSlotDevicePath(slot)
                        showTargetDeviceDialogForSlot = null
                    },
                    onDismiss = { showTargetDeviceDialogForSlot = null }
                )
            }

            if (showArbStatsDialog) {
                AlertDialog(
                    onDismissRequest = { showArbStatsDialog = false },
                    title = { Text(stringResource(R.string.oneplus_arb_stats)) },
                    text = { Text(stringResource(R.string.arb_stats_disclaimer)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showArbStatsDialog = false
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                                intent.data = android.net.Uri.parse("https://oparb.pages.dev/")
                                context.startActivity(intent)
                            }
                        ) {
                            Text(stringResource(R.string.navigate))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showArbStatsDialog = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun MainScreenContent(
    viewModel: MainViewModel,
    onNavigateToManual: () -> Unit,
    onNavigateToAuto: () -> Unit,
    onNavigateToDetail: (MainViewModel.SlotInfo) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToArbStats: () -> Unit,
    onNavigateToArbDatabase: () -> Unit,
    onNavigateToXblExtractor: () -> Unit
) {
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val slotA by viewModel.slotA.collectAsState()
    val slotB by viewModel.slotB.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val hasRoot by viewModel.hasRootAccess.collectAsState()

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        DataCard(title = stringResource(R.string.device_info)) {
            val cardWidth = remember { mutableIntStateOf(0) }
            DataRow(stringResource(R.string.model), deviceInfo.model, mutableMaxWidth = cardWidth)
            DataRow(stringResource(R.string.build_number), deviceInfo.buildNumber, mutableMaxWidth = cardWidth)
            DataRow(
                label = stringResource(R.string.kernel_version),
                value = deviceInfo.kernelVersion,
                mutableMaxWidth = cardWidth
            )
            DataRow(
                label = stringResource(R.string.arb_inspector_version),
                value = deviceInfo.arbInspectorVersion,
                mutableMaxWidth = cardWidth
            )
            if (deviceInfo.isDualSlot) {
                DataRow(stringResource(R.string.slot_suffix), deviceInfo.activeSlot, mutableMaxWidth = cardWidth)
            }
        }

        Spacer(Modifier.height(16.dp))

        SlotCard(
            title = stringResource(R.string.slot_a),
            data = slotA,
            isScanning = isScanning,
            isActive = deviceInfo.activeSlot == "_a",
            hasRoot = hasRoot,
            onDetail = { onNavigateToDetail(slotA) }
        )

        if (deviceInfo.isDualSlot) {
            Spacer(Modifier.height(16.dp))
            SlotCard(
                title = stringResource(R.string.slot_b),
                data = slotB,
                isScanning = isScanning,
                isActive = deviceInfo.activeSlot == "_b",
                hasRoot = hasRoot,
                onDetail = { onNavigateToDetail(slotB) }
            )
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(BUTTON_CORNER_RADIUS),
            onClick = onNavigateToManual
        ) {
            Text(stringResource(R.string.manual_scan))
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(BUTTON_CORNER_RADIUS),
            enabled = hasRoot,
            onClick = onNavigateToAuto
        ) {
            Text(stringResource(R.string.auto_scan))
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(BUTTON_CORNER_RADIUS),
            onClick = onNavigateToSettings
        ) {
            Text(stringResource(R.string.settings))
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(BUTTON_CORNER_RADIUS),
            onClick = onNavigateToArbDatabase
        ) {
            Text(stringResource(R.string.check_arb_online))
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(BUTTON_CORNER_RADIUS),
            onClick = onNavigateToXblExtractor
        ) {
            Text(stringResource(R.string.xbl_extractor))
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(BUTTON_CORNER_RADIUS),
            onClick = onNavigateToArbStats
        ) {
            Text(stringResource(R.string.oneplus_arb_stats))
        }
    }
}

class MainViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return MainViewModel(context) as T
    }
}
